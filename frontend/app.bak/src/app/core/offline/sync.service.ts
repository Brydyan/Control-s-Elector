import { HttpClient } from '@angular/common/http';
import {
  Injectable,
  InjectionToken,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { OfflineStorageService, PendingActa } from './offline-storage.service';
import { HttpActaTransmitter } from './http-acta-transmitter';
import { calcularBackoffMs, clasificarFallo, mensajeFallo } from './fallos';

/**
 * Opciones de una transmisión.
 *
 * `headers` viaja hasta el `HttpClient` de la implementación: el POST del
 * acta lleva `Idempotency-Key` con el `PendingActa.id` para que el servidor
 * pueda deduplicar un reintento.
 */
export interface OpcionesTransmision {
  headers?: Record<string, string>;
}

/**
 * Interfaz de transmisión inyectable. Permite desacoplar SyncService de
 * ObserverService para evitar imports circulares (core ↔ features).
 *
 * La implementación DEBE reenviar `opciones.headers` a la petición HTTP.
 * El backend, a su vez, tiene que honrar la cabecera `Idempotency-Key` para
 * que la garantía de deduplicación sea completa en el servidor: hoy
 * `electoral-api` no la implementa. La parte que funciona por sí sola es el
 * tracking local (`actaEnviada` / `actaIdRemota`): un reintento nunca vuelve
 * a crear el acta.
 */
export interface ActaTransmitter {
  enviarActaDirecta(
    payload: unknown,
    opciones?: OpcionesTransmision,
  ): Observable<{ id: number }>;
  subirImagenActa(actaId: number, blob: Blob): Observable<void>;
}

export const ACTA_TRANSMITTER = new InjectionToken<ActaTransmitter>(
  'ACTA_TRANSMITTER',
);

/** Cabecera de idempotencia del POST del acta. */
export const IDEMPOTENCY_HEADER = 'Idempotency-Key';

/**
 * Sincronizador bidireccional de cola offline (T-FE-09, C.1, design D3).
 *
 * Dispara la transmisión cuando:
 * 1. El navegador emite el evento `online` (recuperación de señal).
 * 2. Un timer periódico de 30s si quedan actas en cola.
 *
 * Cada acta se transmite en dos pasos:
 *   - Paso 1: `POST /api/v1/observer/actas` con payload y cabecera
 *     `Idempotency-Key: <PendingActa.id>`.
 *   - Paso 2: si 2xx, `POST /api/v1/observer/actas/{id}/imagen` con blob.
 *   - Paso 3: borrar el registro de IndexedDB solo si ambos pasos 2xx.
 *
 * Un fallo se clasifica antes de hacer nada con el registro (S3):
 *   - Transitorio (red, timeout, 5xx): el item sigue en la cola con su blob
 *     y se reintenta con backoff exponencial. NUNCA se borra un acta por un
 *     fallo transitorio, por muchos reintentos que se acumulen.
 *   - Permanente (4xx): el item pasa a estado `failed`, visible para el
 *     operador, con su blob intacto. La eliminación del registro exige una
 *     confirmación explícita (`descartarFallido` / `descartarFallidos`).
 *
 * Además, el POST del acta se marca como hecho en cuanto el servidor lo
 * acepta: si falla la imagen, el reintento solo vuelve a subir la imagen y
 * no puede crear un segundo acta en el servidor.
 */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly storage = inject(OfflineStorageService);

  /**
   * Transmisor enlazado por la aplicación (token `ACTA_TRANSMITTER`). Si
   * no hay ninguno, se usa el transmisor HTTP propio: sin él la cola se
   * llenaba pero nunca se vaciaba.
   */
  private readonly transmitterEnyectado = inject(ACTA_TRANSMITTER, {
    optional: true,
  });
  private readonly http = inject(HttpClient, { optional: true });
  private readonly transmitter: ActaTransmitter | null =
    this.transmitterEnyectado ??
    (this.http ? new HttpActaTransmitter(this.http) : null);

  private timerHandle: ReturnType<typeof setInterval> | null = null;
  private onlineListenerBound = false;
  private syncing = false;

  /**
   * Número de actas en la cola. Incluye las fallidas: ningún registro sale
   * del almacén sin que el operador lo decida, y el total tiene que seguir
   * siendo visible.
   */
  readonly pendientes = signal<number>(0);

  /**
   * Actas de la cola que el servidor rechazó de forma permanente. Están
   * esperando una decisión del operador: reintentar o descartar.
   */
  readonly fallidos = signal<number>(0);

  /** Estado de sincronización activa. */
  readonly sincronizando = signal<boolean>(false);

  /**
   * Handler del evento `online`. Tiene que ser una referencia estable: si
   * `addEventListener` y `removeEventListener` reciben funciones distintas,
   * el listener nunca se quita y `stop()` es un no-op.
   */
  private readonly onOnline = (): void => {
    void this.syncNow();
  };

  constructor() {
    // La cola cambia cuando alguien encola un acta (p.ej. el veedor sin
    // señal): el contador tiene que reflejarlo sin esperar al timer de 30s.
    effect(() => {
      this.storage.colaVersion();
      void this.refreshCount();
    });
  }

  /** Inicializa el servicio: registra listener `online` y arranca timer. */
  start(): void {
    if (typeof window === 'undefined') return;

    if (!this.onlineListenerBound) {
      window.addEventListener('online', this.onOnline);
      this.onlineListenerBound = true;
    }

    if (this.timerHandle === null) {
      this.timerHandle = setInterval(() => {
        if (this.pendientes() > 0) void this.syncNow();
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
      window.removeEventListener('online', this.onOnline);
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
    try {
      const cola = await this.storage.obtenerPendientes();
      this.pendientes.set(cola.length);
      // Los items fallidos esperan una acción del operador: no cuentan como
      // trabajo en curso (si no, la UI anunciaría "enviando…" cada 30 s).
      const transmitibles = cola.filter((item) => item.estado !== 'failed');
      if (transmitibles.length === 0) return;

      this.sincronizando.set(true);
      for (const item of transmitibles) {
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
    const version = this.storage.colaVersion();
    const cola = await this.storage.obtenerPendientes();
    if (this.storage.colaVersion() !== version) {
      // La cola cambió mientras la leíamos: el siguiente refresh la refleja.
      return;
    }
    this.pendientes.set(cola.length);
    this.fallidos.set(cola.filter((item) => item.estado === 'failed').length);
  }

  /**
   * Devuelve a la cola las actas que el servidor rechazó de forma
   * permanente. Es una decisión del operador, no un efecto del contador de
   * reintentos.
   */
  async reintentarFallidos(): Promise<void> {
    const cola = await this.storage.obtenerPendientes();
    for (const item of cola) {
      if (item.estado === 'failed') {
        await this.storage.marcarEnCola(item.id);
      }
    }
    await this.refreshCount();
    await this.syncNow();
  }

  /**
   * Descarta un acta fallida. Esta es la ÚNICA vía por la que se borra un
   * registro de la cola sin que el servidor lo haya aceptado: la llamada
   * explícita del operador es la confirmación.
   */
  async descartarFallido(id: string): Promise<void> {
    await this.storage.eliminarPendiente(id);
    console.warn({
      event: 'offline_sync_item_discarded',
      pendingActaId: id,
    });
    await this.refreshCount();
  }

  /** Descarta todas las actas fallidas (confirmación explícita del operador). */
  async descartarFallidos(): Promise<void> {
    const cola = await this.storage.obtenerPendientes();
    for (const item of cola) {
      if (item.estado === 'failed') {
        await this.storage.eliminarPendiente(item.id);
        console.warn({
          event: 'offline_sync_item_discarded',
          pendingActaId: item.id,
        });
      }
    }
    await this.refreshCount();
  }

  private async transmitir(item: PendingActa): Promise<void> {
    const transmitter = this.transmitter;
    if (!transmitter) return;

    // Un item fallido espera una acción del operador: no se reintenta solo.
    if (item.estado === 'failed') return;

    // Backoff: si todavía no toca reintentar, el item sigue en la cola.
    if (
      item.proximoIntentoAt !== null &&
      item.proximoIntentoAt !== undefined &&
      item.proximoIntentoAt > Date.now()
    ) {
      return;
    }

    try {
      let actaIdRemota = item.actaIdRemota ?? null;

      if (!item.actaEnviada) {
        // Paso 1: payload + clave de idempotencia. El header lo reenvía la
        // implementación del transmitter; el backend tiene que honrarla para
        // deduplicar, el tracking local es la garantía que no depende de eso.
        const acta = await firstValueFrom(
          transmitter.enviarActaDirecta(item.payload, {
            headers: { [IDEMPOTENCY_HEADER]: item.id },
          }),
        );
        if (!acta || !acta.id) {
          await this.registrarFalloTransitorio(item, 'Backend no devolvió id');
          return;
        }
        actaIdRemota = acta.id;
        await this.storage.marcarActaEnviada(item.id, acta.id);
        console.info({
          event: 'offline_sync_acta_posted',
          pendingActaId: item.id,
          jrvId: item.jrvId,
          cargoId: item.cargoId,
          actaId: acta.id,
        });
      }

      // Paso 2: imagen si hay blob. Si el acta ya se había posteado, se
      // reintenta SOLO la imagen: nunca se crea un acta duplicada.
      if (item.imageBlob) {
        if (actaIdRemota === null) {
          await this.registrarFalloTransitorio(
            item,
            'El acta no tiene id remoto para la imagen',
          );
          return;
        }
        await firstValueFrom(
          transmitter.subirImagenActa(actaIdRemota, item.imageBlob),
        );
      }

      // Paso 3: borrar de cola solo tras ambos 2xx (G2).
      await this.storage.eliminarPendiente(item.id);
      console.info({
        event: 'offline_sync_completed',
        pendingActaId: item.id,
        jrvId: item.jrvId,
        cargoId: item.cargoId,
        actaId: actaIdRemota,
      });
    } catch (err: unknown) {
      if (clasificarFallo(err) === 'permanente') {
        await this.registrarFalloPermanente(item, err);
      } else {
        await this.registrarFalloTransitorio(item, err);
      }
    }
  }

  /**
   * Fallo transitorio (red, timeout, 5xx): el item sigue en la cola con su
   * blob y se reintenta con backoff. Jamás borra el registro.
   */
  private async registrarFalloTransitorio(
    item: PendingActa,
    causa: unknown,
  ): Promise<void> {
    const retries = item.retries + 1;
    const mensaje = mensajeFallo(causa);
    const proximoIntentoAt = Date.now() + calcularBackoffMs(retries);
    await this.storage.actualizarPendiente(item.id, {
      retries,
      lastError: mensaje,
      estado: 'queued',
      proximoIntentoAt,
    });
    console.warn({
      event: 'offline_sync_transient_failure',
      pendingActaId: item.id,
      jrvId: item.jrvId,
      cargoId: item.cargoId,
      attempt: retries,
      nextAttemptAt: proximoIntentoAt,
      reason: mensaje,
    });
  }

  /**
   * Fallo permanente (4xx): el item queda en estado `failed`, visible para el
   * operador, con su blob intacto. No se borra nada.
   */
  private async registrarFalloPermanente(
    item: PendingActa,
    causa: unknown,
  ): Promise<void> {
    const retries = item.retries + 1;
    const mensaje = mensajeFallo(causa);
    await this.storage.actualizarPendiente(item.id, {
      retries,
      lastError: mensaje,
      estado: 'failed',
      proximoIntentoAt: null,
    });
    console.error({
      event: 'offline_sync_permanent_failure',
      pendingActaId: item.id,
      jrvId: item.jrvId,
      cargoId: item.cargoId,
      attempt: retries,
      reason: mensaje,
    });
  }
}
