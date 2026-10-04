import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DashboardPage } from './dashboard.page';
import { DashboardRealtimeService } from './services/dashboard-realtime.service';
import { DashboardService } from './services/dashboard.service';

/**
 * Tests del componente dashboard (T-FE-11, B.3).
 */
describe('DashboardPage (T-FE-11)', () => {
  let component: DashboardPage;
  let fixture: ComponentFixture<DashboardPage>;
  let dashboardServiceMock: Partial<DashboardService>;
  let realtimeMock: Partial<DashboardRealtimeService>;

  const mockData = {
    resumen: {
      enviadas: 1,
      enDisputa: 2,
      corregidas: 3,
      aprobadas: 4,
      totalActivas: 10,
    },
    resultados: [
      { cargoId: 1, cargoNombre: 'Prefecto/a', candidatos: [] },
    ],
    progreso: [
      {
        cantonId: 1,
        cantonNombre: 'Santa Elena',
        totalJrvs: 200,
        jrvsConActa: 150,
        porcentaje: 75,
      },
      {
        cantonId: 2,
        cantonNombre: 'La Libertad',
        totalJrvs: 150,
        jrvsConActa: 100,
        porcentaje: 66.66,
      },
    ],
    novedades: [
      {
        actaPublicId: '11111111-1111-1111-1111-111111111111',
        tipo: 'ACTA_EN_DISPUTA',
        jrvId: 1,
        jrvNumero: '0023',
        cargoId: 1,
        cargoNombre: 'Alcalde/sa',
        observerPublicId: null,
        observerNombre: null,
        motivo: null,
        minutosTranscurridos: 35,
      },
    ],
  };

  beforeEach(async () => {
    dashboardServiceMock = {
      getDashboardCompleto: vi.fn().mockReturnValue(of(mockData)),
    };
    realtimeMock = {
      reload$: of(),
      start: vi.fn(),
      stop: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        { provide: DashboardService, useValue: dashboardServiceMock },
        { provide: DashboardRealtimeService, useValue: realtimeMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load dashboard data on init', () => {
    expect(component).toBeTruthy();
    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
    expect(component.resumen().totalActivas).toBe(10);
  });

  it('should expose canton progress with computed pct', () => {
    expect(component.totalJrv()).toBe(350);
    expect(component.sentActos()).toBe(250);
    // 250/350 = 71.4% ≈ 71
    expect(component.overallPct()).toBe(71);
    expect(component.pct(component.progreso()[0])).toBe(75);
  });

  it('should color tier green/blue/amber by completion', () => {
    expect(component.tier({ ...component.progreso()[0], porcentaje: 100 })).toBe('green');
    expect(component.tier({ ...component.progreso()[0], porcentaje: 80 })).toBe('blue');
    expect(component.tier({ ...component.progreso()[0], porcentaje: 30 })).toBe('amber');
  });

  it('should subscribe to realtime reload on init and stop on destroy', () => {
    expect(realtimeMock.start).toHaveBeenCalled();
    component.ngOnDestroy();
    expect(realtimeMock.stop).toHaveBeenCalled();
  });

  it('should render error state when service fails', () => {
    (dashboardServiceMock.getDashboardCompleto as ReturnType<typeof vi.fn>)
      .mockReturnValue(throwError(() => new Error('boom')));
    component.cargar();
    expect(component.error()).toBe('boom');
    expect(component.loading()).toBe(false);
  });

  it('should reload when realtime emits', async () => {
    const reload$ = of('INSERT');
    (realtimeMock.reload$ as typeof reload$) = reload$;
    // Re-create component to apply new mock
    fixture = TestBed.createComponent(DashboardPage);
    const comp2 = fixture.componentInstance;
    fixture.detectChanges();
    expect(dashboardServiceMock.getDashboardCompleto).toHaveBeenCalled();
  });
});