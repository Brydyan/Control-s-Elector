import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MasterCargosPage } from './master-cargos.page';
import { CatalogoService } from '../../services/catalogo.service';
import { CargoResponseDto } from '../../models/catalogo.model';

describe('MasterCargosPage', () => {
  let component: MasterCargosPage;
  let catalogoServiceMock: any;

  const mockCargos: CargoResponseDto[] = [
    { id: 1, nombre: 'Presidente', nivel: 'NACIONAL', puestos: 1 },
    { id: 2, nombre: 'Asambleísta', nivel: 'PROVINCIA', puestos: 4 },
  ];

  beforeEach(() => {
    catalogoServiceMock = {
      listarCargos: vi.fn().mockReturnValue(of(mockCargos)),
      crearCargo: vi
        .fn()
        .mockReturnValue(
          of({ id: 3, nombre: 'Alcalde', nivel: 'CANTON', puestos: 1 }),
        ),
      actualizarCargo: vi
        .fn()
        .mockReturnValue(
          of({
            id: 1,
            nombre: 'Presidente Editado',
            nivel: 'NACIONAL',
            puestos: 1,
          }),
        ),
      eliminarCargo: vi.fn().mockReturnValue(of(undefined)),
    };

    TestBed.configureTestingModule({
      providers: [
        MasterCargosPage,
        { provide: CatalogoService, useValue: catalogoServiceMock },
      ],
    });

    component = TestBed.inject(MasterCargosPage);
  });

  it('debe crearse y cargar los cargos en el ngOnInit', () => {
    component.ngOnInit();
    expect(catalogoServiceMock.listarCargos).toHaveBeenCalled();
    expect(component.cargos()).toEqual(mockCargos);
    expect(component.cargosFiltrados().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('debe filtrar por término de búsqueda y nivel', () => {
    component.ngOnInit();

    component.terminoBusqueda.set('Asamble');
    expect(component.cargosFiltrados().length).toBe(1);
    expect(component.cargosFiltrados()[0].nombre).toBe('Asambleísta');

    component.terminoBusqueda.set('');
    component.nivelSeleccionado.set('NACIONAL');
    expect(component.cargosFiltrados().length).toBe(1);
    expect(component.cargosFiltrados()[0].nombre).toBe('Presidente');
  });

  it('debe abrir y validar formulario de creación', () => {
    component.abrirCrear();
    expect(component.modalAbierto).toBe(true);
    expect(component.modoEdicion).toBe(false);

    component.nombre = '';
    component.guardar();
    expect(component.modalError()).toContain('obligatorio');

    component.nombre = 'Concejal';
    component.puestos = 0;
    component.guardar();
    expect(component.modalError()).toContain('al menos 1 puesto');

    component.puestos = 3;
    component.guardar();
    expect(catalogoServiceMock.crearCargo).toHaveBeenCalledWith({
      nombre: 'Concejal',
      nivel: 'NACIONAL',
      puestos: 3,
    });
    expect(component.modalAbierto).toBe(false);
  });

  it('debe abrir modal de edición y actualizar cargo', () => {
    component.abrirEditar(mockCargos[0]);
    expect(component.modalAbierto).toBe(true);
    expect(component.modoEdicion).toBe(true);
    expect(component.nombre).toBe('Presidente');

    component.nombre = 'Presidente de la República';
    component.guardar();
    expect(catalogoServiceMock.actualizarCargo).toHaveBeenCalledWith(1, {
      nombre: 'Presidente de la República',
      nivel: 'NACIONAL',
      puestos: 1,
    });
  });
});
