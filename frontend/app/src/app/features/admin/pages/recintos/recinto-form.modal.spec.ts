import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { RecintoFormModalComponent } from './recinto-form.modal';
import { RecintoApi, RecintoResponse } from '../../data/recinto.api';
import { AdminApi } from '../../data/admin.api';
import { GeografiaApi, GeografiaTreeDto } from '../../data/geografia.api';
import { PagedResponse } from '../../../../shared/models/page.model';
import { UsuarioResponseDto } from '../../domain/models/admin.model';

describe('RecintoFormModalComponent', () => {
  let component: RecintoFormModalComponent;
  let fixture: ComponentFixture<RecintoFormModalComponent>;
  let recintoApi: jasmine.SpyObj<RecintoApi>;
  let adminApi: jasmine.SpyObj<AdminApi>;
  let geografiaApi: jasmine.SpyObj<GeografiaApi>;

  const mockRecinto: RecintoResponse = {
    id: 1,
    nombre: 'Escuela República',
    parroquiaId: 1,
    parroquia: 'Centro, Pichincha',
    coordinadorId: 10,
    coordinador: { id: 10, nombre: 'Juan Pérez', email: 'juan@test.com' },
    totalMesas: 5,
    estado: 'ACTIVO',
    fechaCreacion: '2026-11-01T10:00:00Z',
  };

  const mockCoordinadores: UsuarioResponseDto[] = [
    {
      id: 10,
      nombre: 'Juan Pérez',
      email: 'juan@test.com',
      rolNombre: 'COORDINATOR',
    } as UsuarioResponseDto,
    {
      id: 11,
      nombre: 'María García',
      email: 'maria@test.com',
      rolNombre: 'COORDINATOR',
    } as UsuarioResponseDto,
  ];

  beforeEach(async () => {
    recintoApi = jasmine.createSpyObj('RecintoApi', [
      'crearRecinto',
      'actualizarRecinto',
    ]);
    adminApi = jasmine.createSpyObj('AdminApi', ['listarUsuarios']);
    geografiaApi = jasmine.createSpyObj('GeografiaApi', ['obtenerGeografia']);
    geografiaApi.obtenerGeografia.and.returnValue(
      of({
        provincias: [
          {
            id: 1,
            nombre: 'Santa Elena',
            cantones: [
              {
                id: 10,
                nombre: 'La Libertad',
                parroquias: [
                  { id: 100, nombre: 'Centro', esUrbana: true, jrvs: [] },
                ],
              },
            ],
          },
        ],
      } as GeografiaTreeDto),
    );

    await TestBed.configureTestingModule({
      imports: [RecintoFormModalComponent, ReactiveFormsModule],
      providers: [
        { provide: RecintoApi, useValue: recintoApi },
        { provide: AdminApi, useValue: adminApi },
        { provide: GeografiaApi, useValue: geografiaApi },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RecintoFormModalComponent);
    component = fixture.componentInstance;
  });

  describe('inicialización', () => {
    it('debería crear formulario con validaciones', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));
      component.ngOnInit();

      expect(component.formulario).toBeDefined();
      expect(component.formulario.get('nombre')).toBeDefined();
      expect(component.formulario.get('parroquiaId')).toBeDefined();
    });

    it('debería cargar coordinadores', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));

      component.ngOnInit();

      expect(component.coordinadores.length).toBe(2);
      expect(component.coordinadores[0].rolNombre).toBe('COORDINATOR');
    });

    it('debería cargar y filtrar provincias, cantones y parroquias desde la API', () => {
      adminApi.listarUsuarios.and.returnValue(
        of({
          data: mockCoordinadores,
          meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
        }),
      );

      component.ngOnInit();

      expect(geografiaApi.obtenerGeografia).toHaveBeenCalled();
      expect(component.provincias.map((p) => p.nombre)).toEqual([
        'Santa Elena',
      ]);
      component.onProvinciaChange({ value: 1 });
      expect(component.cantones.map((c) => c.nombre)).toEqual(['La Libertad']);
      component.onCantonChange({ value: 10 });
      expect(component.parroquias.map((p) => p.nombre)).toEqual(['Centro']);
    });
  });

  describe('crear recinto', () => {
    it('debería crear recinto con formulario válido', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));
      const newRecinto: RecintoResponse = {
        ...mockRecinto,
        id: 2,
        nombre: 'Nueva Escuela',
      };
      recintoApi.crearRecinto.and.returnValue(of(newRecinto));

      component.ngOnInit();
      component.modoEdicion = false;
      component.formulario.patchValue({
        provinciaId: 1,
        cantonId: 1,
        parroquiaId: 1,
        nombre: 'Nueva Escuela',
        coordinadorId: 10,
        estado: 'ACTIVO',
      });

      component.guardar();

      expect(recintoApi.crearRecinto).toHaveBeenCalled();
      expect(component.guardado.emit).toBeDefined();
    });

    it('debería mostrar error si formulario inválido', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));
      component.ngOnInit();
      component.formulario.patchValue({ nombre: '' });

      component.guardar();

      expect(component.error()).toBeTruthy();
      expect(recintoApi.crearRecinto).not.toHaveBeenCalled();
    });

    it('debería mostrar error si API falla', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));
      const error = new Error('Error al crear');
      recintoApi.crearRecinto.and.returnValue(throwError(() => error));

      component.ngOnInit();
      component.modoEdicion = false;
      component.formulario.patchValue({
        provinciaId: 1,
        cantonId: 1,
        parroquiaId: 1,
        nombre: 'Nueva Escuela',
        coordinadorId: 10,
        estado: 'ACTIVO',
      });

      component.guardar();

      expect(component.error()).toBeTruthy();
      expect(component.enviando()).toBe(false);
    });
  });

  describe('editar recinto', () => {
    it('debería actualizar recinto en modo edición', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));
      recintoApi.actualizarRecinto.and.returnValue(of(mockRecinto));

      component.ngOnInit();
      component.modoEdicion = true;
      component.recinto = mockRecinto;
      component.formulario.patchValue({
        provinciaId: 1,
        cantonId: 1,
        parroquiaId: 1,
        nombre: 'Escuela Actualizada',
        coordinadorId: 10,
        estado: 'ACTIVO',
      });

      component.guardar();

      expect(recintoApi.actualizarRecinto).toHaveBeenCalled();
    });
  });

  describe('cascada geográfica', () => {
    it('debería limpiar cantones al cambiar provincia', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));
      component.ngOnInit();
      component.cantones = [{ id: 1, nombre: 'Cantón 1', provinciaId: 1 }];

      component.onProvinciaChange({ value: 2 });

      expect(component.cantones.length).toBe(0);
      expect(component.formulario.get('cantonId')?.value).toBeNull();
    });

    it('debería limpiar parroquias al cambiar cantón', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));
      component.ngOnInit();
      component.parroquias = [{ id: 1, nombre: 'Parroquia 1', cantonId: 1 }];

      component.onCantonChange({ value: 2 });

      expect(component.parroquias.length).toBe(0);
      expect(component.formulario.get('parroquiaId')?.value).toBeNull();
    });
  });

  describe('cancelar', () => {
    it('debería reset formulario y emitir evento', () => {
      const response: PagedResponse<UsuarioResponseDto> = {
        data: mockCoordinadores,
        meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      };
      adminApi.listarUsuarios.and.returnValue(of(response));
      component.ngOnInit();
      const emitSpy = vi.spyOn(component.cancelado, 'emit');

      component.formulario.patchValue({ nombre: 'Test' });
      component.error.set('Some error');
      component.cancelar();

      expect(component.error()).toBeNull();
      expect(emitSpy).toHaveBeenCalled();
    });
  });
});
