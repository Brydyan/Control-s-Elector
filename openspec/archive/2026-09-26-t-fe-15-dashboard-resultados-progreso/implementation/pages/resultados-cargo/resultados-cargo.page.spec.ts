import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ResultadosCargoPage } from './resultados-cargo.page';
import { DashboardApi } from '../../data/dashboard.api';
import { ResultadoCargoDto } from '../../domain/models/dashboard.model';

describe('ResultadosCargoPage (T-FE-15 B.1)', () => {
  let component: ResultadosCargoPage;
  let fixture: ComponentFixture<ResultadosCargoPage>;
  let dashboardServiceSpy: jasmine.SpyObj<DashboardApi>;

  const mockResultados: ResultadoCargoDto[] = [
    {
      cargoId: 1,
      cargoNombre: 'Presidente',
      votosValidos: 500000,
      votosBlancos: 5000,
      votosNulos: 3000,
      candidatos: [
        {
          candidatoId: 101,
          candidatoNombre: 'Juan Pérez',
          partidoNombre: 'Movimiento X',
          votos: 300000,
          porcentaje: 60.0,
          estado: 'GANADOR_PROVISIONAL',
          fotoUrl: 'https://ejemplo.com/juan.jpg',
        },
        {
          candidatoId: 102,
          candidatoNombre: 'María García',
          partidoNombre: 'Partido Y',
          votos: 150000,
          porcentaje: 30.0,
          estado: 'EN_CONTEO',
          fotoUrl: null,
        },
        {
          candidatoId: 103,
          candidatoNombre: 'Carlos López',
          partidoNombre: 'Movimiento Z',
          votos: 50000,
          porcentaje: 10.0,
          estado: 'PERDEDOR_PROVISIONAL',
          fotoUrl: 'https://ejemplo.com/carlos.jpg',
        },
      ],
    },
    {
      cargoId: 2,
      cargoNombre: 'Vicepresidente',
      votosValidos: 480000,
      votosBlancos: 6000,
      votosNulos: 4000,
      candidatos: [
        {
          candidatoId: 201,
          candidatoNombre: 'Ana Silva',
          partidoNombre: 'Movimiento X',
          votos: 280000,
          porcentaje: 58.3,
          estado: 'GANADOR_PROVISIONAL',
          fotoUrl: 'https://ejemplo.com/ana.jpg',
        },
      ],
    },
  ];

  beforeEach(async () => {
    dashboardServiceSpy = jasmine.createSpyObj('DashboardApi', [
      'getResultadosDetalles',
    ]);

    dashboardServiceSpy.getResultadosDetalles.and.returnValue(
      of(mockResultados),
    );

    await TestBed.configureTestingModule({
      imports: [ResultadosCargoPage],
      providers: [{ provide: DashboardApi, useValue: dashboardServiceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(ResultadosCargoPage);
    component = fixture.componentInstance;
  });

  // ============================================================================
  // Camino Feliz (Happy Path)
  // ============================================================================

  it('should load resultados and preselect first tab (B.1 happy path)', () => {
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
    expect(component.cargosData().length).toBe(2);
    expect(component.cargoSeleccionado()).toBe(0);
    expect(component.cargoActual?.cargoNombre).toBe('Presidente');
  });

  it('should render cargo tabs with correct labels', () => {
    fixture.detectChanges();

    const tabs = fixture.nativeElement.querySelectorAll('[role="tab"]');
    expect(tabs.length).toBe(2);
    expect(tabs[0].textContent).toContain('Presidente');
    expect(tabs[1].textContent).toContain('Vicepresidente');
  });

  it('should change cargo tab WITHOUT making new HTTP calls (S2)', () => {
    fixture.detectChanges();

    // Primer cargo
    expect(component.cargoSeleccionado()).toBe(0);
    expect(component.cargoActual?.cargoNombre).toBe('Presidente');

    // Cambiar a segundo cargo
    component.cambiarCargo(1);
    fixture.detectChanges();

    expect(component.cargoSeleccionado()).toBe(1);
    expect(component.cargoActual?.cargoNombre).toBe('Vicepresidente');

    // Verificar que getResultadosDetalles solo fue llamado una vez
    expect(dashboardServiceSpy.getResultadosDetalles).toHaveBeenCalledTimes(1);
  });

  it('should display candidato líder (S3)', () => {
    fixture.detectChanges();

    expect(component.candidatoLider?.candidatoNombre).toBe('Juan Pérez');
    expect(component.candidatoLider?.votos).toBe(300000);
    expect(component.candidatoLider?.estado).toBe('GANADOR_PROVISIONAL');
  });

  it('should calculate barra width proportional to votes (S4)', () => {
    fixture.detectChanges();

    const cargo = component.cargoActual;
    if (!cargo) return;

    // Juan: 300000 votos (máximo) → 100%
    const juanWidth = component.porcentajeAncho(cargo.candidatos[0]);
    expect(juanWidth).toBe(100);

    // María: 150000 votos (mitad) → 50%
    const mariaWidth = component.porcentajeAncho(cargo.candidatos[1]);
    expect(mariaWidth).toBe(50);

    // Carlos: 50000 votos (1/6) → 16.67%
    const carlosWidth = component.porcentajeAncho(cargo.candidatos[2]);
    expect(carlosWidth).toBeCloseTo(16.67, 1);
  });

  it('should render resumen cards with votos válidos, blancos, nulos (S5)', () => {
    fixture.detectChanges();

    const resumenSection = fixture.nativeElement.querySelector(
      '[data-testid="resumen-section"]',
    );
    expect(resumenSection).toBeTruthy();

    const resumenText = resumenSection.textContent;
    expect(resumenText).toContain('Votos Válidos');
    expect(resumenText).toContain('500000');
    expect(resumenText).toContain('Votos Blancos');
    expect(resumenText).toContain('5000');
    expect(resumenText).toContain('Votos Nulos');
    expect(resumenText).toContain('3000');
  });

  it('should show error state and retry button on failure', async () => {
    dashboardServiceSpy.getResultadosDetalles.and.returnValue(
      throwError(() => new Error('API error')),
    );

    fixture.detectChanges();

    // Wait for async subscription
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(component.loading()).toBe(false);
    expect(component.error()).toBeTruthy();
    expect(component.error()).toContain('No se pudieron cargar');
  });

  it('should retry loading on error', async () => {
    dashboardServiceSpy.getResultadosDetalles.and.returnValue(
      throwError(() => new Error('API error')),
    );

    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 100));

    // Cambiar spy para que retorne éxito en siguiente llamada
    dashboardServiceSpy.getResultadosDetalles.and.returnValue(
      of(mockResultados),
    );

    // Llamar retry
    component.cargarResultados();
    fixture.detectChanges();

    expect(component.error()).toBeNull();
    expect(component.loading()).toBe(false);
    expect(component.cargosData().length).toBe(2);
  });

  // ============================================================================
  // Límites (Boundary Conditions)
  // ============================================================================

  it('should handle empty candidatos list', () => {
    const emptyResultados: ResultadoCargoDto[] = [
      {
        ...mockResultados[0],
        candidatos: [],
      },
    ];

    dashboardServiceSpy.getResultadosDetalles.and.returnValue(
      of(emptyResultados),
    );

    fixture.detectChanges();

    expect(component.cargoActual?.candidatos.length).toBe(0);
    expect(component.candidatoLider).toBeNull();
  });

  it('should handle all candidatos with zero votos', () => {
    const zeroVotesResultados: ResultadoCargoDto[] = [
      {
        ...mockResultados[0],
        candidatos: mockResultados[0].candidatos.map((c) => ({
          ...c,
          votos: 0,
        })),
      },
    ];

    dashboardServiceSpy.getResultadosDetalles.and.returnValue(
      of(zeroVotesResultados),
    );

    fixture.detectChanges();

    const cargo = component.cargoActual;
    if (cargo) {
      // Con votos=0, porcentajeAncho debe retornar 0
      expect(component.porcentajeAncho(cargo.candidatos[0])).toBe(0);
    }
  });

  // ============================================================================
  // Accesibilidad (A11y)
  // ============================================================================

  it('should have aria-label on tabs', () => {
    fixture.detectChanges();

    const tabs = fixture.nativeElement.querySelectorAll('[role="tab"]');
    expect(tabs[0].getAttribute('aria-label')).toBe('Resultados de Presidente');
    expect(tabs[1].getAttribute('aria-label')).toBe(
      'Resultados de Vicepresidente',
    );
  });

  it('should have role progressbar on barras', () => {
    fixture.detectChanges();

    const barras = fixture.nativeElement.querySelectorAll(
      '[role="progressbar"]',
    );
    expect(barras.length).toBeGreaterThan(0);

    barras.forEach((barra: HTMLElement) => {
      expect(barra.getAttribute('aria-valuenow')).toBeTruthy();
      expect(barra.getAttribute('aria-valuemin')).toBe('0');
      expect(barra.getAttribute('aria-valuemax')).toBeTruthy();
    });
  });

  it('should have alt text on candidato lider foto', () => {
    fixture.detectChanges();

    const foto = fixture.nativeElement.querySelector(
      '[data-testid="lider-foto"]',
    );
    expect(foto).toBeTruthy();
    expect(foto.getAttribute('alt')).toContain('Foto de');
  });

  // ============================================================================
  // Mutaciones (Mutation Test Coverage)
  // ============================================================================

  it('MUTATION: porcentajeAncho must calculate proportional width, not always 100%', () => {
    fixture.detectChanges();

    const cargo = component.cargoActual;
    if (!cargo) return;

    // Si mutamos para siempre retornar 100%, este test cae
    const juan = cargo.candidatos[0]; // 300000 votos (máximo)
    const maria = cargo.candidatos[1]; // 150000 votos (mitad)

    const juanWidth = component.porcentajeAncho(juan);
    const mariaWidth = component.porcentajeAncho(maria);

    // Juan debe ser 100, María 50
    expect(juanWidth).toBe(100);
    expect(mariaWidth).toBe(50);
    expect(juanWidth).not.toBe(mariaWidth); // Esta aserción cae si siempre retorna 100%
  });

  it('MUTATION: cambiarCargo must update cargoSeleccionado, not ignore index', () => {
    fixture.detectChanges();

    // Estado inicial
    expect(component.cargoSeleccionado()).toBe(0);

    // Cambiar a índice 1
    component.cambiarCargo(1);

    // Si mutamos cambiarCargo para ignorar el índice, cargoSeleccionado seguirá siendo 0
    // Este test cae en ese caso
    expect(component.cargoSeleccionado()).toBe(1);
    expect(component.cargoActual?.cargoNombre).toBe('Vicepresidente');
  });
});
