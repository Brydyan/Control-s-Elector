import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ProgresoCantonesPage } from './progreso-cantones.page';
import { DashboardApi } from '../../data/dashboard.api';
import { ProgresoCantonDto } from '../../domain/models/dashboard.model';

describe('ProgresoCantonesPage (T-FE-15 C.1)', () => {
  let component: ProgresoCantonesPage;
  let fixture: ComponentFixture<ProgresoCantonesPage>;
  let dashboardServiceSpy: jasmine.SpyObj<DashboardApi>;

  const mockProgreso: ProgresoCantonDto[] = [
    {
      cantonId: 1,
      cantonNombre: 'Santa Elena',
      totalJrvs: 79,
      jrvsConActa: 67,
      porcentaje: 84.8,
      parroquias: [
        {
          parroquiaId: 10,
          parroquiaNombre: 'Santa Elena',
          jrvsEscrutadas: 40,
          jrvsTotal: 40,
          avance: 100.0,
        },
        {
          parroquiaId: 11,
          parroquiaNombre: 'La Libertad',
          jrvsEscrutadas: 27,
          jrvsTotal: 39,
          avance: 69.2,
        },
      ],
    },
    {
      cantonId: 2,
      cantonNombre: 'Salinas',
      totalJrvs: 42,
      jrvsConActa: 21,
      porcentaje: 50.0,
      parroquias: [
        {
          parroquiaId: 20,
          parroquiaNombre: 'Salinas',
          jrvsEscrutadas: 21,
          jrvsTotal: 42,
          avance: 50.0,
        },
      ],
    },
    {
      cantonId: 3,
      cantonNombre: 'La Libertad',
      totalJrvs: 58,
      jrvsConActa: 15,
      porcentaje: 25.9,
      parroquias: [
        {
          parroquiaId: 30,
          parroquiaNombre: 'Colonche',
          jrvsEscrutadas: 15,
          jrvsTotal: 58,
          avance: 25.9,
        },
      ],
    },
  ];

  beforeEach(async () => {
    dashboardServiceSpy = jasmine.createSpyObj('DashboardApi', [
      'getProgresoDetalles',
    ]);

    dashboardServiceSpy.getProgresoDetalles.and.returnValue(of(mockProgreso));

    await TestBed.configureTestingModule({
      imports: [ProgresoCantonesPage],
      providers: [{ provide: DashboardApi, useValue: dashboardServiceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(ProgresoCantonesPage);
    component = fixture.componentInstance;
  });

  // ============================================================================
  // Camino Feliz (Happy Path)
  // ============================================================================

  it('should load cantones and preselect first canton (C.1 happy path)', () => {
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
    expect(component.cantonesData().length).toBe(3);
    expect(component.cantonSeleccionado()).toBe(0);
    expect(component.cantonActual?.cantonNombre).toBe('Santa Elena');
  });

  it('should render three canton cards', () => {
    fixture.detectChanges();

    const cantonCards = fixture.nativeElement.querySelectorAll(
      '[data-testid^="canton-card-"]',
    );
    expect(cantonCards.length).toBe(3);
  });

  it('should assign color class based on avance% (S3)', () => {
    fixture.detectChanges();

    // Santa Elena: 84.8% ≥ 80 → fill-green
    expect(component.getColorClase(84.8)).toBe('fill-green');

    // Salinas: 50% (50-80) → fill-blue
    expect(component.getColorClase(50.0)).toBe('fill-blue');

    // La Libertad: 25.9% < 50 → fill-amber
    expect(component.getColorClase(25.9)).toBe('fill-amber');
  });

  it('should change canton WITHOUT making new HTTP calls (S4)', () => {
    fixture.detectChanges();

    // Verificar estado inicial
    expect(component.cantonSeleccionado()).toBe(0);
    expect(component.cantonActual?.cantonNombre).toBe('Santa Elena');

    // Cambiar a segundo cantón
    component.cambiarCanton(1);
    fixture.detectChanges();

    expect(component.cantonSeleccionado()).toBe(1);
    expect(component.cantonActual?.cantonNombre).toBe('Salinas');

    // Verificar que getProgresoDetalles solo fue llamado una vez
    expect(dashboardServiceSpy.getProgresoDetalles).toHaveBeenCalledTimes(1);
  });

  it('should render parroquias table with descending sort by avance% (S5)', () => {
    fixture.detectChanges();

    const parroquiasOrdenadas = component.parroquiasOrdenadas;

    // Santa Elena tiene 2 parroquias: Santa Elena (100%) y La Libertad (69.2%)
    // Deben estar ordenadas descendente por avance
    expect(parroquiasOrdenadas[0].parroquiaNombre).toBe('Santa Elena');
    expect(parroquiasOrdenadas[0].avance).toBe(100.0);
    expect(parroquiasOrdenadas[1].parroquiaNombre).toBe('La Libertad');
    expect(parroquiasOrdenadas[1].avance).toBe(69.2);
  });

  it('should show error state and retry button on failure', async () => {
    dashboardServiceSpy.getProgresoDetalles.and.returnValue(
      throwError(() => new Error('API error')),
    );

    fixture.detectChanges();

    // Wait for async subscription
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(component.loading()).toBe(false);
    expect(component.error()).toBeTruthy();
    expect(component.error()).toContain('No se pudieron cargar');
  });

  // ============================================================================
  // Límites (Boundary Conditions)
  // ============================================================================

  it('should handle canton with no parroquias', () => {
    const emptyParroquiasProgreso: ProgresoCantonDto[] = [
      {
        ...mockProgreso[0],
        parroquias: [],
      },
    ];

    dashboardServiceSpy.getProgresoDetalles.and.returnValue(
      of(emptyParroquiasProgreso),
    );

    fixture.detectChanges();

    expect(component.cantonActual?.parroquias.length).toBe(0);
    expect(component.parroquiasOrdenadas.length).toBe(0);
  });

  it('should handle avance boundary values correctly', () => {
    // Exactamente 80% debe ser green
    expect(component.getColorClase(80.0)).toBe('fill-green');

    // Exactamente 50% debe ser blue
    expect(component.getColorClase(50.0)).toBe('fill-blue');

    // Justo por debajo de 80% debe ser blue
    expect(component.getColorClase(79.9)).toBe('fill-blue');

    // Justo por debajo de 50% debe ser amber
    expect(component.getColorClase(49.9)).toBe('fill-amber');
  });

  // ============================================================================
  // Accesibilidad (A11y)
  // ============================================================================

  it('should have aria-label on barra-progreso of cantones', () => {
    fixture.detectChanges();

    const barras = fixture.nativeElement.querySelectorAll(
      '[data-testid^="canton-card-"] [role="progressbar"]',
    );
    expect(barras.length).toBeGreaterThan(0);

    barras.forEach((barra: HTMLElement) => {
      expect(barra.getAttribute('aria-label')).toBeTruthy();
      expect(barra.getAttribute('aria-valuenow')).toBeTruthy();
      expect(barra.getAttribute('aria-valuemin')).toBe('0');
      expect(barra.getAttribute('aria-valuemax')).toBe('100');
    });
  });

  it('should have role grid on parroquias table', () => {
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector(
      '[data-testid="parroquias-section"] table',
    );
    expect(table).toBeTruthy();
    expect(table.getAttribute('role')).toBe('grid');
  });

  // ============================================================================
  // Mutaciones (Mutation Test Coverage)
  // ============================================================================

  it('MUTATION: getColorClase must assign color based on avance, not always fill-green', () => {
    // Si mutamos para siempre retornar 'fill-green', este test cae
    const colorAlto = component.getColorClase(85.0); // ≥80 → green
    const colorMedio = component.getColorClase(65.0); // 50-80 → blue
    const colorBajo = component.getColorClase(25.0); // <50 → amber

    expect(colorAlto).toBe('fill-green');
    expect(colorMedio).toBe('fill-blue');
    expect(colorBajo).toBe('fill-amber');

    // Esta aserción cae si siempre retorna fill-green
    expect(colorMedio).not.toBe('fill-green');
    expect(colorBajo).not.toBe('fill-green');
  });
});
