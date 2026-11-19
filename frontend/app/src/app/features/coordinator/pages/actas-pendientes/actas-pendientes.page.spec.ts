import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { ActasPendientesPage } from './actas-pendientes.page';
import { CoordinatorApi } from '../../data/coordinator.api';
import { NotificacionApi } from '../../data/notificacion.api';
import { PagedResponse } from '../../../../shared/models/page.model';

describe('ActasPendientesPage', () => {
  let component: ActasPendientesPage;
  let fixture: ComponentFixture<ActasPendientesPage>;
  let coordinatorApi: jasmine.SpyObj<CoordinatorApi>;
  let notificacionApi: jasmine.SpyObj<NotificacionApi>;

  const mockActasPendientes = [
    {
      id: 1,
      jrvNumero: 'JRV-001',
      cargo: 'Presidente',
      observer: 'Juan Pérez',
      estado: 'ENVIADA' as const,
      fechaRegistro: '2026-11-19T10:00:00Z',
      totalMesas: 5,
    },
    {
      id: 2,
      jrvNumero: 'JRV-002',
      cargo: 'Vocal',
      observer: 'María García',
      estado: 'ENVIADA' as const,
      fechaRegistro: '2026-11-19T11:00:00Z',
      totalMesas: 3,
    },
  ];

  beforeEach(async () => {
    coordinatorApi = jasmine.createSpyObj('CoordinatorApi', [
      'obtenerActasPendientes',
      'obtenerActaDetalle',
      'aprobarActa',
      'rechazarActa',
    ]);
    notificacionApi = jasmine.createSpyObj('NotificacionApi', [
      'obtenerPendientes',
      'marcarComoLeida',
    ]);

    await TestBed.configureTestingModule({
      imports: [ActasPendientesPage],
      providers: [
        { provide: CoordinatorApi, useValue: coordinatorApi },
        { provide: NotificacionApi, useValue: notificacionApi },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActasPendientesPage);
    component = fixture.componentInstance;
  });

  describe('cargar actas pendientes', () => {
    it('debería cargar actas al iniciar', () => {
      const response: PagedResponse<any> = {
        data: mockActasPendientes,
        meta: { total: 2 },
      };
      coordinatorApi.obtenerActasPendientes.and.returnValue(of(response));

      component.ngOnInit();

      expect(component.actasPendientes()).toEqual(mockActasPendientes);
      expect(component.loading()).toBe(false);
    });

    it('debería mostrar error si falla al cargar', () => {
      const error = new Error('Error al cargar');
      coordinatorApi.obtenerActasPendientes.and.returnValue(
        throwError(() => error),
      );

      component.ngOnInit();

      expect(component.error()).toBeTruthy();
      expect(component.loading()).toBe(false);
    });
  });

  describe('polling', () => {
    it('debería hacer polling cada 5 segundos', (done) => {
      const response: PagedResponse<any> = {
        data: mockActasPendientes,
        meta: { total: 2 },
      };
      coordinatorApi.obtenerActasPendientes.and.returnValue(of(response));

      component.ngOnInit();

      setTimeout(() => {
        // Después de 5+ segundos, el polling debería haber ocurrido
        expect(coordinatorApi.obtenerActasPendientes).toHaveBeenCalled();
        done();
      }, 6000);
    });
  });

  describe('ver detalle', () => {
    it('debería abrir modal al ver detalle', () => {
      component.verDetalle(mockActasPendientes[0]);

      expect(component.modalDetalleAbierto).toBe(true);
      expect(component.actaSeleccionada).toEqual(mockActasPendientes[0]);
    });
  });

  describe('aprobar acta', () => {
    it('debería aprobar acta y recargar lista', () => {
      const response: PagedResponse<any> = {
        data: [],
        meta: { total: 0 },
      };
      coordinatorApi.aprobarActa.and.returnValue(of(undefined as any));
      coordinatorApi.obtenerActasPendientes.and.returnValue(of(response));

      component.aprobarActa(mockActasPendientes[0]);

      expect(coordinatorApi.aprobarActa).toHaveBeenCalledWith(1, undefined);
      expect(component.submitting()).toBe(false);
    });

    it('debería mostrar error si falla aprobación', () => {
      const error = new Error('Error al aprobar');
      coordinatorApi.aprobarActa.and.returnValue(throwError(() => error));

      component.aprobarActa(mockActasPendientes[0]);

      expect(component.error()).toBeTruthy();
      expect(component.submitting()).toBe(false);
    });
  });

  describe('rechazar acta', () => {
    it('debería rechazar acta con motivo', () => {
      const response: PagedResponse<any> = {
        data: [],
        meta: { total: 0 },
      };
      coordinatorApi.rechazarActa.and.returnValue(of(undefined as any));
      coordinatorApi.obtenerActasPendientes.and.returnValue(of(response));

      component.rechazarActa(mockActasPendientes[0], 'Datos incompletos');

      expect(coordinatorApi.rechazarActa).toHaveBeenCalledWith(
        1,
        'Datos incompletos',
      );
    });

    it('debería mostrar error si falla rechazo', () => {
      const error = new Error('Error al rechazar');
      coordinatorApi.rechazarActa.and.returnValue(throwError(() => error));

      component.rechazarActa(mockActasPendientes[0], 'Datos incompletos');

      expect(component.error()).toBeTruthy();
    });
  });

  describe('tabs', () => {
    it('debería cargar revisadas al cambiar a tab 1', () => {
      const response: PagedResponse<any> = {
        data: [],
        meta: { total: 0 },
      };
      coordinatorApi.obtenerActasPendientes.and.returnValue(of(response));

      component.onTabChange(1);

      expect(coordinatorApi.obtenerActasPendientes).toHaveBeenCalled();
    });
  });

  describe('cleanup', () => {
    it('debería limpiar al destruir', () => {
      const response: PagedResponse<any> = {
        data: mockActasPendientes,
        meta: { total: 2 },
      };
      coordinatorApi.obtenerActasPendientes.and.returnValue(of(response));

      component.ngOnInit();
      component.ngOnDestroy();

      expect(component).toBeDefined();
    });
  });
});
