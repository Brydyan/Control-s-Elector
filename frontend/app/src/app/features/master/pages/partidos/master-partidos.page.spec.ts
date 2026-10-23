import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MasterPartidosPage } from './master-partidos.page';
import { CatalogoService } from '../../services/catalogo.service';
import { PartidoResponseDto } from '../../models/catalogo.model';

describe('MasterPartidosPage', () => {
  let component: MasterPartidosPage;
  let catalogoServiceMock: any;

  const mockPartidos: PartidoResponseDto[] = [
    { id: 1, nombre: 'Revolución Ciudadana', siglas: 'RC', numeroLista: 5 },
    {
      id: 2,
      nombre: 'Partido Social Cristiano',
      siglas: 'PSC',
      numeroLista: 6,
    },
  ];

  beforeEach(() => {
    catalogoServiceMock = {
      listarPartidos: vi.fn().mockReturnValue(of(mockPartidos)),
      crearPartido: vi
        .fn()
        .mockReturnValue(
          of({ id: 3, nombre: 'Construye', siglas: 'MC25', numeroLista: 25 }),
        ),
      actualizarPartido: vi
        .fn()
        .mockReturnValue(
          of({ id: 1, nombre: 'RC5', siglas: 'RC', numeroLista: 5 }),
        ),
      eliminarPartido: vi.fn().mockReturnValue(of(undefined)),
    };

    TestBed.configureTestingModule({
      providers: [
        MasterPartidosPage,
        { provide: CatalogoService, useValue: catalogoServiceMock },
      ],
    });

    component = TestBed.inject(MasterPartidosPage);
  });

  it('debe crearse y cargar los partidos en el ngOnInit', () => {
    component.ngOnInit();
    expect(catalogoServiceMock.listarPartidos).toHaveBeenCalled();
    expect(component.partidos()).toEqual(mockPartidos);
    expect(component.partidosFiltrados().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('debe filtrar partidos por nombre, siglas o lista', () => {
    component.ngOnInit();

    component.terminoBusqueda.set('social');
    expect(component.partidosFiltrados().length).toBe(1);
    expect(component.partidosFiltrados()[0].siglas).toBe('PSC');

    component.terminoBusqueda.set('5');
    expect(component.partidosFiltrados().length).toBe(1);
    expect(component.partidosFiltrados()[0].numeroLista).toBe(5);
  });

  it('debe validar y crear un partido', () => {
    component.abrirCrear();
    expect(component.modalAbierto).toBe(true);

    component.nombre = '';
    component.guardar();
    expect(component.modalError()).toContain('obligatorio');

    component.nombre = 'Construye';
    component.siglas = 'MC25';
    component.numeroLista = 25;
    component.guardar();

    expect(catalogoServiceMock.crearPartido).toHaveBeenCalledWith({
      nombre: 'Construye',
      siglas: 'MC25',
      numeroLista: 25,
    });
    expect(component.modalAbierto).toBe(false);
  });

  it('debe abrir modal de edición y actualizar un partido', () => {
    component.abrirEditar(mockPartidos[0]);
    expect(component.modoEdicion).toBe(true);
    expect(component.siglas).toBe('RC');

    component.nombre = 'RC5';
    component.guardar();

    expect(catalogoServiceMock.actualizarPartido).toHaveBeenCalledWith(1, {
      nombre: 'RC5',
      siglas: 'RC',
      numeroLista: 5,
    });
  });
});
