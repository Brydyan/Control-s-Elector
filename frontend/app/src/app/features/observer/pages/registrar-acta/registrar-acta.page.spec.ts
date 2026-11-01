import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { vi } from 'vitest';
import { RegistrarActaPage } from './registrar-acta.page';
import { ObserverService } from '../../services/observer.service';
import { OfflineStorageService } from '../../../../core/offline/offline-storage.service';

describe('RegistrarActaPage', () => {
  let component: RegistrarActaPage;
  let fixture: ComponentFixture<RegistrarActaPage>;
  let observerServiceSpy: jasmine.SpyObj<ObserverService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let offlineStorageSpy: jasmine.SpyObj<OfflineStorageService>;

  beforeEach(async () => {
    observerServiceSpy = jasmine.createSpyObj('ObserverService', [
      'getCandidatosPorCargo',
      'guardarBorrador',
      'enviarActaDirecta',
      'subirImagenActa',
    ]);
    // `p-steps` subscribes to the router events on init, so the mock needs
    // the stream even though the page never navigates from the strip.
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    routerSpy.events = of(EMPTY);
    offlineStorageSpy = jasmine.createSpyObj('OfflineStorageService', [
      'guardarBorrador',
      'guardarFotoBorrador',
      'obtenerBorrador',
      'obtenerBorradoresDeJrv',
      'eliminarBorrador',
      'encolarActa',
      'obtenerPendientes',
      'eliminarPendiente',
    ]);
    offlineStorageSpy.obtenerBorrador.and.returnValue(Promise.resolve(null));
    offlineStorageSpy.guardarBorrador.and.returnValue(Promise.resolve());
    offlineStorageSpy.guardarFotoBorrador.and.returnValue(Promise.resolve());
    offlineStorageSpy.eliminarBorrador.and.returnValue(Promise.resolve());
    // Cada test arranca sin borradores: un borrador de un test anterior
    // contaminaría el siguiente al restaurar.
    localStorage.clear();

    observerServiceSpy.getCandidatosPorCargo.and.returnValue(
      of([
        {
          id: 1,
          nombre: 'Candidato A',
          orden: 1,
          cargoId: 2,
          partidoId: 10,
          partidoSiglas: 'PA',
        },
        {
          id: 2,
          nombre: 'Candidato B',
          orden: 2,
          cargoId: 2,
          partidoId: 20,
          partidoSiglas: 'PB',
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [RegistrarActaPage],
      providers: [
        { provide: ObserverService, useValue: observerServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: OfflineStorageService, useValue: offlineStorageSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => {
                  if (key === 'jrvId') return '101';
                  if (key === 'cargoId') return '2';
                  return null;
                },
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegistrarActaPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and initialize candidatos and votos map', () => {
    expect(component).toBeTruthy();
    expect(component.jrvId).toBe(101);
    expect(component.cargoId).toBe(2);
    expect(component.candidatos.length).toBe(2);
    expect(component.getVoto(1)).toBe(0);
    expect(component.getVoto(2)).toBe(0);
  });

  it('should validate checksum correctly', () => {
    component.totalSufragantes = 100;
    component.votosBlancos = 10;
    component.votosNulos = 10;
    expect(component.isChecksumValid).toBe(false);

    component.votosPorCandidato.set(1, 40);
    component.votosPorCandidato.set(2, 40);
    expect(component.isChecksumValid).toBe(true);
  });

  it('should NOT call enviarActaDirecta when checksum is invalid (D.4)', () => {
    component.totalSufragantes = 100;
    component.votosBlancos = 0;
    component.votosNulos = 0;
    component.votosPorCandidato.set(1, 50);
    component.votosPorCandidato.set(2, 40); // 90 != 100 → invalid

    component.enviarActaFinal();

    expect(component.error()).toContain('votos no cuadran');
    expect(observerServiceSpy.enviarActaDirecta).not.toHaveBeenCalled();
  });

  it('should chain enviarActaDirecta + subirImagenActa + navigate on valid send (D.4)', async () => {
    component.totalSufragantes = 100;
    component.votosBlancos = 0;
    component.votosNulos = 0;
    component.votosPorCandidato.set(1, 50);
    component.votosPorCandidato.set(2, 50); // 100 == 100 → valid
    component.selectedImageFile = new File(['x'], 'acta.webp', {
      type: 'image/webp',
    });

    observerServiceSpy.enviarActaDirecta.and.returnValue(
      of({
        id: 99,
        jrvId: 101,
        cargoId: 2,
        estado: 'ENVIADA',
        totalSufragantes: 100,
        votosBlancos: 0,
        votosNulos: 0,
        detalles: [],
      }),
    );
    observerServiceSpy.subirImagenActa.and.returnValue(of(undefined));

    component.enviarActaFinal();
    // finalizarEnvio usa setTimeout(1800ms) para navegación. Usamos timer real.
    await new Promise((resolve) => setTimeout(resolve, 1900));

    expect(observerServiceSpy.enviarActaDirecta).toHaveBeenCalled();
    expect(observerServiceSpy.subirImagenActa).toHaveBeenCalledWith(
      99,
      expect.any(File),
    );
    expect(routerSpy.navigate).toHaveBeenCalledWith([
      '/observer/jrv',
      101,
      'cargos',
    ]);
  });

  it('should chain subirImagenActa after guardarBorrador (D.4)', () => {
    component.totalSufragantes = 100;
    component.votosBlancos = 0;
    component.votosNulos = 0;
    component.votosPorCandidato.set(1, 50);
    component.votosPorCandidato.set(2, 50);
    component.selectedImageFile = new File(['x'], 'acta.webp', {
      type: 'image/webp',
    });

    observerServiceSpy.guardarBorrador.and.returnValue(
      of({
        id: 88,
        jrvId: 101,
        cargoId: 2,
        estado: 'BORRADOR',
        totalSufragantes: 100,
        votosBlancos: 0,
        votosNulos: 0,
        detalles: [],
      }),
    );
    observerServiceSpy.subirImagenActa.and.returnValue(of(undefined));

    component.guardarComoBorrador();

    expect(observerServiceSpy.guardarBorrador).toHaveBeenCalled();
    expect(observerServiceSpy.subirImagenActa).toHaveBeenCalledWith(
      88,
      expect.any(File),
    );
  });

  it('should preview image via URL.createObjectURL, NOT readAsDataURL (T-FE-08 D1, V.4)', () => {
    const revokeSpy = vi
      .spyOn(URL, 'revokeObjectURL')
      .mockReturnValue(undefined);
    const createSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock-uuid-xyz');

    const fakeFile = new File(['pixel-data'], 'acta.webp', {
      type: 'image/webp',
    });
    const event = {
      target: { files: [fakeFile] },
    } as unknown as Event;

    component.onFileSelected(event);

    expect(createSpy).toHaveBeenCalledWith(fakeFile);
    expect(component.imagePreviewUrl).toBe('blob:mock-uuid-xyz');
    // Garantizar que NUNCA produce data: URL (Base64).
    expect(component.imagePreviewUrl?.startsWith('data:')).toBe(false);

    // Cleanup en ngOnDestroy.
    component.ngOnDestroy();
    expect(revokeSpy).toHaveBeenCalledWith('blob:mock-uuid-xyz');
  });

  it('should mark captureCancelled when user cancels (D7 S5, no file selected)', () => {
    // Estado inicial limpio.
    component.captureCancelled = false;
    component.error.set(null);

    const event = {
      target: { files: [] }, // FileList vacío = cancelación
    } as unknown as Event;

    component.onFileSelected(event);

    expect(component.captureCancelled).toBe(true);
    expect(component.error()).toBeNull(); // No es error de sistema.
    expect(component.selectedImageFile).toBeNull();
    expect(component.imagePreviewUrl).toBeNull();
    // D7: NO se resetean datos digitados.
    expect(component.votosPorCandidato.size).toBeGreaterThan(0);
    expect(component.candidatos.length).toBe(2);
  });

  it('should clear captureCancelled when user selects a file after cancelling (D7)', () => {
    component.captureCancelled = true; // estado previo de cancelación

    const fakeFile = new File(['x'], 'acta.webp', { type: 'image/webp' });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:new-url');
    component.onFileSelected({
      target: { files: [fakeFile] },
    } as unknown as Event);

    expect(component.captureCancelled).toBe(false);
    expect(component.selectedImageFile).toBe(fakeFile);
    expect(component.imagePreviewUrl).toBe('blob:new-url');
  });

  it('should have file input with accept="image/*" and capture="environment" (S3)', () => {
    // El flujo arranca en IMAGEN: el input está en pantalla desde el inicio.
    const fileInput = fixture.nativeElement.querySelector('input[type="file"]');
    expect(fileInput).toBeTruthy();
    expect(fileInput.getAttribute('accept')).toBe('image/*');
    expect(fileInput.getAttribute('capture')).toBe('environment');
  });

  it('should restore draft from IndexedDB after LMK reload (T-FE-09 V.5)', async () => {
    offlineStorageSpy.obtenerBorrador.and.returnValue(
      Promise.resolve({
        draftId: 'draft_101_2',
        jrvId: 101,
        cargoId: 2,
        totalSufragantes: 150,
        votosBlancos: 10,
        votosNulos: 5,
        votosPorCandidato: { 1: 70, 2: 65 },
        imageBlob: null,
        imageName: null,
        updatedAt: Date.now(),
      }),
    );

    // Re-fetch candidatos dispara restaurarBorradorOffline().
    component.cargarCandidatos();
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(component.totalSufragantes).toBe(150);
    expect(component.votosBlancos).toBe(10);
    expect(component.votosNulos).toBe(5);
    expect(component.getVoto(1)).toBe(70);
    expect(component.getVoto(2)).toBe(65);
  });

  it('should enqueue acta in IndexedDB on HTTP failure (T-FE-09 S2)', async () => {
    component.totalSufragantes = 100;
    component.votosBlancos = 0;
    component.votosNulos = 0;
    component.votosPorCandidato.set(1, 50);
    component.votosPorCandidato.set(2, 50);

    observerServiceSpy.enviarActaDirecta.and.returnValue({
      subscribe: (handlers: {
        next: (r: unknown) => void;
        error: (e: unknown) => void;
      }) => handlers.error({ error: { message: 'Network timeout' } }),
    } as never);
    offlineStorageSpy.encolarActa.and.returnValue(Promise.resolve('mock-uuid'));
    offlineStorageSpy.eliminarBorrador.and.returnValue(Promise.resolve());

    component.enviarActaFinal();
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(offlineStorageSpy.encolarActa).toHaveBeenCalledWith(
      expect.objectContaining({
        jrvId: 101,
        cargoId: 2,
      }),
    );
  });

  // ─────────────────────────────────────────────────────────────────────
  // T-FE-13 A.1 — el wizard arranca en la foto y no en el conteo
  // ─────────────────────────────────────────────────────────────────────

  it('T-FE-13 A.1: el wizard arranca en el paso IMAGEN (Foto Primero)', () => {
    expect(component.currentStep()).toBe('IMAGEN');
    expect(
      fixture.nativeElement.querySelector('[data-testid="step-imagen"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="step-votos"]'),
    ).toBeNull();
  });

  it('T-FE-13 A.1: tras capturar la foto aparece "Continuar a Digitación de Votos"', () => {
    // Sin foto, la vía principal todavía no está disponible.
    expect(hostButton('btn-continuar-votos')).toBeNull();

    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:foto');
    seleccionarFoto(new File(['x'], 'acta.webp', { type: 'image/webp' }));

    expect(hostButton('btn-continuar-votos')).toBeTruthy();

    innerButton('btn-continuar-votos')?.click();
    fixture.detectChanges();

    expect(component.currentStep()).toBe('VOTOS');
  });

  it('T-FE-13 A.1: "Digitar votos primero" permite saltar la foto', () => {
    expect(hostButton('btn-digitar-votos-primero')).toBeTruthy();

    innerButton('btn-digitar-votos-primero')?.click();
    fixture.detectChanges();

    expect(component.currentStep()).toBe('VOTOS');
    expect(component.selectedImageFile).toBeNull();
  });

  it('T-FE-13 A.1: el paso de votos vuelve a dejar ver la foto', () => {
    component.digitarVotosPrimero();
    fixture.detectChanges();
    component.irAPasoImagen();
    fixture.detectChanges();

    expect(component.currentStep()).toBe('IMAGEN');
  });

  // ─────────────────────────────────────────────────────────────────────
  // T-FE-13 A.2 — la foto y el borrador viven en IndexedDB, no en localStorage
  // ─────────────────────────────────────────────────────────────────────

  it('T-FE-13 A.2: la foto se guarda en IndexedDB (acta_drafts) al capturarla', async () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:foto');
    const archivo = new File(['pixeles'], 'acta.webp', { type: 'image/webp' });

    component.onFileSelected({
      target: { files: [archivo] },
    } as unknown as Event);
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(offlineStorageSpy.guardarFotoBorrador).toHaveBeenCalledWith(
      'draft_101_2',
      101,
      2,
      archivo,
      'acta.webp',
    );
  });

  it('T-FE-13 A.2: la foto del borrador se restaura tras un cierre por LMK', async () => {
    const blob = new Blob(['pixeles'], { type: 'image/webp' });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:restaurada');
    offlineStorageSpy.obtenerBorrador.and.returnValue(
      Promise.resolve({
        draftId: 'draft_101_2',
        jrvId: 101,
        cargoId: 2,
        totalSufragantes: 150,
        votosBlancos: 10,
        votosNulos: 5,
        votosPorCandidato: { 1: 70, 2: 65 },
        imageBlob: blob,
        imageName: 'acta-foto.webp',
        updatedAt: Date.now(),
      }),
    );

    component.cargarCandidatos();
    await new Promise((resolve) => setTimeout(resolve, 100));

    // El acta vuelve con su evidencia: la foto no estaba en la RAM.
    expect(component.selectedImageFile).toBeTruthy();
    expect(component.selectedImageFile?.name).toBe('acta-foto.webp');
    expect(component.imagePreviewUrl).toBe('blob:restaurada');
    expect(component.getVoto(1)).toBe(70);
  });

  it('T-FE-13 A.2: el borrador NO se escribe en localStorage ni en Base64', async () => {
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    localStorage.clear();

    component.totalSufragantes = 100;
    component.votosPorCandidato.set(1, 100);
    component.onFileSelected({
      target: { files: [new File(['x'], 'acta.webp', { type: 'image/webp' })] },
    } as unknown as Event);
    component.continuarAVotos();
    await new Promise((resolve) => setTimeout(resolve, 600));

    // D4: `localStorage` sincrónico está prohibido (bloquea el hilo, no
    // admite Blobs y revienta con QuotaExceededError a los 5 MB).
    expect(setItemSpy).not.toHaveBeenCalled();
    expect(localStorage.length).toBe(0);

    // Y los dos datos sí están en IndexedDB, por separado.
    expect(offlineStorageSpy.guardarBorrador).toHaveBeenCalled();
    expect(offlineStorageSpy.guardarFotoBorrador).toHaveBeenCalled();

    // La foto entra como Blob binario: nunca como cadena ni como data: URL.
    const llamada = vi.mocked(offlineStorageSpy.guardarFotoBorrador).mock
      .calls[0];
    const blob = llamada[3];
    expect(typeof blob).not.toBe('string');
    expect(blob).toBeInstanceOf(Blob);
    expect(JSON.stringify(blob)).not.toMatch(/^data:image/);
  });

  it('T-FE-13 A.2: el autoguardado de los votos NO borra la foto ya guardada', async () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:foto');
    component.onFileSelected({
      target: { files: [new File(['x'], 'acta.webp', { type: 'image/webp' })] },
    } as unknown as Event);
    await new Promise((resolve) => setTimeout(resolve, 20));

    component.totalSufragantes = 100;
    component.votosPorCandidato.set(1, 100);
    component.continuarAVotos();
    await new Promise((resolve) => setTimeout(resolve, 600));

    // Son dos canales distintos: guardarBorrador no lleva foto y
    // guardarFotoBorrador no lleva votos.
    const guardadoVotos = vi.mocked(offlineStorageSpy.guardarBorrador).mock
      .calls[0][0] as Record<string, unknown>;
    expect(guardadoVotos).not.toHaveProperty('imageBlob');
    expect(guardadoVotos['votosPorCandidato']).toEqual({ 1: 100, 2: 0 });
    expect(offlineStorageSpy.guardarFotoBorrador).toHaveBeenCalledTimes(1);
  });

  it('T-FE-13 A.2: la cámara se abre sólo después de que el borrador quedó escrito', async () => {
    // El almacenamiento devuelve una promesa que NO resuelve: el borrador
    // sigue en el aire mientras la cámara se abre.
    let liberar: () => void = () => {};
    const pendiente = new Promise<void>((resolve) => {
      liberar = resolve;
    });
    offlineStorageSpy.guardarBorrador.and.returnValue(pendiente);

    let camaraAbierta = false;
    const input = {
      click: () => {
        camaraAbierta = true;
      },
    } as unknown as HTMLInputElement;

    component.abrirCamara(input);
    await new Promise((resolve) => setTimeout(resolve, 0));

    // Mientras el borrador no está escrito, la cámara NO se abre: es lo que
    // protege al acta de un cierre de proceso por Android LMK (D4).
    expect(camaraAbierta).toBe(false);

    liberar();
    await pendiente;
    await new Promise((resolve) => setTimeout(resolve, 0));

    // Escrito el borrador, la cámara se abre.
    expect(camaraAbierta).toBe(true);
  });

  it('T-FE-13 A.2: el input de archivo se limpia para poder repetir la captura', () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:foto');
    const input = fixture.nativeElement.querySelector(
      '[data-testid="input-foto-acta"]',
    ) as HTMLInputElement;
    const archivo = new File(['x'], 'acta.webp', { type: 'image/webp' });

    // El navegador deja el valor del input apuntando al archivo elegido.
    Object.defineProperty(input, 'value', {
      value: 'C:\\fakepath\\acta.webp',
      writable: true,
      configurable: true,
    });
    Object.defineProperty(input, 'files', {
      value: [archivo],
      configurable: true,
    });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    // Si el input no queda vacío, elegir de nuevo el mismo archivo no
    // dispara `change` y el acta se quedaría con la foto anterior.
    expect(input.value).toBe('');
  });

  // ─────────────────────────────────────────────────────────────────────
  // T-FE-13 B.1 / B.2 / B.3 — cotas de sufragantes y cuadre estricto
  // ─────────────────────────────────────────────────────────────────────

  it('T-FE-13 B.1: con 380 sufragantes hay advertencia, no bloqueo', () => {
    component.totalSufragantes = 380;

    expect(component.alertaSufragantes).toBe(
      'Advertencia: El padrón estándar es de 350 electores. Verifique si es una mesa especial.',
    );
    expect(component.nivelAlertaSufragantes).toBe('warning');
    expect(component.esSufragantesValido).toBe(true);
    expect(component.excedeTopeSufragantes).toBe(false);
  });

  it('T-FE-13 B.1: con 501 sufragantes hay error y el envío queda bloqueado', () => {
    component.totalSufragantes = 501;

    expect(component.alertaSufragantes).toBe(
      'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes.',
    );
    expect(component.nivelAlertaSufragantes).toBe('error');
    expect(component.esSufragantesValido).toBe(false);
    expect(component.excedeTopeSufragantes).toBe(true);

    component.enviarActaFinal();

    expect(observerServiceSpy.enviarActaDirecta).not.toHaveBeenCalled();
    expect(component.error()).toContain('Límite CNE excedido');
  });

  it('T-FE-13 B.1: con 2500 sufragantes (error de tipeo) el envío queda bloqueado', () => {
    component.totalSufragantes = 2500;

    expect(component.alertaSufragantes).toBe(
      'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes.',
    );
    expect(component.nivelAlertaSufragantes).toBe('error');
    expect(component.esSufragantesValido).toBe(false);
    expect(component.excedeTopeSufragantes).toBe(true);

    component.enviarActaFinal();

    expect(observerServiceSpy.enviarActaDirecta).not.toHaveBeenCalled();
    expect(component.error()).toContain('Límite CNE excedido');
  });

  it('T-FE-13 B.1: con 500 sufragantes la cota todavía se cumple', () => {
    component.totalSufragantes = 500;

    expect(component.alertaSufragantes).toBe(
      'Advertencia: El padrón estándar es de 350 electores. Verifique si es una mesa especial.',
    );
    expect(component.esSufragantesValido).toBe(true);
    expect(component.excedeTopeSufragantes).toBe(false);
  });

  it('T-FE-13 S3: con 380 sufragantes y la suma cuadrada, el acta se transmite', async () => {
    component.totalSufragantes = 380;
    component.votosPorCandidato.set(1, 380); // 380 == 380 → cuadra

    observerServiceSpy.enviarActaDirecta.and.returnValue(
      of({
        id: 99,
        jrvId: 101,
        cargoId: 2,
        estado: 'ENVIADA',
        totalSufragantes: 380,
        votosBlancos: 0,
        votosNulos: 0,
        detalles: [],
      }),
    );
    offlineStorageSpy.eliminarBorrador.and.returnValue(Promise.resolve());

    component.enviarActaFinal();

    // S3 no es sólo "muestra un aviso": una mesa especial se puede
    // transmitir si la suma cuadra exactamente.
    expect(observerServiceSpy.enviarActaDirecta).toHaveBeenCalled();
    expect(component.error()).toBeNull();

    // La navegación final se dispara con setTimeout(1800ms).
    await new Promise((resolve) => setTimeout(resolve, 1900));
  });

  it('T-FE-13 B.2: el cuadre es 1:1 estricto, sin margen ni 3x', () => {
    component.totalSufragantes = 100;
    component.votosPorCandidato.set(1, 100);
    expect(component.isChecksumValid).toBe(true);

    // Un voto de más no vale.
    component.votosPorCandidato.set(1, 101);
    expect(component.isChecksumValid).toBe(false);

    // El triple tampoco: la fórmula de 3x no existe (D3).
    component.votosPorCandidato.set(1, 300);
    expect(component.isChecksumValid).toBe(false);

    // Ni la mitad.
    component.votosPorCandidato.set(1, 50);
    expect(component.isChecksumValid).toBe(false);
  });

  it('T-FE-13 B.3: el banner de cota se pinta con el nivel correcto', () => {
    irADigitarVotos();
    escribirPadron(380);

    let banner = fixture.nativeElement.querySelector(
      '[data-testid="cota-banner"]',
    );
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('El padrón estándar es de 350');
    expect(banner.getAttribute('data-nivel')).toBe('warning');
    // Un aviso no interrumpe: se anuncia con `role="status"`.
    expect(banner.getAttribute('role')).toBe('status');

    escribirPadron(501);

    banner = fixture.nativeElement.querySelector('[data-testid="cota-banner"]');
    expect(banner.getAttribute('data-nivel')).toBe('error');
    expect(banner.textContent).toContain('ninguna JRV puede superar 500');
    // Un error sí interrumpe: `role="alert"`.
    expect(banner.getAttribute('role')).toBe('alert');
  });

  it('T-FE-13 B.3: la revisión muestra la cota y bloquea "Transmitir Acta"', () => {
    component.totalSufragantes = 501;
    component.votosPorCandidato.set(1, 501);
    component.continuarARevision();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="cota-banner-revision"]',
      ),
    ).toBeTruthy();
    expect(innerButton('btn-transmitir-acta')?.disabled).toBe(true);
  });

  // ─────────────────────────────────────────────────────────────────────
  // S4 · S5 · S6 — la pantalla de campo del veedor
  // ─────────────────────────────────────────────────────────────────────

  /**
   * Lleva la pantalla al paso de dígitos.
   *
   * El flujo arranca en IMAGEN (T-FE-13 A.1), así que las pruebas de la
   * pantalla de conteo tienen que pasar por la foto primero.
   */
  function irADigitarVotos(): void {
    component.continuarAVotos();
    fixture.detectChanges();
  }

  /** Input de votos del candidato indicado. */
  function voteInput(nombre: string): HTMLInputElement {
    const input = fixture.nativeElement.querySelector(
      `input[aria-label="Votos para ${nombre}"]`,
    ) as HTMLInputElement | null;
    expect(input).toBeTruthy();
    return input as HTMLInputElement;
  }

  /** Input del padrón de la mesa. */
  function padronInput(): HTMLInputElement {
    const input = fixture.nativeElement.querySelector(
      '#totalSufragantes',
    ) as HTMLInputElement | null;
    expect(input).toBeTruthy();
    return input as HTMLInputElement;
  }

  /**
   * Escribe en un input como lo haría el observador (tecla) y deja que la
   * vista se actualice. Los tests de esta pantalla manejan el DOM: lo que
   * importa es lo que el veedor ve.
   */
  function escribir(input: HTMLInputElement, valor: string): void {
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  /** Escribe el total de sufragantes del padrón. */
  function escribirPadron(valor: number): void {
    escribir(padronInput(), String(valor));
  }

  /** Escribe los votos de un candidato. */
  function escribirVotos(nombre: string, valor: number): void {
    escribir(voteInput(nombre), String(valor));
  }

  /** Botón cuyo texto incluye `texto`, o null. */
  function buttonWith(texto: string): HTMLButtonElement | null {
    const botones = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    return botones.find((b) => (b.textContent ?? '').includes(texto)) ?? null;
  }

  /** Host `<app-button>` con el `data-testid` dado, o null. */
  function hostButton(testid: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(
      `[data-testid="${testid}"]`,
    ) as HTMLElement | null;
  }

  /** Botón nativo dentro de un `<app-button>` (p-button lo renderiza). */
  function innerButton(testid: string): HTMLButtonElement | null {
    return (hostButton(testid)?.querySelector('button') ??
      null) as HTMLButtonElement | null;
  }

  /**
   * Elige una foto como lo haría el navegador: un `change` real sobre el
   * input de archivo.
   *
   * No se llama al método del componente directamente: al mutar el estado
   * fuera del ciclo de eventos, Angular en modo desarrollo detecta el
   * cambio entre la pasada de actualización y la de verificación
   * (NG0100) y el test mide un artefacto del framework, no el flujo.
   */
  function seleccionarFoto(archivo: File): void {
    const input = fixture.nativeElement.querySelector(
      '[data-testid="input-foto-acta"]',
    ) as HTMLInputElement;
    expect(input).toBeTruthy();
    Object.defineProperty(input, 'files', {
      value: [archivo],
      configurable: true,
    });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('S4: un acta encolada se anuncia en una región role="status", no en la de error', async () => {
    irADigitarVotos();
    escribirPadron(100);
    escribirVotos('Candidato A', 50);
    escribirVotos('Candidato B', 50);

    observerServiceSpy.enviarActaDirecta.and.returnValue({
      subscribe: (handlers: {
        next: (r: unknown) => void;
        error: (e: unknown) => void;
      }) => handlers.error({ error: { message: 'Network timeout' } }),
    } as never);
    offlineStorageSpy.encolarActa.and.returnValue(Promise.resolve('mock-uuid'));
    offlineStorageSpy.eliminarBorrador.and.returnValue(Promise.resolve());

    component.enviarActaFinal();
    await new Promise((resolve) => setTimeout(resolve, 100));
    fixture.detectChanges();

    const estado = fixture.nativeElement.querySelector('[role="status"]');
    expect(estado).toBeTruthy();
    expect(estado.textContent).toContain('Acta guardada en este dispositivo');

    // El error (role="alert") no debe paintar el mensaje de éxito.
    const alerta = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alerta).toBeNull();
  });

  it('S5: el valor del modelo y el del DOM son siempre el mismo', () => {
    irADigitarVotos();
    const input = voteInput('Candidato A');

    // Caso del bug: desde 0, "-5" se registraba como 0 mientras el campo
    // seguía mostrando "-5" (el binding no escribía porque el valor
    // del modelo no cambiaba).
    escribir(input, '-5');
    expect(component.getVoto(1)).toBe(0);
    expect(input.value).toBe('0');

    escribir(input, '7');
    expect(component.getVoto(1)).toBe(7);
    expect(input.value).toBe('7');
  });

  it('S5: el campo de votos no admite decimales ni negativos y declara su rango', () => {
    irADigitarVotos();
    const input = voteInput('Candidato A');

    escribir(input, '1.5');
    expect(component.getVoto(1)).toBe(1);
    expect(input.value).toBe('1');

    escribir(input, '-5');
    expect(component.getVoto(1)).toBe(0);
    expect(input.value).toBe('0');

    expect(input.getAttribute('min')).toBe('0');
    expect(input.getAttribute('step')).toBe('1');
    expect(input.getAttribute('max')).toBeTruthy();
  });

  it('S6: con cero votos el camino de escape está disponible en el DOM', () => {
    irADigitarVotos();
    escribirPadron(15);

    expect(buttonWith('Continuar con Inconsistencia')).toBeTruthy();
  });

  it('S6: se muestra la diferencia concreta entre la suma y el padrón', () => {
    irADigitarVotos();
    escribirPadron(15);
    escribirVotos('Candidato A', 12);

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('suma 12 ≠ padrón 15');
    expect(texto).toContain('faltan 3');
  });

  it('S6: el botón principal explica por qué no deja continuar', () => {
    irADigitarVotos();
    escribirPadron(15);

    const principal = buttonWith('Continuar a Revisión Final');
    expect(principal).toBeTruthy();
    expect(principal?.disabled).toBe(false);

    principal?.click();
    fixture.detectChanges();

    expect(component.currentStep()).toBe('VOTOS');
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('faltan 15');
  });

  it('S6: salir con votos escritos pide confirmación antes de descartar', () => {
    irADigitarVotos();
    escribirPadron(15);
    escribirVotos('Candidato A', 12);

    buttonWith('Cancelar y volver')?.click();
    fixture.detectChanges();

    expect(routerSpy.navigate).not.toHaveBeenCalled();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('no se ha enviado');
  });

  it('S6: confirmar la salida vuelve a la lista de dignidades', () => {
    irADigitarVotos();
    escribirPadron(15);
    escribirVotos('Candidato A', 12);

    buttonWith('Cancelar y volver')?.click();
    fixture.detectChanges();
    buttonWith('Volver sin enviar')?.click();
    fixture.detectChanges();

    expect(routerSpy.navigate).toHaveBeenCalledWith([
      '/observer/jrv',
      101,
      'cargos',
    ]);
  });

  it('S6: el paso de votos persiste un borrador para no perder lo escrito', async () => {
    irADigitarVotos();
    offlineStorageSpy.guardarBorrador.and.returnValue(Promise.resolve());

    const input = voteInput('Candidato A');
    input.value = '7';
    input.dispatchEvent(new Event('input'));

    await new Promise((resolve) => setTimeout(resolve, 600));

    expect(offlineStorageSpy.guardarBorrador).toHaveBeenCalledWith(
      expect.objectContaining({
        draftId: 'draft_101_2',
        votosPorCandidato: { 1: 7, 2: 0 },
      }),
    );
  });

  it('S6: al cambiar de paso se guarda el borrador antes de navegar', async () => {
    offlineStorageSpy.guardarBorrador.and.returnValue(Promise.resolve());
    component.totalSufragantes = 100;
    component.votosPorCandidato.set(1, 45);

    component.continuarAVotos();
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(component.currentStep()).toBe('VOTOS');
    expect(offlineStorageSpy.guardarBorrador).toHaveBeenCalledWith(
      expect.objectContaining({
        draftId: 'draft_101_2',
        totalSufragantes: 100,
        votosPorCandidato: { 1: 45, 2: 0 },
      }),
    );
  });

  it('S5: un voto por encima del padrón se marca inválido sin recortarlo', () => {
    irADigitarVotos();
    escribirPadron(10);
    const input = voteInput('Candidato A');

    escribir(input, '12');

    // El valor no se recorta: el acta registra lo que se escribió.
    expect(component.getVoto(1)).toBe(12);
    expect(input.value).toBe('12');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    // Y la diferencia concreta queda a la vista.
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('suma 12 ≠ padrón 10 — sobran 2');
  });

  /*
   * S11: the stepper is PrimeNG's `p-steps`. The hand-rolled step, number and
   * label that used to live in the stylesheet were never rendered, and the
   * app's own labels ran together as "VotosEvidenciaRevisión".
   *
   * T-FE-13 A.1: el orden es Foto → Votos → Revisión.
   */
  it('S11: el stepper muestra los tres pasos en el orden Foto Primero', () => {
    const nodos = fixture.nativeElement.querySelectorAll(
      '.p-steps-item-label',
    ) as NodeListOf<Element>;
    const labels = Array.from(nodos).map((element) =>
      (element.textContent ?? '').trim(),
    );

    expect(labels).toEqual(['Foto', 'Votos', 'Revisión']);
  });
});
