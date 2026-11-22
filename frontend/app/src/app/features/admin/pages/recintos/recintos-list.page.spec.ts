import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { RecintosListPage } from './recintos-list.page';
import { RecintoApi, RecintoResponse } from '../../data/recinto.api';
import { PagedResponse } from '../../../../shared/models/page.model';

describe('RecintosListPage', () => {
  let component: RecintosListPage;
  let fixture: ComponentFixture<RecintosListPage>;
  let recintoApi: jasmine.SpyObj<RecintoApi>;

  const mockRecintos: RecintoResponse[] = [
    {
      id: 1,
      nombre: 'Escuela República',
      parroquiaId: 1,
      parroquia: 'Centro, Pichincha',
      coordinadorId: 10,
      coordinador: { id: 10, nombre: 'Juan Pérez', email: 'juan@test.com' },
      totalMesas: 5,
      estado: 'ACTIVO',
      fechaCreacion: '2026-11-01T10:00:00Z',
    },
    {
      id: 2,
      nombre: 'Escuela Nueva',
      parroquiaId: 2,
      parroquia: 'Sur, Pichincha',
      coordinadorId: 11,
      coordinador: { id: 11, nombre: 'María García', email: 'maria@test.com' },
      totalMesas: 3,
      estado: 'ACTIVO',
      fechaCreacion: '2026-11-02T10:00:00Z',
    },
  ];

  beforeEach(async () => {
    recintoApi = jasmine.createSpyObj('RecintoApi', [
      'listarRecintos',
      'crearRecinto',
      'actualizarRecinto',
      'eliminarRecinto',
    ]);

    await TestBed.configureTestingModule({
      imports: [RecintosListPage],
      providers: [{ provide: RecintoApi, useValue: recintoApi }],
    }).compileComponents();

    fixture = TestBed.createComponent(RecintosListPage);
    component = fixture.componentInstance;
  });

  describe('cargar lista', () => {
    it('debería cargar recintos al iniciar', () => {
      const response: PagedResponse<RecintoResponse> = {
        data: mockRecintos,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      recintoApi.listarRecintos.and.returnValue(of(response));

      component.ngOnInit();

      expect(component.recintos()).toEqual(mockRecintos);
      expect(component.totalElements()).toBe(2);
      expect(component.loading()).toBe(false);
    });

    it('debería mostrar error si falla al cargar', () => {
      const error = new Error('Error al cargar');
      recintoApi.listarRecintos.and.returnValue(throwError(() => error));

      component.ngOnInit();

      expect(component.error()).toBeTruthy();
      expect(component.loading()).toBe(false);
      expect(component.recintos().length).toBe(0);
    });
  });

  describe('crear recinto', () => {
    it('debería abrir modal sin modo edición', () => {
      component.abrirFormularioCrear();

      expect(component.modalFormAbierto).toBe(true);
      expect(component.modoEdicion).toBe(false);
      expect(component.recintoSeleccionado).toBeNull();
    });
  });

  describe('editar recinto', () => {
    it('debería abrir modal con modo edición', () => {
      component.abrirFormularioEditar(mockRecintos[0]);

      expect(component.modalFormAbierto).toBe(true);
      expect(component.modoEdicion).toBe(true);
      expect(component.recintoSeleccionado).toEqual(mockRecintos[0]);
    });
  });

  describe('eliminar recinto', () => {
    it('debería confirmar antes de eliminar', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      const eliminarSpy = spyOn(component, 'eliminarRecinto');

      component.confirmarEliminar(mockRecintos[0]);

      expect(eliminarSpy).not.toHaveBeenCalled();
    });

    it('debería eliminar recinto si se confirma', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      recintoApi.eliminarRecinto.and.returnValue(of(undefined));
      const response: PagedResponse<RecintoResponse> = {
        data: [mockRecintos[1]],
        meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      };
      recintoApi.listarRecintos.and.returnValue(of(response));

      component.confirmarEliminar(mockRecintos[0]);

      expect(recintoApi.eliminarRecinto).toHaveBeenCalledWith(1);
    });

    it('debería mostrar error si falla eliminación', () => {
      const error = new Error('Error al eliminar');
      recintoApi.eliminarRecinto.and.returnValue(throwError(() => error));

      component.eliminarRecinto(1);

      expect(component.error()).toBeTruthy();
      expect(component.submitting()).toBe(false);
    });
  });

  describe('modal callbacks', () => {
    it('debería recargar recintos al guardar', () => {
      const response: PagedResponse<RecintoResponse> = {
        data: mockRecintos,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      recintoApi.listarRecintos.and.returnValue(of(response));

      component.onFormGuardado();

      expect(component.modalFormAbierto).toBe(false);
      expect(recintoApi.listarRecintos).toHaveBeenCalled();
    });

    it('debería cerrar modal sin recargar al cancelar', () => {
      component.onFormCancelado();

      expect(component.modalFormAbierto).toBe(false);
      expect(recintoApi.listarRecintos).not.toHaveBeenCalled();
    });
  });

  describe('paginación', () => {
    it('debería cambiar página y recargar', () => {
      const response: PagedResponse<RecintoResponse> = {
        data: mockRecintos,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      recintoApi.listarRecintos.and.returnValue(of(response));

      component.onPaginationChange({ page: 1, rows: 20 });

      expect(component.page).toBe(1);
      expect(component.size).toBe(20);
      expect(recintoApi.listarRecintos).toHaveBeenCalled();
    });
  });

  describe('filtros', () => {
    it('debería filtrar por estado', () => {
      const response: PagedResponse<RecintoResponse> = {
        data: [mockRecintos[0]],
        meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      };
      recintoApi.listarRecintos.and.returnValue(of(response));

      component.estadoSeleccionado = 'ACTIVO';
      component.onEstadoChange();

      expect(component.page).toBe(0);
      expect(recintoApi.listarRecintos).toHaveBeenCalled();
    });
  });
});
