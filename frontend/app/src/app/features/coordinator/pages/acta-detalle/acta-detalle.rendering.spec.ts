import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { providePrimeNG } from 'primeng/config';
import { ActaDetalleCompleto } from '../../models/coordinator.model';
import { CoordinatorService } from '../../services/coordinator.service';
import { ActaDetallePage } from './acta-detalle.page';

/**
 * Rendering contract of the acta detail.
 *
 * The page loads the acta over the network, so the first render happens with
 * the detail still null. Every part of the template that reads the detail
 * must therefore live behind a guard: an unguarded read throws while the
 * data is in flight and takes the whole screen down.
 */
describe('ActaDetallePage (rendering)', () => {
  let fixture: ComponentFixture<ActaDetallePage>;
  let detalle$: Subject<ActaDetalleCompleto>;

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
    jrv: { id: 201, numero: '201', parroquia: 'Manglaralto' },
    cargo: { id: 1, nombre: 'Prefecto/a', nivel: 'PROVINCIA' },
    candidatos: [],
    totalVotosCalculado: 100,
    diferenciaChecksum: 0,
    checksumValido: true,
    historial: [
      {
        accion: 'REGISTRO',
        timestamp: '2026-09-12T10:00:00Z',
        registradoPorNombre: 'Observador',
        registradoPorIp: '10.0.0.1',
      },
    ],
  };

  beforeEach(async () => {
    detalle$ = new Subject<ActaDetalleCompleto>();

    await TestBed.configureTestingModule({
      imports: [ActaDetallePage],
      providers: [
        {
          provide: CoordinatorService,
          useValue: {
            getActaDetalleCompleto: vi
              .fn()
              .mockReturnValue(detalle$.asObservable()),
            aprobarActa: vi.fn(),
            disputarActa: vi.fn(),
            corregirActa: vi.fn(),
            subirImagenContingencia: vi.fn(),
            descargarExpedientePdf: vi.fn(),
          },
        },
        { provide: Router, useValue: { navigate: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '42' }) } },
        },
        providePrimeNG(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActaDetallePage);
  });

  it('should render while the detail is still in flight', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
  });

  it('should render the transition history once the detail arrives', () => {
    fixture.detectChanges();
    detalle$.next(mockDetalle);
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('.historial-item');
    expect(items.length).toBe(1);
    expect(items[0].textContent).toContain('REGISTRO');
  });

  it('should leave the loading state once the detail arrives', () => {
    fixture.detectChanges();
    detalle$.next(mockDetalle);
    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.querySelector('app-loading-state')).toBeNull();
  });
});
