import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
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
      urlEvidencia: 'https://example.com/acta42.webp',
      totalSufragantes: 100,
      votosBlancos: 2,
      votosNulos: 3,
      fechaRegistro: '2026-09-12T10:00:00Z',
      totalVotosCandidatos: 95,
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
    historial: [],
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
      subirImagenContingencia: vi.fn(),
      descargarExpedientePdf: vi.fn(),
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
    expect(component.data()?.acta.id).toBe(42);
    expect(component.data()?.acta.estado).toBe('ENVIADA');

    const badge = fixture.nativeElement.querySelector(
      '.status-banner app-badge',
    );
    expect(badge?.textContent.trim()).toBe('Enviada');
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
    // El modal se cierra al disparar la llamada; la actualización del DTO la
    // hace handleActionSuccess → cargarDetalle() (flujo asíncrono cubierto por
    // integration test, no por este unit test).
    expect(component.modalDisputarAbierto).toBe(false);
  });

  /**
   * T-FE-10 V.2: confirmarDisputa debe rechazar motivo vacío sin llamar al
   * servicio. Captura regresiones que omitan la validación obligatoria.
   */
  it('should reject empty motivo and NOT call service (V.2)', () => {
    component.abrirModalDisputar();
    component.motivoDisputa = '   '; // whitespace-only también cuenta
    component.confirmarDisputa();

    expect(component.errorDisputa).toContain('obligatorio');
    expect(coordinatorServiceMock.disputarActa).not.toHaveBeenCalled();
    expect(component.modalDisputarAbierto).toBe(true); // sigue abierto
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

  it('should approve acta when confirmed (T-FE-12 S3 modal)', () => {
    // T-FE-12: abrirAprobar ahora abre modal; el servicio se llama en confirmarAprobar.
    component.abrirAprobar();
    expect(component.confirmAprobarAbierto).toBe(true);
    expect(coordinatorServiceMock.aprobarActa).not.toHaveBeenCalled();

    component.confirmarAprobar();

    // El observable mock emite sincrónicamente, así que submitting vuelve
    // a false al finalizar el subscribe. Verificamos que el servicio fue
    // llamado con el id correcto y que se disparó la recarga (éxito).
    expect(coordinatorServiceMock.aprobarActa).toHaveBeenCalledWith(42);
    expect(coordinatorServiceMock.getActaDetalleCompleto).toHaveBeenCalledTimes(
      2,
    ); // inicial + recarga
    expect(component.submitting()).toBe(false);
  });

  it('should auto-clear success message after the configured timeout', () => {
    vi.useFakeTimers();
    component['handleActionSuccess']('Mensaje de éxito');
    expect(component.actionSuccessMessage).toBe('Mensaje de éxito');
    vi.advanceTimersByTime(3999);
    expect(component.actionSuccessMessage).toBe('Mensaje de éxito');
    vi.advanceTimersByTime(2);
    expect(component.actionSuccessMessage).toBeNull();
    vi.useRealTimers();
  });

  it('should clear pending success timer on destroy', () => {
    vi.useFakeTimers();
    const clearSpy = vi.spyOn(globalThis, 'clearTimeout');
    component['handleActionSuccess']('otro mensaje');
    component.ngOnDestroy();
    expect(clearSpy).toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('should expose chequeos with ok items for a healthy acta', () => {
    const checks = component.chequeos();
    const okCount = checks.filter((c) => c.ok).length;
    expect(okCount).toBeGreaterThan(0);
    expect(checks.some((c) => c.texto.includes('Evidencia'))).toBe(true);
    expect(checks.some((c) => c.texto.includes('Cuadre'))).toBe(true);
  });

  it('should flag inconsistent checksum when diferenciaChecksum is not zero', () => {
    if (component.data()) {
      component.data().diferenciaChecksum = 5;
      component.data().checksumValido = false;
    }
    const checks = component.chequeos();
    const cuadreCheck = checks.find((c) => c.texto.includes('Cuadre'));
    expect(cuadreCheck?.ok).toBe(false);
  });

  /**
   * CRITICAL-1 (S7): 409 Conflict detection and auto-reload
   * When dispute action fails with HTTP 409, handleActionError() is invoked with
   * reloadOnConflict=true, triggering cargarDetalle() to refresh the acta state.
   * This test verifies the auto-reload behavior by confirming getActaDetalleCompleto
   * is called twice: once at init, once on 409 error.
   */
  it('should auto-reload detail on 409 Conflict from dispute action', () => {
    // Mock disputarActa to reject with 409 Conflict
    (coordinatorServiceMock.disputarActa as any).mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            statusText: 'Conflict',
            url: '/api/v1/coordinator/actas/42/disputar',
          }),
      ),
    );

    // Open modal and trigger dispute
    component.abrirModalDisputar();
    component.motivoDisputa = 'Test conflict scenario';
    component.confirmarDisputa();

    // PRIMARY ASSERTION: verify auto-reload (cargarDetalle calls getActaDetalleCompleto)
    // Initial load: 1 call. Error handler with reloadOnConflict=true: +1 call = 2 total.
    expect(coordinatorServiceMock.getActaDetalleCompleto).toHaveBeenCalledTimes(
      2,
    );
  });
});
