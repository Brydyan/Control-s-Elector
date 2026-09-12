import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { ActaItem, CargoResumen } from '../../models/coordinator.model';
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

  const mockCargos: CargoResumen[] = [
    { id: 1, nombre: 'Prefecto/a', nivel: 'PROVINCIA' },
    { id: 2, nombre: 'Alcalde/sa', nivel: 'CANTON' },
  ];

  beforeEach(async () => {
    coordinatorServiceMock = {
      getActas: vi.fn().mockReturnValue(of(mockActas)),
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

  it('should load actas and cargos on init', () => {
    expect(coordinatorServiceMock.getActas).toHaveBeenCalled();
    expect(coordinatorServiceMock.getCargos).toHaveBeenCalled();
    expect(component.actas.length).toBe(2);
    expect(component.cargos.length).toBe(2);
  });

  it('should filter actas by search query', () => {
    component.terminoBusqueda = '101';
    expect(component.actasFiltradas.length).toBe(1);
    expect(component.actasFiltradas[0].jrvId).toBe(101);
  });

  it('should navigate to detail view on verDetalle', () => {
    component.verDetalle(1);
    expect(routerMock.navigate).toHaveBeenCalledWith(['/coordinator/actas', 1]);
  });
});
