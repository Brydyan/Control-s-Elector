import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RecintoEditWizardModalComponent } from './recinto-edit-wizard.modal';
import { RecintoApi } from '../../data/recinto.api';
import { AdminApi } from '../../data/admin.api';
import { GeografiaApi } from '../../data/geografia.api';
import { of } from 'rxjs';

describe('RecintoEditWizardModalComponent', () => {
  let component: RecintoEditWizardModalComponent;
  let fixture: ComponentFixture<RecintoEditWizardModalComponent>;
  let recintoApiMock: jasmine.SpyObj<RecintoApi>;
  let adminApiMock: jasmine.SpyObj<AdminApi>;
  let geografiaApiMock: jasmine.SpyObj<GeografiaApi>;

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

  const mockRecintoData = {
    id: 'recinto-123',
    nombre: 'Escuela Pichincha',
    parroquiaId: '1001',
    parroquiaNombre: 'El Comercio',
    cantidadJrvs: 15,
    cantidadJrvsActuales: 12,
    estado: 'ACTIVO',
    observadorIds: ['usr-1', 'usr-2', 'usr-3', 'usr-4', 'usr-5'],
    coordinadorIds: ['usr-6'],
    fechaCreacion: '2024-01-15T10:00:00',
    fechaActualizacion: '2024-01-15T10:00:00',
  };

  beforeEach(async () => {
    recintoApiMock = jasmine.createSpyObj('RecintoApi', [
      'editarRecintoConAsignaciones',
      'obtenerRecintoConAsignaciones',
    ]);
    adminApiMock = jasmine.createSpyObj('AdminApi', ['listarUsuarios']);
    geografiaApiMock = jasmine.createSpyObj('GeografiaApi', [
      'obtenerGeografia',
    ]);

    adminApiMock.listarUsuarios.and.returnValue(of(mockUsuarios));
    geografiaApiMock.obtenerGeografia.and.returnValue(of(mockGeografia));
    recintoApiMock.obtenerRecintoConAsignaciones.and.returnValue(
      of(mockRecintoData),
    );

    await TestBed.configureTestingModule({
      imports: [RecintoEditWizardModalComponent],
      providers: [
        { provide: RecintoApi, useValue: recintoApiMock },
        { provide: AdminApi, useValue: adminApiMock },
        { provide: GeografiaApi, useValue: geografiaApiMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RecintoEditWizardModalComponent);
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

  it('should validate minimum 5 observers on update', () => {
    component.formularioPaso1.patchValue({
      nombre: 'Updated Recinto',
      cantidadJrvs: 10,
    });

    // Set only 3 observers
    component.observadoresSeleccionados.set(['usr-1', 'usr-2', 'usr-3']);

    component.guardar();

    expect(component.error()).toBeTruthy();
    expect(recintoApiMock.editarRecintoConAsignaciones).not.toHaveBeenCalled();
  });

  it('should handle successful update', () => {
    recintoApiMock.editarRecintoConAsignaciones.and.returnValue(of(void 0));

    return new Promise<void>((resolve) => {
      component.abierto = true;
      component.recintoId = 'recinto-123';
      component.formularioPaso1.patchValue({
        nombre: 'Updated Recinto',
        cantidadJrvs: 20,
      });

      component.observadoresSeleccionados.set([
        'usr-1',
        'usr-2',
        'usr-3',
        'usr-4',
        'usr-5',
      ]);
      component.coordinadoresSeleccionados.set(['usr-6']);

      component.guardado.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.guardar();
    });
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

  it('should handle geography filtering', () => {
    component.onProvinciaChange({ value: 1 });

    expect(component.cantones.length).toBe(1);
    expect(component.cantones[0].nombre).toBe('Quito');

    component.onCantonChange({ value: 101 });

    expect(component.parroquias.length).toBe(2);
    expect(component.parroquias[0].nombre).toBe('El Comercio');
  });
});
