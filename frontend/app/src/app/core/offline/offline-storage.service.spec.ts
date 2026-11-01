import { TestBed } from '@angular/core/testing';
import {
  ActaDraftSinFoto,
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

  it('S3: should keep a pending acta in queued state with its blob by default', async () => {
    const blob = new Blob(['foto'], { type: 'image/webp' });
    const id = await service.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: blob,
    });
    const [item] = await service.obtenerPendientes();
    expect(item.estado).toBe('queued');
    expect(item.actaEnviada).toBe(false);
    expect(item.actaIdRemota).toBeNull();
    expect(item.proximoIntentoAt).toBeNull();
    expect(item.imageBlob).not.toBeNull();
    expect(item.id).toBe(id);
  });

  it('S3: should remember that the acta was already posted (anti-duplicate)', async () => {
    const id = await service.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    await service.marcarActaEnviada(id, 77);
    const [item] = await service.obtenerPendientes();
    expect(item.actaEnviada).toBe(true);
    expect(item.actaIdRemota).toBe(77);
  });

  it('S3: should mark an acta as failed without deleting it', async () => {
    const id = await service.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    await service.marcarFallida(id, '422: Acta inconsistente');
    const cola = await service.obtenerPendientes();
    expect(cola.length).toBe(1);
    expect(cola[0].estado).toBe('failed');
    expect(cola[0].lastError).toBe('422: Acta inconsistente');
  });

  it('S3: should put a failed acta back in the queue on retry', async () => {
    const id = await service.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    await service.marcarFallida(id, '422: Acta inconsistente');
    await service.marcarEnCola(id);
    const [item] = await service.obtenerPendientes();
    expect(item.estado).toBe('queued');
  });

  it('S3: should bump the queue version on every mutation', async () => {
    const antes = service.colaVersion();
    const id = await service.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    expect(service.colaVersion()).toBeGreaterThan(antes);
    await service.eliminarPendiente(id);
    expect(service.colaVersion()).toBeGreaterThan(antes + 1);
  });

  it('should compose draftId from jrvId + cargoId (D5)', () => {
    expect(OfflineStorageService.draftId(101, 2)).toBe('draft_101_2');
    expect(OfflineStorageService.draftId(1, 1)).toBe('draft_1_1');
  });

  it('should save and retrieve a draft', async () => {
    const draft: ActaDraftSinFoto = {
      draftId: 'draft_101_2',
      jrvId: 101,
      cargoId: 2,
      totalSufragantes: 150,
      votosBlancos: 5,
      votosNulos: 5,
      votosPorCandidato: { 1: 70, 2: 70 },
    };
    await service.guardarBorrador(draft);
    const retrieved = await service.obtenerBorrador('draft_101_2');
    expect(retrieved).toBeTruthy();
    expect(retrieved?.totalSufragantes).toBe(150);
    expect(retrieved?.votosPorCandidato).toEqual({ 1: 70, 2: 70 });
    // Un borrador escrito sin foto se lee como "sin foto", no como un error.
    expect(retrieved?.imageBlob).toBeNull();
    expect(retrieved?.imageName).toBeNull();
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
    });
    await service.eliminarBorrador('draft_x');
    expect(await service.obtenerBorrador('draft_x')).toBeNull();
  });

  // ─────────────────────────────────────────────────────────────────────
  // T-FE-13 A.2 / D4 — la foto del borrador vive en IndexedDB, en binario
  // ─────────────────────────────────────────────────────────────────────

  it('T-FE-13: should store the evidence photo as a binary Blob, never as Base64', async () => {
    const blob = new Blob(['fake-pixels'], { type: 'image/webp' });
    await service.guardarFotoBorrador('draft_101_2', 101, 2, blob, 'acta.webp');

    const retrieved = await service.obtenerBorrador('draft_101_2');
    expect(retrieved).toBeTruthy();
    expect(retrieved?.imageName).toBe('acta.webp');
    // D4: la foto NUNCA es una cadena ni una URL `data:` (Base64). Con un
    // IndexedDB real el valor vuelve como `Blob`; `fake-indexeddb` puede
    // aplanarlo, así que la garantía que se verifica es "no es texto".
    expect(typeof retrieved?.imageBlob).not.toBe('string');
    expect(JSON.stringify(retrieved?.imageBlob)).not.toMatch(/^data:image/);
    if (retrieved?.imageBlob instanceof Blob) {
      expect(retrieved.imageBlob.type).toBe('image/webp');
    }
  });

  it('T-FE-13: should keep the votes when the photo is written', async () => {
    await service.guardarBorrador({
      draftId: 'draft_101_2',
      jrvId: 101,
      cargoId: 2,
      totalSufragantes: 150,
      votosBlancos: 5,
      votosNulos: 5,
      votosPorCandidato: { 1: 70, 2: 70 },
    });

    await service.guardarFotoBorrador(
      'draft_101_2',
      101,
      2,
      new Blob(['foto'], { type: 'image/webp' }),
      'acta.webp',
    );

    const retrieved = await service.obtenerBorrador('draft_101_2');
    expect(retrieved?.totalSufragantes).toBe(150);
    expect(retrieved?.votosPorCandidato).toEqual({ 1: 70, 2: 70 });
    expect(retrieved?.imageName).toBe('acta.webp');
  });

  it('T-FE-13: should keep the photo when the votes are written', async () => {
    await service.guardarFotoBorrador(
      'draft_101_2',
      101,
      2,
      new Blob(['foto'], { type: 'image/webp' }),
      'acta.webp',
    );

    await service.guardarBorrador({
      draftId: 'draft_101_2',
      jrvId: 101,
      cargoId: 2,
      totalSufragantes: 150,
      votosBlancos: 5,
      votosNulos: 5,
      votosPorCandidato: { 1: 70, 2: 70 },
    });

    const retrieved = await service.obtenerBorrador('draft_101_2');
    // El autoguardado de los votos NO puede borrar la foto: sin ella, el
    // acta que se transmitiría después no tendría evidencia.
    expect(retrieved?.imageName).toBe('acta.webp');
    expect(retrieved?.totalSufragantes).toBe(150);
  });

  it('T-FE-13: should list the drafts of a JRV indexed by cargoId', async () => {
    await service.guardarFotoBorrador(
      'draft_101_2',
      101,
      2,
      new Blob(['foto-2'], { type: 'image/webp' }),
      'alcalde.webp',
    );
    await service.guardarFotoBorrador(
      'draft_101_3',
      101,
      3,
      new Blob(['foto-3'], { type: 'image/webp' }),
      'prefecto.webp',
    );
    // Otra JRV: no debe aparecer en el listado de la mesa 101.
    await service.guardarFotoBorrador(
      'draft_202_2',
      202,
      2,
      new Blob(['foto-otra'], { type: 'image/webp' }),
      'otra.webp',
    );

    const borradores = await service.obtenerBorradoresDeJrv(101);
    expect(Object.keys(borradores)).toEqual(['2', '3']);
    expect(borradores[2].imageName).toBe('alcalde.webp');
    expect(borradores[3].imageName).toBe('prefecto.webp');
  });
});
