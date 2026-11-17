import { TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastService } from '../../../../../../core/services/toast.service';
import { GeografiaApi } from '../../../../data/geografia.api';
import { ProvinciaModalComponent } from './provincia-modal.component';

/**
 * El modal de provincia: alta, edicion y validacion.
 *
 * <p>El codigo es opcional, pero cuando viene es unico en el sistema: el 409 se muestra en el
 * modal con su motivo.
 */
describe('ProvinciaModalComponent', () => {
  let component: ProvinciaModalComponent;
  let api: {
    crearProvincia: ReturnType<typeof vi.fn>;
    actualizarProvincia: ReturnType<typeof vi.fn>;
  };
  let close: ReturnType<typeof vi.fn>;
  let toast: { success: ReturnType<typeof vi.fn> };

  const configurar = (data: unknown) => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProvinciaModalComponent],
      providers: [
        { provide: GeografiaApi, useValue: api },
        { provide: DynamicDialogRef, useValue: { close } },
        { provide: DynamicDialogConfig, useValue: { data } },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(ProvinciaModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(() => {
    api = {
      crearProvincia: vi
        .fn()
        .mockReturnValue(of({ id: 2, nombre: 'Guayas', codigo: '09' })),
      actualizarProvincia: vi
        .fn()
        .mockReturnValue(of({ id: 2, nombre: 'Guayas', codigo: '09' })),
    };
    close = vi.fn();
    toast = { success: vi.fn() };

    configurar({ mode: 'create' });
  });

  it('el submit llama al POST de provincia', () => {
    component.form.setValue({ nombre: 'Guayas', codigo: '09' });
    component.onSubmit();

    expect(api.crearProvincia).toHaveBeenCalledTimes(1);
    expect(api.crearProvincia).toHaveBeenCalledWith({
      nombre: 'Guayas',
      codigo: '09',
    });
    expect(toast.success).toHaveBeenCalledWith('Provincia creada', 'Guayas');
    expect(close).toHaveBeenCalled();
  });

  it('el codigo es opcional', () => {
    component.form.setValue({ nombre: 'Guayas', codigo: '' });
    component.onSubmit();

    expect(api.crearProvincia).toHaveBeenCalledWith({
      nombre: 'Guayas',
      codigo: '',
    });
  });

  it('no llama al backend si el nombre esta vacio', () => {
    component.form.setValue({ nombre: '  ', codigo: '09' });
    component.onSubmit();

    expect(api.crearProvincia).not.toHaveBeenCalled();
    expect(component.form.get('nombre')?.invalid).toBe(true);
  });

  it('la edicion llama al PUT con el id de la provincia', () => {
    configurar({
      mode: 'edit',
      provincia: { id: 2, nombre: 'Guayas', codigo: '09', cantones: [] },
    });

    component.form.setValue({ nombre: 'Guayas', codigo: '09' });
    component.onSubmit();

    expect(api.actualizarProvincia).toHaveBeenCalledTimes(1);
    expect(api.actualizarProvincia).toHaveBeenCalledWith(2, {
      nombre: 'Guayas',
      codigo: '09',
    });
    expect(toast.success).toHaveBeenCalledWith(
      'Provincia actualizada',
      'Guayas',
    );
  });

  it('el 409 por codigo repetido se muestra en el modal', () => {
    api.crearProvincia.mockReturnValue(throwError(() => ({ status: 409 })));
    component.form.setValue({ nombre: 'Guayas', codigo: '24' });
    component.onSubmit();

    expect(component.error).toContain('Ya existe una provincia');
    expect(toast.success).not.toHaveBeenCalled();
  });
});
