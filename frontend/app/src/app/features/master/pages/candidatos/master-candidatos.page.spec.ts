import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MasterCandidatosPage } from './master-candidatos.page';
import { CatalogoApi } from '../../data/catalogo.api';
import {
  CandidatoResponseDto,
  CargoResponseDto,
  PartidoResponseDto,
} from '../../domain/models/catalogo.model';

describe('MasterCandidatosPage', () => {
  let component: MasterCandidatosPage;
  let catalogoServiceMock: any;

  const mockCargos: CargoResponseDto[] = [
    { id: 1, nombre: 'Presidente', nivel: 'NACIONAL', puestos: 1 },
    { id: 2, nombre: 'Asambleísta', nivel: 'PROVINCIA', puestos: 4 },
  ];

  const mockPartidos: PartidoResponseDto[] = [
    { id: 10, nombre: 'Revolución Ciudadana', siglas: 'RC', numeroLista: 5 },
    {
      id: 20,
      nombre: 'Partido Social Cristiano',
      siglas: 'PSC',
      numeroLista: 6,
    },
  ];

  const mockCandidatos: CandidatoResponseDto[] = [
    { id: 100, nombre: 'Luisa González', orden: 1, cargoId: 1, partidoId: 10 },
    { id: 101, nombre: 'Henry Kronfle', orden: 1, cargoId: 1, partidoId: 20 },
  ];

  beforeEach(() => {
    catalogoServiceMock = {
      listarCargos: vi.fn().mockReturnValue(of(mockCargos)),
      listarPartidos: vi.fn().mockReturnValue(of(mockPartidos)),
      listarCandidatos: vi.fn().mockReturnValue(of(mockCandidatos)),
      crearCandidato: vi.fn().mockReturnValue(
        of({
          id: 102,
          nombre: 'Nuevo Candidato',
          orden: 2,
          cargoId: 1,
          partidoId: 10,
        }),
      ),
      actualizarCandidato: vi.fn().mockReturnValue(
        of({
          id: 100,
          nombre: 'Luisa G. Actualizada',
          orden: 1,
          cargoId: 1,
          partidoId: 10,
        }),
      ),
      eliminarCandidato: vi.fn().mockReturnValue(of(undefined)),
    };

    TestBed.configureTestingModule({
      providers: [
        MasterCandidatosPage,
        { provide: CatalogoApi, useValue: catalogoServiceMock },
      ],
    });

    component = TestBed.inject(MasterCandidatosPage);
  });

  it('debe inicializarse y cargar cargos, partidos y candidatos', () => {
    component.ngOnInit();
    expect(catalogoServiceMock.listarCargos).toHaveBeenCalled();
    expect(catalogoServiceMock.listarPartidos).toHaveBeenCalled();
    expect(catalogoServiceMock.listarCandidatos).toHaveBeenCalled();

    expect(component.cargos()).toEqual(mockCargos);
    expect(component.partidos()).toEqual(mockPartidos);
    expect(component.candidatos()).toEqual(mockCandidatos);
    expect(component.candidatosFiltrados().length).toBe(2);
  });

  it('debe filtrar candidatos por búsqueda, cargo y partido', () => {
    component.ngOnInit();

    component.terminoBusqueda.set('Kronfle');
    expect(component.candidatosFiltrados().length).toBe(1);
    expect(component.candidatosFiltrados()[0].nombre).toBe('Henry Kronfle');

    component.terminoBusqueda.set('');
    component.partidoFiltroId.set(10);
    expect(component.candidatosFiltrados().length).toBe(1);
    expect(component.candidatosFiltrados()[0].nombre).toBe('Luisa González');
  });

  it('debe abrir y registrar un nuevo candidato', () => {
    component.ngOnInit();
    component.abrirCrear();

    expect(component.modalAbierto).toBe(true);
    expect(component.cargoId).toBe(1);
    expect(component.partidoId).toBe(10);

    component.nombre = 'Candidato Test';
    component.orden = 2;
    component.guardar();

    expect(catalogoServiceMock.crearCandidato).toHaveBeenCalledWith({
      nombre: 'Candidato Test',
      cargoId: 1,
      partidoId: 10,
      orden: 2,
    });
    expect(component.modalAbierto).toBe(false);
  });

  it('debe abrir modal de edición y actualizar candidato', () => {
    component.ngOnInit();
    component.abrirEditar(mockCandidatos[0]);

    expect(component.modoEdicion).toBe(true);
    expect(component.nombre).toBe('Luisa González');

    component.nombre = 'Luisa G. Actualizada';
    component.guardar();

    expect(catalogoServiceMock.actualizarCandidato).toHaveBeenCalledWith(100, {
      nombre: 'Luisa G. Actualizada',
      cargoId: 1,
      partidoId: 10,
      orden: 1,
    });
  });
});
