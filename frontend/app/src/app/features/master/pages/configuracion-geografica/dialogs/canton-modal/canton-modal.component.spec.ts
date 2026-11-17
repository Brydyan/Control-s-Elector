import { TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastService } from '../../../../../../core/services/toast.service';
import { GeografiaApi } from '../../../../data/geografia.api';
import { CantonModalComponent } from './canton-modal.component';

/**
 * El modal de canton: alta, edicion y validacion.
 *
 * <p>El canton cuelga de una provincia: el modal la recibe del nodo del arbol del que salio y
 * la manda en el payload, porque el backend la exige.
 */
describe('CantonModalComponent', () => {
  let component: CantonModalComponent;
  let api: {
    crearCanton: ReturnType<typeof vi.fn>;
    actualizarCanton: ReturnType<typeof vi.fn>;
  };
  let close: ReturnType<typeof vi.fn>;
  let toast: { success: ReturnType<typeof vi.fn> };

  const configurar = (data: unknown) => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CantonModalComponent],
      providers: [
        { provide: GeografiaApi, useValue: api },
        { provide: DynamicDialogRef, useValue: { close } },
        { provide: DynamicDialogConfig, useValue: { data } },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(CantonModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(() => {
    api = {
      crearCanton: vi.fn().mockReturnValue(
        of({
          id: 11,
          nombre: 'Nuevo Canton',
          codigo: '2403',
          provinciaId: 1,
        }),
      ),
      actualizarCanton: vi.fn().mockReturnValue(
        of({
          id: 10,
          nombre: 'Canton editado',
          codigo: '2404',
          provinciaId: 1,
        }),
      ),
    };
    close = vi.fn();
    toast = { success: vi.fn() };

    configurar({
      mode: 'create',
      provinciaId: 1,
      provinciaNombre: 'Santa Elena',
    });
  });

  it('el submit llama al POST de canton con la provincia del nodo', () => {
    component.form.setValue({ nombre: 'Nuevo Canton', codigo: '2403' });
    component.onSubmit();

    expect(api.crearCanton).toHaveBeenCalledTimes(1);
    expect(api.crearCanton).toHaveBeenCalledWith({
      nombre: 'Nuevo Canton',
      codigo: '2403',
      provinciaId: 1,
    });
    expect(toast.success).toHaveBeenCalledWith('Canton creado', 'Nuevo Canton');
    expect(close).toHaveBeenCalled();
  });

  it('no llama al backend si el nombre esta vacio', () => {
    component.form.setValue({ nombre: '   ', codigo: '2403' });
    component.onSubmit();

    expect(api.crearCanton).not.toHaveBeenCalled();
    expect(component.form.get('nombre')?.invalid).toBe(true);
  });

  it('no llama al backend si el codigo esta vacio', () => {
    component.form.setValue({ nombre: 'Nuevo Canton', codigo: '' });
    component.onSubmit();

    expect(api.crearCanton).not.toHaveBeenCalled();
    expect(component.form.get('codigo')?.invalid).toBe(true);
  });

  it('la edicion llama al PUT con el id del canton', () => {
    configurar({
      mode: 'edit',
      provinciaId: 1,
      provinciaNombre: 'Santa Elena',
      canton: { id: 10, nombre: 'Santa Elena', codigo: '2401', parroquias: [] },
    });

    component.form.setValue({ nombre: 'Canton editado', codigo: '2404' });
    component.onSubmit();

    expect(api.actualizarCanton).toHaveBeenCalledTimes(1);
    expect(api.actualizarCanton).toHaveBeenCalledWith(10, {
      nombre: 'Canton editado',
      codigo: '2404',
      provinciaId: 1,
    });
    expect(toast.success).toHaveBeenCalledWith(
      'Canton actualizado',
      'Canton editado',
    );
  });

  it('el 409 se muestra en el modal y no se duplica en un toast', () => {
    api.crearCanton.mockReturnValue(throwError(() => ({ status: 409 })));
    component.form.setValue({ nombre: 'Nuevo Canton', codigo: '2403' });
    component.onSubmit();

    expect(component.error).toContain('Ya existe un canton');
    expect(toast.success).not.toHaveBeenCalled();
  });
});
