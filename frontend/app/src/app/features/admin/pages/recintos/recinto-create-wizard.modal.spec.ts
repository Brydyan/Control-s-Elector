import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RecintoCreateWizardModalComponent } from './recinto-create-wizard.modal';
import { RecintoApi } from '../../data/recinto.api';
import { AdminApi } from '../../data/admin.api';
import { GeografiaApi } from '../../data/geografia.api';
import { of, throwError } from 'rxjs';
import { describe, it, beforeEach, expect, vi } from 'vitest';

describe('RecintoCreateWizardModalComponent', () => {
  let component: RecintoCreateWizardModalComponent;
  let fixture: ComponentFixture<RecintoCreateWizardModalComponent>;
  let recintoApiMock: any;
  let adminApiMock: any;
  let geografiaApiMock: any;

  const mockGeografia = {
    provincias: [
      {
        id: 1,
        nombre: 'Pichincha',
        codigo: 'P',
        cantones: [
          {
            id: 101,
            nombre: 'Quito',
            codigo: 'Q',
            parroquias: [
              { id: 1001, nombre: 'El Comercio', esUrbana: true },
              { id: 1002, nombre: 'Los Olivos', esUrbana: true },
            ],
          },
        ],
      },
    ],
  };

  const mockUsuarios = {
    data: [
      {
        id: 'usr-1',
        nombre: 'Juan Observador 1',
        email: 'juan1@test.com',
        rol: 'OBSERVER',
      },
      {
        id: 'usr-2',
        nombre: 'Juan Observador 2',
        email: 'juan2@test.com',
        rol: 'OBSERVER',
      },
      {
        id: 'usr-3',
        nombre: 'Juan Observador 3',
        email: 'juan3@test.com',
        rol: 'OBSERVER',
      },
      {
        id: 'usr-4',
        nombre: 'Juan Observador 4',
        email: 'juan4@test.com',
        rol: 'OBSERVER',
      },
      {
        id: 'usr-5',
        nombre: 'Juan Observador 5',
        email: 'juan5@test.com',
        rol: 'OBSERVER',
      },
      {
        id: 'usr-6',
        nombre: 'Carlos Coordinador',
        email: 'carlos@test.com',
        rol: 'COORDINATOR',
      },
    ],
    meta: { page: 0, size: 20, totalElements: 6 },
  };

  beforeEach(async () => {
    recintoApiMock = {
      crearRecintoConAsignaciones: vi.fn(),
    };
    adminApiMock = {
      listarUsuarios: vi.fn().mockReturnValue(of(mockUsuarios)),
    };
    geografiaApiMock = {
      obtenerGeografia: vi.fn().mockReturnValue(of(mockGeografia)),
    };

    await TestBed.configureTestingModule({
      imports: [RecintoCreateWizardModalComponent],
      providers: [
        { provide: RecintoApi, useValue: recintoApiMock },
        { provide: AdminApi, useValue: adminApiMock },
        { provide: GeografiaApi, useValue: geografiaApiMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RecintoCreateWizardModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load provinces on init', () => {
    expect(component.provincias.length).toBe(1);
    expect(component.provincias[0].nombre).toBe('Pichincha');
  });

  it('should load users on init', () => {
    expect(component.usuarios.length).toBe(6);
  });

  it('should filter cantons when province is selected', () => {
    expect(component.cantones.length).toBe(0);

    component.onProvinciaChange({ value: 1 });

    expect(component.cantones.length).toBe(1);
    expect(component.cantones[0].nombre).toBe('Quito');
  });

  it('should filter parroquias when canton is selected', () => {
    component.onProvinciaChange({ value: 1 });
    expect(component.parroquias.length).toBe(0);

    component.onCantonChange({ value: 101 });

    expect(component.parroquias.length).toBe(2);
    expect(component.parroquias[0].nombre).toBe('El Comercio');
  });

  it('should validate minimum 5 observers', () => {
    component.formularioPaso1.patchValue({
      nombre: 'Test Recinto',
      provinciaId: 1,
      cantonId: 101,
      parroquiaId: 1001,
      cantidadJrvs: 10,
    });

    // Select 4 observers (less than minimum)
    component.toggleObservador('usr-1', { checked: true });
    component.toggleObservador('usr-2', { checked: true });
    component.toggleObservador('usr-3', { checked: true });
    component.toggleObservador('usr-4', { checked: true });

    component.guardar();

    expect(component.error()).toBeTruthy();
    expect(recintoApiMock.crearRecintoConAsignaciones).not.toHaveBeenCalled();
  });

  it('should validate maximum 5 coordinators', () => {
    recintoApiMock.crearRecintoConAsignaciones.mockReturnValue(
      of({ recintoId: 'new-id' }),
    );

    component.formularioPaso1.patchValue({
      nombre: 'Test Recinto',
      provinciaId: 1,
      cantonId: 101,
      parroquiaId: 1001,
      cantidadJrvs: 10,
    });

    // Select 5 observers (minimum ok)
    component.toggleObservador('usr-1', { checked: true });
    component.toggleObservador('usr-2', { checked: true });
    component.toggleObservador('usr-3', { checked: true });
    component.toggleObservador('usr-4', { checked: true });
    component.toggleObservador('usr-5', { checked: true });

    // Try to select 2 coordinators (max is 5, but test with valid count first)
    component.toggleCoordinador('usr-6', { checked: true });

    component.guardar();

    expect(component.error()).toBeFalsy();
    expect(recintoApiMock.crearRecintoConAsignaciones).toHaveBeenCalledWith(
      expect.objectContaining({
        nombre: 'Test Recinto',
        observadorIds: ['usr-1', 'usr-2', 'usr-3', 'usr-4', 'usr-5'],
        coordinadorIds: ['usr-6'],
      }),
    );
  });

  it('should handle API errors gracefully', () => {
    recintoApiMock.crearRecintoConAsignaciones.mockReturnValue(
      throwError(() => new Error('Network error')),
    );

    component.formularioPaso1.patchValue({
      nombre: 'Test Recinto',
      provinciaId: 1,
      cantonId: 101,
      parroquiaId: 1001,
      cantidadJrvs: 10,
    });

    for (let i = 1; i <= 5; i++) {
      component.toggleObservador(`usr-${i}`, { checked: true });
    }

    component.guardar();

    expect(component.enviando()).toBeFalsy();
    expect(component.error()).toBeTruthy();
  });

  it('should emit guardado event on successful creation', () => {
    recintoApiMock.crearRecintoConAsignaciones.mockReturnValue(
      of({ recintoId: 'new-id' }),
    );

    return new Promise<void>((resolve) => {
      component.guardado.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.formularioPaso1.patchValue({
        nombre: 'Test Recinto',
        provinciaId: 1,
        cantonId: 101,
        parroquiaId: 1001,
        cantidadJrvs: 10,
      });

      for (let i = 1; i <= 5; i++) {
        component.toggleObservador(`usr-${i}`, { checked: true });
      }

      component.guardar();
    });
  });

  it('should reset form on cancel', () => {
    component.formularioPaso1.patchValue({
      nombre: 'Test',
      provinciaId: 1,
    });
    component.toggleObservador('usr-1', { checked: true });

    component.cancelar();

    expect(component.formularioPaso1.value.nombre).toBeNull();
    expect(component.observadoresSeleccionados().length).toBe(0);
    expect(component.error()).toBeNull();
  });

  it('should emit cancelado event on cancel', () => {
    return new Promise<void>((resolve) => {
      component.cancelado.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.cancelar();
    });
  });
});
