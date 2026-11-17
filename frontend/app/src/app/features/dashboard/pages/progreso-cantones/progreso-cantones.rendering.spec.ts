import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { providePrimeNG } from 'primeng/config';

import { ProgresoCantonesPage } from './progreso-cantones.page';
import { DashboardApi } from '../../data/dashboard.api';
import type { ProgresoCantonDto } from '../../domain/models/dashboard.model';

/**
 * Rendering contract de la vista de Progreso.
 *
 * El spec de la pagina (`progreso-cantones.page.spec.ts`) prueba la LOGICA del
 * componente (`parroquiasOrdenadas`, orden, colores). No renderiza la plantilla.
 * Eso dejaba un hueco real: los nombres de campo que lee el template
 * (`parroquia.parroquiaId`, `parroquia.parroquiaNombre`) son los mismos que
 * declara el modelo `ParroquiaProgresoDto`, pero si la plantilla y el modelo
 * se desalinean, la logica sigue verde y las celdas salen vacias en pantalla.
 *
 * Este spec renderiza el DOM y mira lo que el usuario ve de verdad.
 */
describe('ProgresoCantonesPage (rendering)', () => {
  let fixture: ComponentFixture<ProgresoCantonesPage>;

  const cantones: ProgresoCantonDto[] = [
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
  ];

  const dashService = {
    getProgresoDetalles: () => of(cantones),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProgresoCantonesPage],
      providers: [
        { provide: DashboardApi, useValue: dashService },
        providePrimeNG(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProgresoCantonesPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('should leave the loading state once the data arrives', () => {
    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.querySelector('app-loading-state')).toBeNull();
  });

  it('should render one row per parroquia of the selected canton', () => {
    const rows = fixture.nativeElement.querySelectorAll(
      '[data-testid^="parroquia-row-"]',
    ) as NodeListOf<HTMLElement>;

    expect(rows.length).toBe(2);
  });

  it('should render the parroquia name from the model field', () => {
    // El nombre sale de `parroquia.parroquiaNombre`. Si la plantilla leyera
    // `parroquiasNombre` (que no existe en el modelo), la celda saldria vacia
    // y este test caeria.
    const nombres = Array.from(
      fixture.nativeElement.querySelectorAll(
        '.parroquias-table tbody .col-nombre',
      ) as NodeListOf<HTMLElement>,
    ).map((el) => el.textContent?.trim());

    expect(nombres).toEqual(['Santa Elena', 'La Libertad']);
  });

  it('should render thejut counts and the progress of each parroquia', () => {
    const filas = Array.from(
      fixture.nativeElement.querySelectorAll(
        '.parroquias-table tbody tr',
      ) as NodeListOf<HTMLElement>,
    );

    const primera = filas[0];
    const celdas = Array.from(
      primera.querySelectorAll('td') as NodeListOf<HTMLElement>,
    ).map((el) => el.textContent?.trim());

    // nombre | jrvs scrutadas | jrvs total | avance
    expect(celdas[1]).toBe('40');
    expect(celdas[2]).toBe('40');
    expect(celdas[3]).toContain('100');
  });

  it('should expose the avance as a progressbar for screen readers', () => {
    // Acotado a la tabla de parroquias: las tarjetas de canton tambien llevan
    // su propia barra con role="progressbar".
    const barras = fixture.nativeElement.querySelectorAll(
      '.parroquias-table [role="progressbar"]',
    ) as NodeListOf<HTMLElement>;

    expect(barras.length).toBe(2);
    expect(barras[0].getAttribute('aria-valuenow')).toBe('100');
    expect(barras[0].getAttribute('aria-label')).toContain('Santa Elena');
  });

  it('should paint each parroquia row with the tier color of its avance', () => {
    // La mini-barra de parroquia era verde FIJO: un canton recien iniciado (69%) se
    // pintaba igual que uno al 100%. El color tiene que venir del avance, con el
    // mismo criterio que las tarjetas cantonales (S3).
    const celdas = Array.from(
      fixture.nativeElement.querySelectorAll(
        '.parroquias-table tbody .col-avance .avance-cell',
      ) as NodeListOf<HTMLElement>,
    );

    expect(celdas.length).toBe(2);
    // Santa Elena 100% -> verde
    expect(celdas[0].classList).toContain('fill-green');
    // La Libertad 69.2% -> azul (50-80)
    expect(celdas[1].classList).toContain('fill-blue');
  });

  it('should show an empty state when the canton has no parroquias', () => {
    // Con el desglose vacio, el panel tiene que decir que no hay parroquias y no
    // dejar una tabla muda: "sin datos" y "el backend no manda el desglose" se
    // ven distinto. El caso es un canton SIN parroquias, no una lista sin
    // cantones (en ese caso el bloque entero no se renderiza).
    fixture.componentInstance.cantonesData.set([
      {
        cantonId: 2,
        cantonNombre: 'Sin parroquias',
        totalJrvs: 0,
        jrvsConActa: 0,
        porcentaje: 0,
        parroquias: [],
      },
    ]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector(
        '[data-testid="parroquias-empty-state"]',
      ),
    ).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.parroquias-table')).toBeNull();
  });
});
