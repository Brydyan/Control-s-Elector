import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
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
});
