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

  it('should load image and set preview dataURL on file selection (S4)', () => {
    const mockFile = new File(['fake-image-data'], 'test.webp', {
      type: 'image/webp',
    });
    const dataUrl = 'data:image/webp;base64,Zmfha2UtaW1hZ2UtZGF0YQ==';

    // Mock FileReader constructor
    const mockOnLoad = vi.fn();
    const mockReadAsDataURL = vi.fn(function (this: any) {
      this.result = dataUrl;
      this.onload();
    });

    const FileReaderMock = vi.fn(function (this: any) {
      this.readAsDataURL = mockReadAsDataURL;
    }) as any;
    FileReaderMock.prototype.readAsDataURL = mockReadAsDataURL;

    vi.stubGlobal('FileReader', FileReaderMock);

    component.onFileSelected({ target: { files: [mockFile] } });

    expect(component.selectedImageFile).toBe(mockFile);
    expect(component.imagePreviewUrl).toBe(dataUrl);

    vi.unstubAllGlobals();
  });
});
