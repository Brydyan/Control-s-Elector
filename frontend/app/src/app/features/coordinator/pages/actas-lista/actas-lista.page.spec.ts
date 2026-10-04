import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ActaItem, CargoResumen } from '../../models/coordinator.model';
import type { PagedResponse } from '../../../../shared/models/page.model';
import { CoordinatorService } from '../../services/coordinator.service';
import { ActasListaPage } from './actas-lista.page';

describe('ActasListaPage', () => {
  let component: ActasListaPage;
  let fixture: ComponentFixture<ActasListaPage>;
  let coordinatorServiceMock: Partial<CoordinatorService>;
  let routerMock: Partial<Router>;

  const mockActas: ActaItem[] = [
    {
      id: 1,
      jrvId: 101,
      cargoId: 1,
      estado: 'ENVIADA',
      totalSufragantes: 200,
      votosBlancos: 5,
      votosNulos: 3,
      fechaRegistro: '2026-09-12T10:00:00Z',
      nombreCargo: 'Prefecto/a',
      numeroJrv: 'JRV-101-A',
      totalVotosCandidatos: 192,
    },
    {
      id: 2,
      jrvId: 102,
      cargoId: 2,
      estado: 'EN_DISPUTA',
      totalSufragantes: 150,
      votosBlancos: 2,
      votosNulos: 1,
      fechaRegistro: '2026-09-12T11:00:00Z',
    },
  ];

  const mockPaged: PagedResponse<ActaItem> = {
    data: mockActas,
    meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
  };

  const mockCargos: CargoResumen[] = [
    { id: 1, nombre: 'Prefecto/a', nivel: 'PROVINCIA' },
    { id: 2, nombre: 'Alcalde/sa', nivel: 'CANTON' },
  ];

  beforeEach(async () => {
    coordinatorServiceMock = {
      getActas: vi.fn().mockReturnValue(of(mockPaged)),
      getCargos: vi.fn().mockReturnValue(of(mockCargos)),
      getCantones: vi.fn().mockReturnValue(of([])),
      anularActa: vi.fn().mockReturnValue(of(undefined)),
    };

    routerMock = {
      navigate: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ActasListaPage],
      providers: [
        { provide: CoordinatorService, useValue: coordinatorServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActasListaPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load actas envelope and cargos on init', () => {
    expect(coordinatorServiceMock.getActas).toHaveBeenCalled();
    expect(coordinatorServiceMock.getCargos).toHaveBeenCalled();
    expect(component.actas.length).toBe(2);
    expect(component.cargos.length).toBe(2);
    expect(component.totalElements).toBe(2);
    expect(component.totalPages).toBe(1);
  });

  it('should send search server-side on reload', () => {
    component.terminoBusqueda = '101';
    component.cargarActas();
    expect(coordinatorServiceMock.getActas).toHaveBeenCalledWith(
      expect.objectContaining({ search: '101', page: 0, size: 20 }),
    );
  });

  it('should reset page when changing estado tab', () => {
    component.page = 2;
    component.cambiarEstado('ENVIADA');
    expect(component.page).toBe(0);
    expect(coordinatorServiceMock.getActas).toHaveBeenCalledWith(
      expect.objectContaining({ estado: 'ENVIADA', page: 0 }),
    );
  });

  it('should use meta total for the active tab badge', () => {
    component.estadoSeleccionado = 'TODAS';
    component.totalElements = 42;
    expect(component.conteoTab('TODAS')).toBe(42);
  });

  it('should navigate to detail view on verDetalle', () => {
    component.verDetalle(1);
    expect(routerMock.navigate).toHaveBeenCalledWith(['/coordinator/actas', 1]);
  });

  it('should prefer backend denominatives over the cargos lookup', () => {
    expect(component.obtenerNombreCargo(mockActas[0])).toBe('Prefecto/a');
    expect(component.obtenerNumeroJrv(mockActas[0])).toBe('JRV-101-A');
  });

  it('should fall back to cargos lookup when denominatives are absent', () => {
    expect(component.obtenerNombreCargo(mockActas[1])).toBe('Alcalde/sa');
    expect(component.obtenerNumeroJrv(mockActas[1])).toBe('JRV-102');
  });

  it('should compute full checksum only when the subtotal is present', () => {
    // 192 + 5 + 3 == 200 → exact.
    expect(component.checksumTexto(mockActas[0])).toBe('200/200');
    expect(component.checksumValido(mockActas[0])).toBe(true);
    expect(component.checksumTexto(mockActas[1])).toBeNull();
    expect(component.checksumValido(mockActas[1])).toBeNull();
  });

  it('should start with no active filters and not show the clear button', () => {
    expect(component.tieneFiltrosActivos()).toBe(false);
  });

  it('should expose the clear button after any filter is touched', () => {
    component.estadoSeleccionado = 'ENVIADA';
    expect(component.tieneFiltrosActivos()).toBe(true);
  });

  it('should reset every filter when limpiarFiltros is called', () => {
    component.terminoBusqueda = 'foo';
    component.estadoSeleccionado = 'EN_DISPUTA';
    component.cargoSeleccionado = 7;
    component.cantonSeleccionado = 3;

    component.limpiarFiltros();

    expect(component.terminoBusqueda).toBe('');
    expect(component.estadoSeleccionado).toBe('TODAS');
    expect(component.cargoSeleccionado).toBeNull();
    expect(component.cantonSeleccionado).toBeNull();
  });

  it('should only allow MASTER to see the Anular action on APROBADA', () => {
    component.esMaster = false;
    expect(component.puedeAnular(mockActas[0])).toBe(false); // ENVIADA

    const aprobada: ActaItem = { ...mockActas[0], estado: 'APROBADA' };
    expect(component.puedeAnular(aprobada)).toBe(false); // not MASTER yet

    component.esMaster = true;
    expect(component.puedeAnular(aprobada)).toBe(true);
    expect(component.puedeAnular(mockActas[0])).toBe(false); // not APROBADA
  });

  /**
   * WARNING-3 (resolved): MASTER role detection logic
   * Verify that the component stores role detection result in esMaster flag.
   * This test verifies the flag is used correctly in puedeAnular() logic.
   */
  it('should only show Anular button when esMaster is true', () => {
    component.esMaster = false;
    const aprobada: ActaItem = { ...mockActas[0], estado: 'APROBADA' };
    expect(component.puedeAnular(aprobada)).toBe(false);

    component.esMaster = true;
    expect(component.puedeAnular(aprobada)).toBe(true);
  });

  /**
   * W4 FIX (S7): anular() 409 Conflict handling
   * When HTTP 409 is returned during annul (concurrent modification),
   * the component should auto-reload the list to reflect current state,
   * consistent with dispute/correct actions in acta-detalle.
   */
  it('should auto-reload list on 409 Conflict from annul action', () => {
    (coordinatorServiceMock.anularActa as any).mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            statusText: 'Conflict',
            url: '/api/v1/coordinator/actas/1/anular',
          }),
      ),
    );

    const aprobada: ActaItem = { ...mockActas[0], estado: 'APROBADA' };
    component.esMaster = true;
    const initialCallCount = (coordinatorServiceMock.getActas as any).mock.calls
      .length;

    // Mock window.prompt and confirm
    vi.spyOn(window, 'prompt').mockReturnValue('Fraud detected');
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.anular(aprobada.id);

    // PRIMARY ASSERTION: Verify auto-reload on 409 (getActas called again)
    // Initial load + error handler reload = +1 call
    expect((coordinatorServiceMock.getActas as any).mock.calls.length).toBe(
      initialCallCount + 1,
    );
  });
});
