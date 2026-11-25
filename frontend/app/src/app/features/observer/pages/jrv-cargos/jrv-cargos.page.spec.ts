import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { JrvCargosPage } from './jrv-cargos.page';
import { ObserverApi } from '../../data/observer.api';
import { OfflineStorageService } from '../../../../core/offline/offline-storage.service';
import { ImageCompressionService } from '../../../../core/services/image-compression.service';
import { AuthStore } from '../../../auth/services/auth.store';

describe('JrvCargosPage', () => {
  let component: JrvCargosPage;
  let fixture: ComponentFixture<JrvCargosPage>;
  let observerServiceSpy: jasmine.SpyObj<ObserverApi>;
  let routerSpy: jasmine.SpyObj<Router>;
  let offlineStorageSpy: jasmine.SpyObj<OfflineStorageService>;
  let imageCompressionSpy: jasmine.SpyObj<ImageCompressionService>;

  beforeEach(async () => {
    observerServiceSpy = jasmine.createSpyObj('ObserverService', [
      'getCargosDeJrv',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    offlineStorageSpy = jasmine.createSpyObj('OfflineStorageService', [
      'guardarFotoBorrador',
      'obtenerBorradoresDeJrv',
    ]);
    offlineStorageSpy.obtenerBorradoresDeJrv.and.returnValue(
      Promise.resolve({}),
    );
    offlineStorageSpy.guardarFotoBorrador.and.returnValue(Promise.resolve());
    imageCompressionSpy = jasmine.createSpyObj('ImageCompressionService', [
      'compress',
      'createPreviewUrl',
      'revokePreviewUrl',
    ]);

    observerServiceSpy.getCargosDeJrv.and.returnValue(
      of([
        { cargoId: 1, nombre: 'Alcalde', nivel: 'CANTONAL', registrado: false },
        {
          cargoId: 2,
          nombre: 'Prefecto',
          nivel: 'PROVINCIAL',
          registrado: true,
          estadoActa: 'ENVIADA',
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [JrvCargosPage],
      providers: [
        { provide: ObserverApi, useValue: observerServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: OfflineStorageService, useValue: offlineStorageSpy },
        { provide: ImageCompressionService, useValue: imageCompressionSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'jrvId' ? '42' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JrvCargosPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load cargos for JRV', () => {
    expect(component).toBeTruthy();
    expect(component.jrvId).toBe(42);
    expect(component.cargos().length).toBe(2);
  });

  it('should navigate to registrar acta', () => {
    component.registrarActa(component.cargos()[0]);
    expect(routerSpy.navigate).toHaveBeenCalledWith([
      '/observer/jrv',
      42,
      'cargo',
      1,
      'registrar',
    ]);
  });

  it('debe navegar al detalle de coordinador/master si el usuario es MASTER', () => {
    // Simular que el usuario es MASTER
    const authStore = TestBed.inject(AuthStore);
    vi.spyOn(authStore, 'isMaster').mockReturnValue(true);

    const cargoRegistrado = {
      cargoId: 2,
      nombre: 'Prefecto',
      nivel: 'PROVINCIAL',
      registrado: true,
      actaId: 105,
      estadoActa: 'ENVIADA' as const,
    };

    component.registrarActa(cargoRegistrado);
    expect(routerSpy.navigate).toHaveBeenCalledWith([
      '/coordinator/actas',
      105,
    ]);
  });

  // ─────────────────────────────────────────────────────────────────────
  // T-FE-13 C.1 — Captura Rápida de Actas (Foto Primero)
  // ─────────────────────────────────────────────────────────────────────

  it('T-FE-13 C.1: la acción de captura rápida está disponible en la cabecera', () => {
    const boton = fixture.nativeElement.querySelector(
      '[data-testid="btn-captura-rapida"]',
    );
    expect(boton).toBeTruthy();
    expect(boton.textContent).toContain('Captura Rápida de Actas');
  });

  it('T-FE-13 C.1: la captura rápida guarda la foto de la dignidad en IndexedDB (T-FE-20 comprimida)', async () => {
    await component.abrirCapturaRapida();
    fixture.detectChanges();

    const archivo = new File(['pixeles'], 'alcalde.jpg', {
      type: 'image/jpeg',
    });
    const comprimido = new File(['comprimido'], 'alcalde.webp', {
      type: 'image/webp',
    });
    imageCompressionSpy.compress.and.returnValue(Promise.resolve(comprimido));
    imageCompressionSpy.createPreviewUrl.and.returnValue('blob:mock-123');

    component.prepararCaptura(
      component.cargos()[0],
      document.createElement('input'),
    );
    await component.onFotoCapturada({
      target: { files: [archivo] },
    } as unknown as Event);

    // T-FE-20: La foto se guarda COMPRIMIDA (WebP) en `acta_drafts`, con la
    // misma clave que usa el formulario de registro de esa dignidad.
    expect(offlineStorageSpy.guardarFotoBorrador).toHaveBeenCalledWith(
      'draft_42_1',
      42,
      1,
      comprimido,
      'alcalde.webp',
    );
    expect(component.tieneFoto(1)).toBe(true);
    expect(component.tieneFoto(2)).toBe(false);
  });

  it('T-FE-13 C.1: las actas ya fotografiadas se recuperan del dispositivo', async () => {
    offlineStorageSpy.obtenerBorradoresDeJrv.and.returnValue(
      Promise.resolve({
        2: {
          draftId: 'draft_42_2',
          jrvId: 42,
          cargoId: 2,
          totalSufragantes: 0,
          votosBlancos: 0,
          votosNulos: 0,
          votosPorCandidato: {},
          imageBlob: new Blob(['foto'], { type: 'image/webp' }),
          imageName: 'prefecto.webp',
          updatedAt: Date.now(),
        },
      }),
    );

    await component.abrirCapturaRapida();

    expect(component.tieneFoto(2)).toBe(true);
    expect(component.tieneFoto(1)).toBe(false);
  });

  it('T-FE-13 C.1: desde la captura rápida se pasa a digitar los votos', async () => {
    await component.abrirCapturaRapida();
    component.registrarActa(component.cargos()[1]);

    expect(routerSpy.navigate).toHaveBeenCalledWith([
      '/observer/jrv',
      42,
      'cargo',
      2,
      'registrar',
    ]);
  });

  // ─────────────────────────────────────────────────────────────────────
  // T-FE-20 C.3 — Compresión de Imágenes en Captura Rápida
  // ─────────────────────────────────────────────────────────────────────

  it('C.3: llama a imageCompression.compress() al capturar foto rápida', async () => {
    const archivo = new File(['pixeles'], 'alcalde.jpg', {
      type: 'image/jpeg',
    });
    const comprimido = new File(['comprimido'], 'alcalde.webp', {
      type: 'image/webp',
    });
    imageCompressionSpy.compress.and.returnValue(Promise.resolve(comprimido));
    imageCompressionSpy.createPreviewUrl.and.returnValue('blob:mock-123');

    component.prepararCaptura(
      component.cargos()[0],
      document.createElement('input'),
    );
    await component.onFotoCapturada({
      target: { files: [archivo] },
    } as unknown as Event);

    expect(imageCompressionSpy.compress).toHaveBeenCalledWith(archivo);
  });

  it('C.3: guarda la foto WebP comprimida en IndexedDB', async () => {
    const archivo = new File(['pixeles'], 'alcalde.jpg', {
      type: 'image/jpeg',
    });
    const comprimido = new File(['comprimido'], 'alcalde.webp', {
      type: 'image/webp',
    });
    imageCompressionSpy.compress.and.returnValue(Promise.resolve(comprimido));
    imageCompressionSpy.createPreviewUrl.and.returnValue('blob:mock-123');

    component.prepararCaptura(
      component.cargos()[0],
      document.createElement('input'),
    );
    await component.onFotoCapturada({
      target: { files: [archivo] },
    } as unknown as Event);

    // Debe guardarse el archivo comprimido, no el original.
    expect(offlineStorageSpy.guardarFotoBorrador).toHaveBeenCalledWith(
      'draft_42_1',
      42,
      1,
      comprimido,
      'alcalde.webp',
    );
  });

  it('C.3: maneja error de compresión sin romper la UX', async () => {
    const archivo = new File(['pixeles'], 'alcalde.jpg', {
      type: 'image/jpeg',
    });
    imageCompressionSpy.compress.and.returnValue(
      Promise.reject(new Error('Compression failed')),
    );
    imageCompressionSpy.createPreviewUrl.and.returnValue('blob:mock-123');

    component.prepararCaptura(
      component.cargos()[0],
      document.createElement('input'),
    );
    await component.onFotoCapturada({
      target: { files: [archivo] },
    } as unknown as Event);

    // El error se muestra pero no se lanzan excepciones.
    expect(component.error()).toContain('optimizar');
    // No se guarda en IndexedDB.
    expect(offlineStorageSpy.guardarFotoBorrador).not.toHaveBeenCalled();
  });
});
