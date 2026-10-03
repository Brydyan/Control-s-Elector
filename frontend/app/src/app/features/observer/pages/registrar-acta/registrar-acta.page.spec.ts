import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { RegistrarActaPage } from './registrar-acta.page';
import { ObserverService } from '../../services/observer.service';

describe('RegistrarActaPage', () => {
  let component: RegistrarActaPage;
  let fixture: ComponentFixture<RegistrarActaPage>;
  let observerServiceSpy: jasmine.SpyObj<ObserverService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    observerServiceSpy = jasmine.createSpyObj('ObserverService', [
      'getCandidatosPorCargo',
      'guardarBorrador',
      'enviarActaDirecta',
      'subirImagenActa',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

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

  it('should proceed to image step when called', () => {
    component.totalSufragantes = 100;
    component.votosBlancos = 0;
    component.votosNulos = 0;
    component.irAPasoImagen();
    expect(component.currentStep).toBe('IMAGEN');
    expect(component.error).toBeNull();
  });

  it('should NOT call enviarActaDirecta when checksum is invalid (D.4)', () => {
    component.totalSufragantes = 100;
    component.votosBlancos = 0;
    component.votosNulos = 0;
    component.votosPorCandidato.set(1, 50);
    component.votosPorCandidato.set(2, 40); // 90 != 100 → invalid

    component.enviarActaFinal();

    expect(component.error).toContain('votos no cuadran');
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
    component.error = null;

    const event = {
      target: { files: [] }, // FileList vacío = cancelación
    } as unknown as Event;

    component.onFileSelected(event);

    expect(component.captureCancelled).toBe(true);
    expect(component.error).toBeNull(); // No es error de sistema.
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
    // Navegar a IMAGEN step para renderizar el input
    component.currentStep = 'IMAGEN';
    fixture.detectChanges();

    const fileInput = fixture.nativeElement.querySelector(
      'input[type="file"]',
    );
    expect(fileInput).toBeTruthy();
    expect(fileInput.getAttribute('accept')).toBe('image/*');
    expect(fileInput.getAttribute('capture')).toBe('environment');
  });
});
