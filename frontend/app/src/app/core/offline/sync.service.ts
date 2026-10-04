import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { OfflineStorageService, PendingActa } from './offline-storage.service';

/**
 * Interfaz de transmisión inyectable. Permite desacoplar SyncService de
 * ObserverService para evitar imports circulares (core ↔ features).
 */
export interface ActaTransmitter {
  enviarActaDirecta(payload: unknown): Observable<{ id: number }>;
  subirImagenActa(actaId: number, blob: Blob): Observable<void>;
}

export const ACTA_TRANSMITTER = new InjectionToken<ActaTransmitter>(
  'ACTA_TRANSMITTER',
);

/**
 * Sincronizador bidireccional de cola offline (T-FE-09, C.1, design D3).
 *
 * Dispara la transmisión cuando:
 * 1. El navegador emite el evento `online` (recuperación de señal).
 * 2. Un timer periódico de 30s si quedan actas en cola.
 *
 * Cada acta se transmite en dos pasos:
 *   - Paso 1: `POST /api/v1/observer/actas` con payload.
 *   - Paso 2: si 2xx, `POST /api/v1/observer/actas/{id}/imagen` con blob.
 *   - Paso 3: borrar el registro de IndexedDB solo si ambos pasos 2xx.
 *
 * Si el backend rechaza con 4xx (e.g. mesa cerrada, inconsistencia), se
 * incrementa `retries` y tras 5 fallos consecutivos se abandona el intento.
 */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly storage = inject(OfflineStorageService);
  private readonly transmitter = inject(ACTA_TRANSMITTER, {
    optional: true,
  });

  private timerHandle: ReturnType<typeof setInterval> | null = null;
  private onlineListenerBound = false;
  private syncing = false;

  /** Número de actas pendientes; UI puede suscribirse. */
  readonly pendientes = signal<number>(0);

  /** Estado de sincronización activa. */
  readonly sincronizando = signal<boolean>(false);

  /** Inicializa el servicio: registra listener `online` y arranca timer. */
  start(): void {
    if (typeof window === 'undefined') return;

    if (!this.onlineListenerBound) {
      window.addEventListener('online', () => this.syncNow());
      this.onlineListenerBound = true;
    }

    if (this.timerHandle === null) {
      this.timerHandle = setInterval(() => {
        if (this.pendientes() > 0) this.syncNow();
      }, 30_000);
    }

    // Snapshot inicial.
    void this.refreshCount();
  }

  /** Detiene el timer y listeners (testing). */
  stop(): void {
    if (this.timerHandle !== null) {
      clearInterval(this.timerHandle);
      this.timerHandle = null;
    }
    if (this.onlineListenerBound && typeof window !== 'undefined') {
      window.removeEventListener('online', () => this.syncNow());
      this.onlineListenerBound = false;
    }
  }

  /** Sincronización manual (para testing o UI button). */
  async syncNow(): Promise<void> {
    if (this.syncing) return;
    if (typeof navigator !== 'undefined' && navigator.onLine === false) return;
    if (!this.transmitter) {
      // Sin transmitter (test sin DI), no se puede transmitir.
      return;
    }
    this.syncing = true;
    this.sincronizando.set(true);
    try {
      const cola = await this.storage.obtenerPendientes();
      this.pendientes.set(cola.length);
      for (const item of cola) {
        await this.transmitir(item);
      }
    } finally {
      this.syncing = false;
      this.sincronizando.set(false);
      await this.refreshCount();
    }
  }

  /** Refresca contador de pendientes. Llamar tras encolar o eliminar. */
  async refreshCount(): Promise<void> {
    const cola = await this.storage.obtenerPendientes();
    this.pendientes.set(cola.length);
  }

  private async transmitir(item: PendingActa): Promise<void> {
    if (!this.transmitter) return;
    try {
      // Paso 1: payload.
      const acta = await new Promise<{ id: number }>((resolve, reject) => {
        this.transmitter!.enviarActaDirecta(item.payload).subscribe({
          next: (res) => resolve(res),
          error: (e) => reject(e),
        });
      });
      if (!acta || !acta.id) {
        await this.storage.actualizarIntentos(
          item.id,
          item.retries + 1,
          'Backend no devolvió id',
        );
        return;
      }
      // Paso 2: imagen si hay blob.
      if (item.imageBlob) {
        await new Promise<void>((resolve, reject) => {
          this.transmitter!.subirImagenActa(acta.id, item.imageBlob!).subscribe(
            {
              next: () => resolve(),
              error: (e) => reject(e),
            },
          );
        });
      }
      // Paso 3: borrar de cola solo tras ambos 2xx (G2).
      await this.storage.eliminarPendiente(item.id);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      await this.storage.actualizarIntentos(item.id, item.retries + 1, msg);
      // Si supera 5 reintentos, abandonar (D3: no saturar red).
      if (item.retries + 1 >= 5) {
        await this.storage.eliminarPendiente(item.id);
      }
    }
  }
}
