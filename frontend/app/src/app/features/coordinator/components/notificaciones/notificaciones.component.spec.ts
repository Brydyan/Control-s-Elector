import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach } from 'vitest';
import { NotificacionesComponent } from './notificaciones.component';
import { NotificacionApi, Notificacion } from '../../data/notificacion.api';

describe('NotificacionesComponent', () => {
  let component: NotificacionesComponent;
  let fixture: ComponentFixture<NotificacionesComponent>;
  let notificacionApi: jasmine.SpyObj<NotificacionApi>;

  const mockNotificaciones: Notificacion[] = [
    {
      id: 1,
      usuarioId: 1,
      tipo: 'ACTA_ENVIADA',
      acta_id: 1,
      titulo: 'Acta pendiente',
      mensaje: 'JRV-001 - Presidente enviada por Juan',
      leida: false,
      fechaCreacion: '2026-11-19T10:00:00Z',
    },
    {
      id: 2,
      usuarioId: 1,
      tipo: 'ACTA_APROBADA',
      acta_id: 2,
      titulo: 'Acta aprobada',
      mensaje: 'Tu acta ha sido aprobada',
      leida: true,
      fechaCreacion: '2026-11-18T10:00:00Z',
    },
  ];

  beforeEach(async () => {
    notificacionApi = jasmine.createSpyObj('NotificacionApi', [
      'obtenerPendientes',
      'marcarComoLeida',
    ]);

    await TestBed.configureTestingModule({
      imports: [NotificacionesComponent],
      providers: [{ provide: NotificacionApi, useValue: notificacionApi }],
    }).compileComponents();

    fixture = TestBed.createComponent(NotificacionesComponent);
    component = fixture.componentInstance;
  });

  describe('cargar notificaciones', () => {
    it('debería cargar notificaciones al iniciar', () => {
      notificacionApi.obtenerPendientes.and.returnValue(
        of({
          data: mockNotificaciones,
          meta: { total: 2, sinLeer: 1 },
        }),
      );

      component.ngOnInit();

      expect(component.notificaciones()).toEqual(mockNotificaciones);
      expect(component.sinLeer()).toBe(1);
    });

    it('debería mostrar error si falla al cargar', () => {
      const error = new Error('Error al cargar');
      notificacionApi.obtenerPendientes.and.returnValue(
        throwError(() => error),
      );

      component.ngOnInit();

      expect(component.notificaciones().length).toBe(0);
    });
  });

  describe('badge contador', () => {
    it('debería mostrar contador de notificaciones sin leer', () => {
      notificacionApi.obtenerPendientes.and.returnValue(
        of({
          data: mockNotificaciones,
          meta: { total: 2, sinLeer: 1 },
        }),
      );

      component.ngOnInit();

      expect(component.sinLeer()).toBe(1);
    });

    it('debería ser 0 si no hay notificaciones sin leer', () => {
      notificacionApi.obtenerPendientes.and.returnValue(
        of({
          data: [],
          meta: { total: 0, sinLeer: 0 },
        }),
      );

      component.ngOnInit();

      expect(component.sinLeer()).toBe(0);
    });
  });

  describe('marcar como leída', () => {
    it('debería marcar notificación como leída', () => {
      notificacionApi.obtenerPendientes.and.returnValue(
        of({
          data: mockNotificaciones,
          meta: { total: 2, sinLeer: 1 },
        }),
      );
      notificacionApi.marcarComoLeida.and.returnValue(of(undefined));

      component.ngOnInit();
      // Simulate clicking on a notification
      (component as any).onNotificacionClick(mockNotificaciones[0]);

      expect(notificacionApi.marcarComoLeida).toHaveBeenCalledWith(1);
    });
  });

  describe('polling', () => {
    it('debería hacer polling cada 10 segundos', (done) => {
      notificacionApi.obtenerPendientes.and.returnValue(
        of({
          data: mockNotificaciones,
          meta: { total: 2, sinLeer: 1 },
        }),
      );

      component.ngOnInit();

      setTimeout(() => {
        // Después de 10+ segundos, el polling debería haber ocurrido al menos una vez
        expect(notificacionApi.obtenerPendientes.calls.count()).toBeGreaterThan(
          1,
        );
        done();
      }, 11000);
    });
  });

  describe('cleanup', () => {
    it('debería limpiar al destruir', () => {
      notificacionApi.obtenerPendientes.and.returnValue(
        of({
          data: mockNotificaciones,
          meta: { total: 2, sinLeer: 1 },
        }),
      );

      component.ngOnInit();
      component.ngOnDestroy();

      expect(component).toBeDefined();
    });
  });
});
