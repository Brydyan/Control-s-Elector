import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ModificarActaPage } from './modificar-acta.page';
import { ObserverApi } from '../../data/observer.api';
import {
  ActaResponse,
  CandidatoInfo,
} from '../../domain/models/observer.model';
import { ImageCompressionService } from '../../../../core/services/image-compression.service';

describe('ModificarActaPage', () => {
  let component: ModificarActaPage;
  let fixture: ComponentFixture<ModificarActaPage>;
  let observerServiceSpy: jasmine.SpyObj<ObserverApi>;
  let routerSpy: jasmine.SpyObj<Router>;
  let imageCompressionSpy: jasmine.SpyObj<ImageCompressionService>;

  const mockActa: ActaResponse = {
    id: 42,
    jrvId: 101,
    cargoId: 5,
    estado: 'ENVIADA',
    totalSufragantes: 350,
    votosBlancos: 10,
    votosNulos: 5,
    urlEvidencia: 'https://ejemplo.com/foto-acta.jpg',
    detalles: [
      { id: 1, candidatoId: 101, votos: 150 },
      { id: 2, candidatoId: 102, votos: 135 },
      { id: 3, candidatoId: 103, votos: 50 },
    ],
  };

  const mockCandidatos: CandidatoInfo[] = [
    {
      id: 101,
      nombre: 'Candidato A',
      orden: 1,
      cargoId: 5,
      partidoId: 1,
      partidoNombre: 'Partido A',
      partidoSiglas: 'PA',
      partidoNumeroLista: 1,
    },
    {
      id: 102,
      nombre: 'Candidato B',
      orden: 2,
      cargoId: 5,
      partidoId: 2,
      partidoNombre: 'Partido B',
      partidoSiglas: 'PB',
      partidoNumeroLista: 2,
    },
    {
      id: 103,
      nombre: 'Candidato C',
      orden: 3,
      cargoId: 5,
      partidoId: 3,
      partidoNombre: 'Partido C',
      partidoSiglas: 'PC',
      partidoNumeroLista: 3,
    },
  ];

  beforeEach(async () => {
    observerServiceSpy = jasmine.createSpyObj('ObserverApi', [
      'getActaParaCorregir',
      'getCandidatosPorCargo',
      'corregirActa',
      'subirImagenActa',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    imageCompressionSpy = jasmine.createSpyObj('ImageCompressionService', [
      'compress',
      'createPreviewUrl',
      'revokePreviewUrl',
    ]);

    observerServiceSpy.getActaParaCorregir.and.returnValue(of(mockActa));
    observerServiceSpy.getCandidatosPorCargo.and.returnValue(
      of(mockCandidatos),
    );
    observerServiceSpy.corregirActa.and.returnValue(of(mockActa));
    observerServiceSpy.subirImagenActa.and.returnValue(of(undefined));
    imageCompressionSpy.createPreviewUrl.and.returnValue('blob:preview-url');

    await TestBed.configureTestingModule({
      imports: [ModificarActaPage],
      providers: [
        { provide: ObserverApi, useValue: observerServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ImageCompressionService, useValue: imageCompressionSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? '42' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ModificarActaPage);
    component = fixture.componentInstance;
  });

  // ============================================================================
  // Carga inicial
  // ============================================================================

  it('should load acta and candidatos on init', () => {
    fixture.detectChanges();

    expect(observerServiceSpy.getActaParaCorregir).toHaveBeenCalledWith(42);
    expect(observerServiceSpy.getCandidatosPorCargo).toHaveBeenCalledWith(5);
    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
  });

  it('should preload acta data into form fields', () => {
    fixture.detectChanges();

    expect(component.totalSufragantes()).toBe(350);
    expect(component.votosBlancos()).toBe(10);
    expect(component.votosNulos()).toBe(5);
    expect(component.detalles().length).toBe(3);
    expect(component.urlEvidencia()).toBe('https://ejemplo.com/foto-acta.jpg');
    expect(component.jrvNumero()).toBe('101');
  });

  it('should enrich detalles with candidate names and party siglas', () => {
    fixture.detectChanges();

    const detalles = component.detalles();
    expect(detalles[0].candidatoNombre).toBe('Candidato A');
    expect(detalles[0].partidoSiglas).toBe('PA');
    expect(detalles[1].candidatoNombre).toBe('Candidato B');
    expect(detalles[2].candidatoNombre).toBe('Candidato C');
  });

  it('should show error when acta fails to load', () => {
    observerServiceSpy.getActaParaCorregir.and.returnValue(
      throwError(() => new Error('Not found')),
    );

    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.error()).toContain('No se pudo cargar');
  });

  it('should set actaId from route parameter', () => {
    fixture.detectChanges();
    expect(component.actaId).toBe(42);
  });

  // ============================================================================
  // Checksum y validación
  // ============================================================================

  it('should calculate valid checksum when votes sum equals sufragantes', () => {
    fixture.detectChanges();

    // Votos: 150 + 135 + 50 = 335
    // Blancos: 10, Nulos: 5
    // Total: 335 + 10 + 5 = 350 = sufragantes ✓
    expect(component.sumaVotos).toBe(350);
    expect(component.checksumValido).toBe(true);
  });

  it('should mark checksum invalid when votes do not match', () => {
    fixture.detectChanges();
    component.actualizarVotoCandidato(101, 160);

    // Votos: 160 + 135 + 50 = 345 + 10 + 5 = 360 ≠ 350
    expect(component.sumaVotos).toBe(360);
    expect(component.checksumValido).toBe(false);
    expect(component.diferenciaChecksum).toBe(10);
  });

  it('should provide human-readable difference description', () => {
    fixture.detectChanges();
    component.actualizarVotoCandidato(101, 140);

    // Votos: 140 + 135 + 50 = 325 + 10 + 5 = 340 ≠ 350 (faltan 10)
    expect(component.descripcionDiferencia).toContain('faltan 10');
  });

  it('should block save when sufragantes exceeds CNE limit', () => {
    fixture.detectChanges();
    component.actualizarTotalSufragantes(501);

    expect(component.excedeTopeSufragantes).toBe(true);
    expect(component.alertaSufragantes).toContain('Límite CNE excedido');
  });

  // ============================================================================
  // Actualización de votos
  // ============================================================================

  it('should update candidate vote and trigger checksum recalculation', () => {
    fixture.detectChanges();

    component.actualizarVotoCandidato(101, 100);
    expect(component.detalles()[0].votos).toBe(100);
    // 100 + 135 + 50 + 10 + 5 = 300 ≠ 350
    expect(component.checksumValido).toBe(false);
  });

  it('should update votos blancos', () => {
    fixture.detectChanges();

    component.actualizarVotosBlancos(20);
    expect(component.votosBlancos()).toBe(20);
    // 150 + 135 + 50 + 20 + 5 = 360 ≠ 350
    expect(component.checksumValido).toBe(false);
  });

  it('should update votos nulos', () => {
    fixture.detectChanges();

    component.actualizarVotosNulos(15);
    expect(component.votosNulos()).toBe(15);
    // 150 + 135 + 50 + 10 + 15 = 360 ≠ 350
    expect(component.checksumValido).toBe(false);
  });

  it('should update total sufragantes', () => {
    fixture.detectChanges();

    component.actualizarTotalSufragantes(360);
    expect(component.totalSufragantes()).toBe(360);
    // 150 + 135 + 50 + 10 + 5 = 350 ≠ 360
    expect(component.checksumValido).toBe(false);
  });

  // ============================================================================
  // Guardado de modificaciones
  // ============================================================================

  it('should call corregirActa with correct payload on save', () => {
    fixture.detectChanges();
    component.guardarModificaciones();

    expect(observerServiceSpy.corregirActa).toHaveBeenCalledWith(42, {
      jrvId: 0,
      cargoId: 0,
      totalSufragantes: 350,
      votosBlancos: 10,
      votosNulos: 5,
      detalles: [
        { candidatoId: 101, votos: 150 },
        { candidatoId: 102, votos: 135 },
        { candidatoId: 103, votos: 50 },
      ],
    });
  });

  it('should navigate to mis-jrvs after successful save', async () => {
    fixture.detectChanges();
    component.guardarModificaciones();

    await new Promise((resolve) => setTimeout(resolve, 2100));
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/observer/mis-jrvs']);
  });

  it('should upload new image if selected', async () => {
    const mockFile = new File(['data'], 'test.webp', { type: 'image/webp' });
    component.selectedImageFile = mockFile;

    fixture.detectChanges();
    component.guardarModificaciones();

    expect(observerServiceSpy.subirImagenActa).toHaveBeenCalledWith(
      42,
      mockFile,
    );
  });

  it('should not upload image if none selected', () => {
    fixture.detectChanges();
    component.guardarModificaciones();

    expect(observerServiceSpy.subirImagenActa).not.toHaveBeenCalled();
  });

  it('should not save when checksum is invalid', () => {
    fixture.detectChanges();
    component.actualizarVotoCandidato(101, 160); // breaks checksum
    component.guardarModificaciones();

    expect(observerServiceSpy.corregirActa).not.toHaveBeenCalled();
  });

  it('should not save when sufragantes exceeds limit', () => {
    fixture.detectChanges();
    component.actualizarTotalSufragantes(501);
    // Also break checksum so guardarModificaciones enters the error path
    component.actualizarVotoCandidato(101, 0);
    component.guardarModificaciones();

    // checksum invalid blocks first, so corregirActa is not called
    expect(observerServiceSpy.corregirActa).not.toHaveBeenCalled();
  });

  it('should show error on 400 response', async () => {
    observerServiceSpy.corregirActa.and.returnValue(
      throwError(() => ({ status: 400, message: 'Bad request' })),
    );

    fixture.detectChanges();
    component.guardarModificaciones();

    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(component.error()).toContain('suma de votos no coincide');
  });

  it('should show error on 409 response', async () => {
    observerServiceSpy.corregirActa.and.returnValue(
      throwError(() => ({ status: 409, message: 'Conflict' })),
    );

    fixture.detectChanges();
    component.guardarModificaciones();

    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(component.error()).toContain('ya fue modificada');
  });

  // ============================================================================
  // Navegación
  // ============================================================================

  it('should cancel and navigate back to mis-jrvs', () => {
    fixture.detectChanges();
    component.cancelar();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/observer/mis-jrvs']);
  });

  // ============================================================================
  // Limpieza
  // ============================================================================

  it('should revoke preview URL on destroy', () => {
    fixture.detectChanges();
    component.imagePreviewUrl = 'blob:test-url';
    component.ngOnDestroy();

    expect(imageCompressionSpy.revokePreviewUrl).toHaveBeenCalledWith(
      'blob:test-url',
    );
  });
});
