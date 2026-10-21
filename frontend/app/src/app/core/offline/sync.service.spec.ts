import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { vi, afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { ACTA_TRANSMITTER, ActaTransmitter, SyncService } from './sync.service';
import { OfflineStorageService } from './offline-storage.service';

/**
 * Tests del SyncService (T-FE-09, C.1, V.2 + S3).
 *
 * V.2: si la eliminación post-sync exitoso se comenta, la cola NO se vacía.
 * S3: la cola no puede perder un acta (un fallo transitorio jamás borra el
 * registro ni su blob) ni duplicarla (un acta ya posteada no se vuelve a
 * postear) y su estado es visible para el operador.
 */

/** Error con status HTTP: el clasificador separa 4xx de 5xx/red. */
function httpError(
  status: number,
  message = 'Error HTTP',
): Error & { status: number } {
  return Object.assign(new Error(message), { status });
}

/** Reloj congelado para que el backoff sea determinista. */
function congelarReloj(): { avanzar: (ms: number) => void } {
  let actual = 1_700_000_000_000;
  vi.spyOn(Date, 'now').mockImplementation(() => actual);
  return {
    avanzar: (ms: number) => {
      actual += ms;
    },
  };
}

describe('SyncService', () => {
  let service: SyncService;
  let storage: OfflineStorageService;
  let transmitter: ActaTransmitter;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    transmitter = {
      enviarActaDirecta: vi.fn(),
      subirImagenActa: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        SyncService,
        OfflineStorageService,
        { provide: ACTA_TRANSMITTER, useValue: transmitter },
      ],
    });
    service = TestBed.inject(SyncService);
    storage = TestBed.inject(OfflineStorageService);

    // IndexedDB es compartido por todos los tests del archivo: se vacía la
    // cola para que cada test parta de cero.
    for (const item of await storage.obtenerPendientes()) {
      await storage.eliminarPendiente(item.id);
    }

    Object.defineProperty(navigator, 'onLine', {
      value: true,
      configurable: true,
      writable: true,
    });
  });

  afterEach(async () => {
    service.stop();
    await storage.resetForTesting();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should empty the queue after successful sync (V.2 baseline)', async () => {
    transmitter.enviarActaDirecta = vi.fn().mockReturnValue(of({ id: 100 }));
    transmitter.subirImagenActa = vi.fn().mockReturnValue(of(undefined));

    await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: { foo: 'bar' },
      imageBlob: null,
    });
    await service.refreshCount();
    expect(service.pendientes()).toBe(1);

    await service.syncNow();

    expect(service.pendientes()).toBe(0);
    expect((await storage.obtenerPendientes()).length).toBe(0);
    expect(transmitter.enviarActaDirecta).toHaveBeenCalled();
  });

  it('should keep item in queue on HTTP failure (no eliminar en fallo)', async () => {
    transmitter.enviarActaDirecta = vi
      .fn()
      .mockReturnValue(throwError(() => new Error('Network timeout')));

    await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    await service.syncNow();

    expect(service.pendientes()).toBe(1);
    const [item] = await storage.obtenerPendientes();
    expect(item.retries).toBe(1);
    expect(item.lastError).toBe('Network timeout');
  });

  it('should skip sync when navigator.onLine is false', async () => {
    Object.defineProperty(navigator, 'onLine', {
      value: false,
      configurable: true,
      writable: true,
    });
    transmitter.enviarActaDirecta = vi.fn().mockReturnValue(of({ id: 1 }));
    await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    await service.syncNow();
    expect(transmitter.enviarActaDirecta).not.toHaveBeenCalled();
  });

  it('should register 30s timer on start (S4)', async () => {
    const setIntervalSpy = vi.spyOn(global, 'setInterval');
    service.start();
    expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 30_000);
    setIntervalSpy.mockRestore();
  });

  it('should sync two-step: payload then image', async () => {
    transmitter.enviarActaDirecta = vi.fn().mockReturnValue(of({ id: 42 }));
    transmitter.subirImagenActa = vi.fn().mockReturnValue(of(undefined));

    const blob = new Blob(['x'], { type: 'image/webp' });
    await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: { totalSufragantes: 100 },
      imageBlob: blob,
    });
    await service.syncNow();

    // El acta viaja con su clave de idempotencia (S3 regla 3).
    expect(transmitter.enviarActaDirecta).toHaveBeenCalledWith(
      { totalSufragantes: 100 },
      expect.objectContaining({
        headers: expect.objectContaining({
          'Idempotency-Key': expect.any(String),
        }),
      }),
    );
    // fake-indexeddb puede deserializar el Blob como objeto plano; validamos
    // el id y la invocación. La fidelidad de Blob nativo se valida en
    // OfflineStorageService.
    expect(transmitter.subirImagenActa).toHaveBeenCalledWith(
      42,
      expect.anything(),
    );
    expect(service.pendientes()).toBe(0);
  });

  // -------------------------------------------------------------------
  // S3 — la cola no puede perder un acta
  // -------------------------------------------------------------------

  it('S3: a transient failure after the retry budget keeps the acta and its blob (no destructive delete)', async () => {
    transmitter.enviarActaDirecta = vi
      .fn()
      .mockReturnValue(throwError(() => httpError(503, 'Service Unavailable')));

    const blob = new Blob(['foto-evidencia'], { type: 'image/webp' });
    const id = await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: blob,
    });
    // 4 fallos previos: el siguiente es el quinto, el que antes borraba todo.
    await storage.actualizarIntentos(id, 4, 'fallo previo');

    await service.syncNow();

    const cola = await storage.obtenerPendientes();
    expect(cola.length).toBe(1);
    expect(cola[0].id).toBe(id);
    expect(cola[0].retries).toBe(5);
    // El blob de la foto sobrevive al fallo transitorio.
    expect(cola[0].imageBlob).not.toBeNull();
    expect(typeof cola[0].imageBlob).not.toBe('string');
    expect(cola[0].estado).not.toBe('failed');
  });

  it('S3: a permanent rejection (4xx) moves the acta to a visible failed state instead of deleting it', async () => {
    transmitter.enviarActaDirecta = vi
      .fn()
      .mockReturnValue(throwError(() => httpError(422, 'Acta inconsistente')));

    await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: new Blob(['foto-evidencia'], { type: 'image/webp' }),
    });

    await service.syncNow();

    const cola = await storage.obtenerPendientes();
    // El registro NO se borra: queda en un estado que el operador puede ver.
    expect(cola.length).toBe(1);
    expect(cola[0].estado).toBe('failed');
    expect(cola[0].lastError).toContain('422');
    expect(service.fallidos()).toBe(1);
  });

  it('S3: a transient failure waits for the backoff before the next attempt', async () => {
    const reloj = congelarReloj();
    transmitter.enviarActaDirecta = vi
      .fn()
      .mockReturnValue(throwError(() => httpError(503, 'Service Unavailable')));

    await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });

    await service.syncNow();
    expect(transmitter.enviarActaDirecta).toHaveBeenCalledTimes(1);

    // Todavía dentro del backoff: no se reintenta.
    await service.syncNow();
    expect(transmitter.enviarActaDirecta).toHaveBeenCalledTimes(1);

    // Backoff vencido: vuelve a intentarlo.
    reloj.avanzar(31_000);
    await service.syncNow();
    expect(transmitter.enviarActaDirecta).toHaveBeenCalledTimes(2);
  });

  // -------------------------------------------------------------------
  // S3 — la cola no puede duplicar un acta
  // -------------------------------------------------------------------

  it('S3: when only the image upload failed, the next attempt does not POST the acta again', async () => {
    const reloj = congelarReloj();
    transmitter.enviarActaDirecta = vi.fn().mockReturnValue(of({ id: 77 }));
    transmitter.subirImagenActa = vi
      .fn()
      .mockReturnValueOnce(
        throwError(() => httpError(503, 'Fallo al subir la imagen')),
      )
      .mockReturnValue(of(undefined));

    await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: { totalSufragantes: 100 },
      imageBlob: new Blob(['foto-evidencia'], { type: 'image/webp' }),
    });

    await service.syncNow();
    expect(transmitter.enviarActaDirecta).toHaveBeenCalledTimes(1);
    expect(transmitter.subirImagenActa).toHaveBeenCalledTimes(1);

    // El backoff vence: se reintenta la cola.
    reloj.avanzar(60_000);

    await service.syncNow();

    // El acta YA existe en el servidor: solo se reintenta la imagen.
    expect(transmitter.enviarActaDirecta).toHaveBeenCalledTimes(1);
    expect(transmitter.subirImagenActa).toHaveBeenCalledTimes(2);
    expect(transmitter.subirImagenActa).toHaveBeenLastCalledWith(
      77,
      expect.anything(),
    );
    expect((await storage.obtenerPendientes()).length).toBe(0);
  });

  it('S3: the idempotency key travels with the acta request', async () => {
    transmitter.enviarActaDirecta = vi.fn().mockReturnValue(of({ id: 9 }));
    transmitter.subirImagenActa = vi.fn().mockReturnValue(of(undefined));

    const id = await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: { totalSufragantes: 100 },
      imageBlob: null,
    });

    await service.syncNow();

    expect(transmitter.enviarActaDirecta).toHaveBeenCalledWith(
      { totalSufragantes: 100 },
      expect.objectContaining({
        headers: expect.objectContaining({ 'Idempotency-Key': id }),
      }),
    );
  });

  // -------------------------------------------------------------------
  // S3 — el listener `online` se puede quitar
  // -------------------------------------------------------------------

  it('S3: stop() removes the online listener with the same function identity', () => {
    const addSpy = vi.spyOn(window, 'addEventListener');
    const removeSpy = vi.spyOn(window, 'removeEventListener');

    service.start();
    const added = addSpy.mock.calls.find((call) => call[0] === 'online');
    expect(added).toBeTruthy();

    service.stop();
    const removed = removeSpy.mock.calls.find((call) => call[0] === 'online');
    expect(removed).toBeTruthy();
    // Misma referencia: sin esto `stop()` es un no-op.
    expect(removed![1]).toBe(added![1]);
  });

  it('S3: after stop() the service no longer reacts to online events', () => {
    transmitter.enviarActaDirecta = vi.fn().mockReturnValue(of({ id: 1 }));
    const syncSpy = vi.spyOn(service, 'syncNow');

    service.start();
    window.dispatchEvent(new Event('online'));
    expect(syncSpy).toHaveBeenCalledTimes(1);

    service.stop();
    window.dispatchEvent(new Event('online'));
    expect(syncSpy).toHaveBeenCalledTimes(1);
  });
});

/**
 * Sin `ACTA_TRANSMITTER` enlazado (que es el caso de la app), la cola tiene
 * que seguir transmitiendo: si no, el acta encolada se queda para siempre.
 */
describe('SyncService (transmisor por defecto)', () => {
  let service: SyncService;
  let storage: OfflineStorageService;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SyncService);
    storage = TestBed.inject(OfflineStorageService);
    httpMock = TestBed.inject(HttpTestingController);

    for (const item of await storage.obtenerPendientes()) {
      await storage.eliminarPendiente(item.id);
    }
  });

  afterEach(async () => {
    service.stop();
    await storage.resetForTesting();
    httpMock.verify();
  });

  it('S3: transmits the queued acta over HTTP carrying the idempotency key', async () => {
    const id = await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: { totalSufragantes: 10 },
      imageBlob: null,
    });

    const sincronizado = service.syncNow();
    await vi.waitFor(() => {
      const reqs = httpMock.match(`${environment.apiUrl}/observer/actas`);
      expect(reqs.length).toBe(1);
      reqs[0].flush({ id: 5 });
    });
    await sincronizado;

    expect((await storage.obtenerPendientes()).length).toBe(0);
    expect(service.pendientes()).toBe(0);
    expect(id).toBeTruthy();
  });

  it('S3: sends the pending id as the Idempotency-Key header', async () => {
    const id = await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: { totalSufragantes: 10 },
      imageBlob: null,
    });

    const sincronizado = service.syncNow();
    await vi.waitFor(() => {
      const reqs = httpMock.match(`${environment.apiUrl}/observer/actas`);
      expect(reqs.length).toBe(1);
      expect(reqs[0].request.headers.get('Idempotency-Key')).toBe(id);
      reqs[0].flush({ id: 5 });
    });
    await sincronizado;
  });
});
