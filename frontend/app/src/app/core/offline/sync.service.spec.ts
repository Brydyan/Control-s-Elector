import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi, afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  ACTA_TRANSMITTER,
  ActaTransmitter,
  SyncService,
} from './sync.service';
import { OfflineStorageService } from './offline-storage.service';

/**
 * Tests del SyncService (T-FE-09, C.1, V.2).
 *
 * V.2: si la eliminación post-sync exitoso se comenta, la cola NO se vacía.
 */
describe('SyncService', () => {
  let service: SyncService;
  let storage: OfflineStorageService;
  let transmitter: ActaTransmitter;

  beforeEach(() => {
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

    Object.defineProperty(navigator, 'onLine', {
      value: true,
      configurable: true,
      writable: true,
    });
  });

  afterEach(async () => {
    service.stop();
    await storage.resetForTesting();
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

    expect(transmitter.enviarActaDirecta).toHaveBeenCalledWith({
      totalSufragantes: 100,
    });
    // fake-indexeddb puede deserializar el Blob como objeto plano; validamos
    // el id y la invocación. La fidelidad de Blob nativo se valida en
    // OfflineStorageService.
    expect(transmitter.subirImagenActa).toHaveBeenCalledWith(
      42,
      expect.anything(),
    );
    expect(service.pendientes()).toBe(0);
  });

});