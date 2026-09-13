import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
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
});
