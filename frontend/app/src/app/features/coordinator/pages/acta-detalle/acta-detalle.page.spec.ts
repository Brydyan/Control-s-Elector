import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ActaDetalleCompleto } from '../../models/coordinator.model';
import { CoordinatorService } from '../../services/coordinator.service';
import { ActaDetallePage } from './acta-detalle.page';

describe('ActaDetallePage', () => {
  let component: ActaDetallePage;
  let fixture: ComponentFixture<ActaDetallePage>;
  let coordinatorServiceMock: Partial<CoordinatorService>;
  let routerMock: Partial<Router>;

  const mockDetalle: ActaDetalleCompleto = {
    acta: {
      id: 42,
      jrvId: 201,
      cargoId: 1,
      estado: 'ENVIADA',
      storageUrl: 'https://example.com/acta42.webp',
      totalSufragantes: 100,
      votosBlancos: 2,
      votosNulos: 3,
      fechaRegistro: '2026-09-12T10:00:00Z',
    },
    jrv: {
      id: 201,
      numero: '201',
      parroquia: 'Manglaralto',
    },
    cargo: {
      id: 1,
      nombre: 'Prefecto/a',
      nivel: 'PROVINCIA',
    },
    candidatos: [
      {
        id: 10,
        nombre: 'Candidato 1',
        orden: 1,
        cargoId: 1,
        partidoId: 1,
        partidoNombre: 'Partido Azul',
        partidoSiglas: 'PA',
        votos: 60,
      },
      {
        id: 11,
        nombre: 'Candidato 2',
        orden: 2,
        cargoId: 1,
        partidoId: 2,
        partidoNombre: 'Partido Rojo',
        partidoSiglas: 'PR',
        votos: 35,
      },
    ],
    totalVotosCalculado: 100,
    diferenciaChecksum: 0,
    checksumValido: true,
  };

  beforeEach(async () => {
    coordinatorServiceMock = {
      getActaDetalleCompleto: vi.fn().mockReturnValue(of(mockDetalle)),
      aprobarActa: vi
        .fn()
        .mockReturnValue(of({ ...mockDetalle.acta, estado: 'APROBADA' })),
      disputarActa: vi
        .fn()
        .mockReturnValue(of({ ...mockDetalle.acta, estado: 'EN_DISPUTA' })),
      corregirActa: vi
        .fn()
        .mockReturnValue(of({ ...mockDetalle.acta, estado: 'CORREGIDA' })),
    };

    routerMock = {
      navigate: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ActaDetallePage],
      providers: [
        { provide: CoordinatorService, useValue: coordinatorServiceMock },
        { provide: Router, useValue: routerMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: '42' }),
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActaDetallePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load acta detail', () => {
    expect(component).toBeTruthy();
    expect(coordinatorServiceMock.getActaDetalleCompleto).toHaveBeenCalledWith(
      42,
    );
    expect(component.data?.acta.id).toBe(42);
    expect(component.data?.acta.estado).toBe('ENVIADA');
  });

  it('should navigate back to list on volver', () => {
    component.volver();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/coordinator/actas']);
  });

  it('should open and confirm dispute modal', () => {
    component.abrirModalDisputar();
    expect(component.modalDisputarAbierto).toBe(true);

    component.motivoDisputa = 'Votos ilegibles';
    component.confirmarDisputa();

    expect(coordinatorServiceMock.disputarActa).toHaveBeenCalledWith(42, {
      motivo: 'Votos ilegibles',
    });
    expect(component.modalDisputarAbierto).toBe(false);
    expect(component.data?.acta.estado).toBe('EN_DISPUTA');
  });

  it('should open and handle correction modal with checksum balance', () => {
    component.abrirModalCorregir();
    expect(component.modalCorregirAbierto).toBe(true);

    component.correcionSufragantes = 100;
    component.correcionBlancos = 5;
    component.correcionNulos = 5;
    component.votosCorregidosMap.set(10, 50);
    component.votosCorregidosMap.set(11, 40);

    expect(component.isChecksumCorregidoValido).toBe(true);

    component.confirmarCorrecion();
    expect(coordinatorServiceMock.corregirActa).toHaveBeenCalledWith(42, {
      jrvId: 201,
      cargoId: 1,
      totalSufragantes: 100,
      votosBlancos: 5,
      votosNulos: 5,
      detalles: [
        { candidatoId: 10, votos: 50 },
        { candidatoId: 11, votos: 40 },
      ],
    });
  });

  it('should approve acta when confirmed', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    component.abrirAprobar();

    expect(coordinatorServiceMock.aprobarActa).toHaveBeenCalledWith(42);
    expect(component.data?.acta.estado).toBe('APROBADA');
  });
});
