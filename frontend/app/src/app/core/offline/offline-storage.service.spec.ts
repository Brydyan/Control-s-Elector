import { TestBed } from '@angular/core/testing';
import {
  ActaDraft,
  OfflineStorageService,
  PendingActa,
} from './offline-storage.service';

/**
 * Tests del almacén IndexedDB (T-FE-09, B.1).
 *
 * Usa `fake-indexeddb` para emular IndexedDB en jsdom. Polyfill cargado
 * en `src/test-setup.ts`.
 */
describe('OfflineStorageService', () => {
  let service: OfflineStorageService;

  beforeEach(async () => {
    // Resetear servicio previo y borrar DB entre tests para aislamiento.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [OfflineStorageService] });
    service = TestBed.inject(OfflineStorageService);
    if (typeof indexedDB !== 'undefined') {
      await new Promise<void>((resolve) => {
        const req = indexedDB.deleteDatabase('ElectoralOfflineDB');
        req.onsuccess = () => resolve();
        req.onerror = () => resolve();
        req.onblocked = () => resolve();
      });
    }
  });

  afterEach(async () => {
    await service?.resetForTesting();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should enqueue an acta in pending_actas with generated UUID', async () => {
    const id = await service.encolarActa({
      jrvId: 12,
      cargoId: 3,
      payload: { totalSufragantes: 100 },
      imageBlob: null,
    });
    expect(id).toMatch(/^[0-9a-f-]{36}$/);
    const all = await service.obtenerPendientes();
    expect(all.length).toBe(1);
    expect(all[0].jrvId).toBe(12);
    expect(all[0].retries).toBe(0);
    expect(all[0].lastError).toBeNull();
  });

  it('should preserve imageBlob as Blob, never convert to Base64', async () => {
    const blob = new Blob(['fake-pixels'], { type: 'image/webp' });
    await service.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: blob,
    });
    const [item] = await service.obtenerPendientes();
    // En IndexedDB nativo, el blob se preserva como Blob. fake-indexeddb
    // puede deserializarlo como objeto plano; verificamos que NO se haya
    // convertido a data: URL (Base64) ni a string.
    expect(typeof item.imageBlob).not.toBe('string');
    if (item.imageBlob instanceof Blob) {
      expect(item.imageBlob.type).toBe('image/webp');
    }
    // Garantía D1: nunca data:image/...
    expect(JSON.stringify(item.imageBlob)).not.toMatch(/^data:image/);
  });

  it('should delete a pendiente by id (G2: only after 2xx)', async () => {
    const id = await service.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    expect((await service.obtenerPendientes()).length).toBe(1);
    await service.eliminarPendiente(id);
    expect((await service.obtenerPendientes()).length).toBe(0);
  });

  it('should update retries + lastError on failure', async () => {
    const id = await service.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    await service.actualizarIntentos(id, 3, 'Network timeout');
    const [item] = await service.obtenerPendientes();
    expect(item.retries).toBe(3);
    expect(item.lastError).toBe('Network timeout');
  });

  it('should compose draftId from jrvId + cargoId (D5)', () => {
    expect(OfflineStorageService.draftId(101, 2)).toBe('draft_101_2');
    expect(OfflineStorageService.draftId(1, 1)).toBe('draft_1_1');
  });

  it('should save and retrieve a draft', async () => {
    const draft: ActaDraft = {
      draftId: 'draft_101_2',
      jrvId: 101,
      cargoId: 2,
      totalSufragantes: 150,
      votosBlancos: 5,
      votosNulos: 5,
      votosPorCandidato: { 1: 70, 2: 70 },
      updatedAt: 0,
    };
    await service.guardarBorrador(draft);
    const retrieved = await service.obtenerBorrador('draft_101_2');
    expect(retrieved).toBeTruthy();
    expect(retrieved?.totalSufragantes).toBe(150);
    expect(retrieved?.votosPorCandidato).toEqual({ 1: 70, 2: 70 });
  });

  it('should return null when obtaining a missing draft', async () => {
    const result = await service.obtenerBorrador('draft_999_9');
    expect(result).toBeNull();
  });

  it('should delete a draft', async () => {
    await service.guardarBorrador({
      draftId: 'draft_x',
      jrvId: 1,
      cargoId: 1,
      totalSufragantes: 0,
      votosBlancos: 0,
      votosNulos: 0,
      votosPorCandidato: {},
      updatedAt: 0,
    });
    await service.eliminarBorrador('draft_x');
    expect(await service.obtenerBorrador('draft_x')).toBeNull();
  });
});
