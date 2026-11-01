import { Injectable } from '@angular/core';
import { DBSchema, IDBPDatabase, openDB } from 'idb';

/**
 * Almacén IndexedDB offline (T-FE-09, B.1, design D2 + D5).
 *
 * - DB: `ElectoralOfflineDB` v1
 * - Store `pending_actas`: actas que no pudieron transmitirse por falta de red.
 *   Se eliminan tras confirmación 2xx de `POST /actas` y `POST /actas/:id/imagen`.
 * - Store `acta_drafts`: borradores preventivos por (jrvId, cargoId).
 *   Persiste el estado del formulario antes de invocar la cámara nativa
 *   (Android LMK mata el proceso Chrome al abrir cámara).
 *   Desde T-FE-13 guarda también la foto de la evidencia como `Blob` binario.
 *
 * Decisión: las imágenes se almacenan en IndexedDB como Blob nativo (no
 * Base64) para preservar D1 de T-FE-08.
 */

export interface PendingActa {
  id: string; // UUID generado en cliente
  jrvId: number;
  cargoId: number;
  payload: unknown; // ActaRequest serializado
  imageBlob: Blob | null; // puede ser null si el usuario no adjuntó foto
  createdAt: number;
  retries: number;
  lastError: string | null;
}

export interface ActaDraft {
  draftId: string; // `draft_${jrvId}_${cargoId}`
  jrvId: number;
  cargoId: number;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  votosPorCandidato: Record<number, number>;
  /**
   * Evidencia fotográfica del acta, como `Blob` binario puro (T-FE-13 D4).
   *
   * Nunca Base64 ni `data:` URL: el Base64 infla la memoria del teléfono y
   * precipita el cierre del proceso (Android LMK) justo cuando el veedor
   * acaba de asegurar su acta.
   */
  imageBlob: Blob | null;
  /** Nombre original del archivo de la foto, para reconstruir el `File`. */
  imageName: string | null;
  updatedAt: number;
}

/**
 * Borrador electoral sin la parte de la foto.
 *
 * Es lo que escribe el formulario de votos ({@link OfflineStorageService.guardarBorrador}).
 * La foto se gobierna por su propio método
 * ({@link OfflineStorageService.guardarFotoBorrador}): el estado electoral nunca la pisa
 * y la foto nunca pisa los votos.
 */
export type ActaDraftSinFoto = Omit<
  ActaDraft,
  'imageBlob' | 'imageName' | 'updatedAt'
>;

interface ElectoralOfflineDB extends DBSchema {
  pending_actas: {
    key: string;
    value: PendingActa;
  };
  acta_drafts: {
    key: string;
    value: ActaDraft;
  };
}

const DB_NAME = 'ElectoralOfflineDB';
const DB_VERSION = 1;

@Injectable({ providedIn: 'root' })
export class OfflineStorageService {
  private dbPromise: Promise<IDBPDatabase<ElectoralOfflineDB>> | null = null;

  private getDB(): Promise<IDBPDatabase<ElectoralOfflineDB>> {
    if (!this.dbPromise) {
      this.dbPromise = openDB<ElectoralOfflineDB>(DB_NAME, DB_VERSION, {
        upgrade(db) {
          if (!db.objectStoreNames.contains('pending_actas')) {
            db.createObjectStore('pending_actas', { keyPath: 'id' });
          }
          if (!db.objectStoreNames.contains('acta_drafts')) {
            db.createObjectStore('acta_drafts', { keyPath: 'draftId' });
          }
        },
      });
    }
    return this.dbPromise;
  }

  /**
   * Test helper: cierra la conexión IDB y limpia el cache de promesas.
   * Llamar en afterEach para evitar bloqueos de `deleteDatabase` en fake-indexeddb.
   */
  async resetForTesting(): Promise<void> {
    if (this.dbPromise) {
      const db = await this.dbPromise;
      db.close();
      this.dbPromise = null;
    }
  }

  // -----------------------------------------------------------------
  // Cola de actas pendientes
  // -----------------------------------------------------------------

  async encolarActa(
    acta: Omit<PendingActa, 'id' | 'createdAt' | 'retries' | 'lastError'>,
  ): Promise<string> {
    const full: PendingActa = {
      id: this.uuid(),
      createdAt: Date.now(),
      retries: 0,
      lastError: null,
      ...acta,
    };
    const db = await this.getDB();
    await db.put('pending_actas', full);
    return full.id;
  }

  async obtenerPendientes(): Promise<PendingActa[]> {
    const db = await this.getDB();
    return db.getAll('pending_actas');
  }

  async eliminarPendiente(id: string): Promise<void> {
    const db = await this.getDB();
    await db.delete('pending_actas', id);
  }

  async actualizarIntentos(
    id: string,
    retries: number,
    lastError: string | null,
  ): Promise<void> {
    const db = await this.getDB();
    const current = await db.get('pending_actas', id);
    if (!current) return;
    await db.put('pending_actas', { ...current, retries, lastError });
  }

  // -----------------------------------------------------------------
  // Borradores preventivos (D5)
  // -----------------------------------------------------------------

  static draftId(jrvId: number, cargoId: number): string {
    return `draft_${jrvId}_${cargoId}`;
  }

  /**
   * Guarda el estado electoral del borrador, sin tocar la foto.
   *
   * Separar las dos escrituras es lo que permite el patrón "Guardar antes de
   * capturar" (T-FE-13 A.2): el veedor digita los votos, el formulario los
   * guarda, y después toma la foto — que se guarda en su propio canal. Si las
   * dos cosas vivieran en un único `put`, la segunda escritura borraría la
   * primera y la foto del acta se perdería en el dispositivo.
   */
  async guardarBorrador(draft: ActaDraftSinFoto): Promise<void> {
    const db = await this.getDB();
    const previo = await db.get('acta_drafts', draft.draftId);
    await db.put('acta_drafts', {
      ...previo,
      ...draft,
      // La foto que ya estaba guardada sobrevive a la escritura de votos.
      imageBlob: previo?.imageBlob ?? null,
      imageName: previo?.imageName ?? null,
      updatedAt: Date.now(),
    });
  }

  /**
   * Guarda sólo la foto de la evidencia, sin tocar los votos.
   *
   * La foto se guarda como `Blob` binario puro: nunca Base64 ni `data:` URL
   * (T-FE-13 D4). Un Base64 de una foto de 4 MB son 5,4 MB de heap en un
   * teléfono de gama baja, y eso es exactamente lo que dispara el Low Memory
   * Killer de Android en medio de una captura.
   */
  async guardarFotoBorrador(
    draftId: string,
    jrvId: number,
    cargoId: number,
    imageBlob: Blob,
    imageName: string,
  ): Promise<void> {
    const db = await this.getDB();
    const previo = await db.get('acta_drafts', draftId);
    await db.put('acta_drafts', {
      ...previo,
      draftId,
      jrvId,
      cargoId,
      totalSufragantes: previo?.totalSufragantes ?? 0,
      votosBlancos: previo?.votosBlancos ?? 0,
      votosNulos: previo?.votosNulos ?? 0,
      votosPorCandidato: previo?.votosPorCandidato ?? {},
      imageBlob,
      imageName,
      updatedAt: Date.now(),
    });
  }

  async obtenerBorrador(draftId: string): Promise<ActaDraft | null> {
    const db = await this.getDB();
    const result = await db.get('acta_drafts', draftId);
    if (!result) return null;
    // Un borrador anterior a T-FE-13 no tiene foto: se lee como "sin foto",
    // no como un error.
    return {
      ...result,
      imageBlob: result.imageBlob ?? null,
      imageName: result.imageName ?? null,
    };
  }

  /**
   * Los borradores de una JRV, indexados por `cargoId`.
   *
   * La captura rápida (T-FE-13 C.1) necesita saber qué actas de la mesa ya
   * tienen foto antes de volver a pedirlas: sin esto, el veedor fotografía dos
   * veces la misma acta.
   */
  async obtenerBorradoresDeJrv(
    jrvId: number,
  ): Promise<Record<number, ActaDraft>> {
    const db = await this.getDB();
    const todos = await db.getAll('acta_drafts');
    const porCargo: Record<number, ActaDraft> = {};
    for (const draft of todos) {
      if (draft.jrvId !== jrvId) continue;
      porCargo[draft.cargoId] = {
        ...draft,
        imageBlob: draft.imageBlob ?? null,
        imageName: draft.imageName ?? null,
      };
    }
    return porCargo;
  }

  async eliminarBorrador(draftId: string): Promise<void> {
    const db = await this.getDB();
    await db.delete('acta_drafts', draftId);
  }

  // -----------------------------------------------------------------
  // Utilidades
  // -----------------------------------------------------------------

  private uuid(): string {
    if (
      typeof crypto !== 'undefined' &&
      typeof crypto.randomUUID === 'function'
    ) {
      return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }
}
