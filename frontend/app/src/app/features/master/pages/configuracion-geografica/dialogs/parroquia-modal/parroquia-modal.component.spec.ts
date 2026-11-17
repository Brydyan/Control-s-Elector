import { TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastService } from '../../../../../../core/services/toast.service';
import { GeografiaApi } from '../../../../data/geografia.api';
import { ParroquiaModalComponent } from './parroquia-modal.component';

/**
 * El modal de parroquia: alta, edicion y el interruptor de "urbana".
 */
describe('ParroquiaModalComponent', () => {
  let component: ParroquiaModalComponent;
  let api: {
    crearParroquia: ReturnType<typeof vi.fn>;
    actualizarParroquia: ReturnType<typeof vi.fn>;
  };
  let close: ReturnType<typeof vi.fn>;
  let toast: { success: ReturnType<typeof vi.fn> };

  const configurar = (data: unknown) => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ParroquiaModalComponent],
      providers: [
        { provide: GeografiaApi, useValue: api },
        { provide: DynamicDialogRef, useValue: { close } },
        { provide: DynamicDialogConfig, useValue: { data } },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(ParroquiaModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(() => {
    api = {
      crearParroquia: vi
        .fn()
        .mockReturnValue(
          of({ id: 101, nombre: 'Ballenita', esUrbana: false, cantonId: 10 }),
        ),
      actualizarParroquia: vi
        .fn()
        .mockReturnValue(
          of({ id: 100, nombre: 'Santa Elena', esUrbana: true, cantonId: 10 }),
        ),
    };
    close = vi.fn();
    toast = { success: vi.fn() };

    configurar({ mode: 'create', cantonId: 10, cantonNombre: 'Santa Elena' });
  });

  it('el submit llama al POST de parroquia con el canton del nodo', () => {
    component.form.setValue({ nombre: 'Ballenita', esUrbana: false });
    component.onSubmit();

    expect(api.crearParroquia).toHaveBeenCalledTimes(1);
    expect(api.crearParroquia).toHaveBeenCalledWith({
      nombre: 'Ballenita',
      esUrbana: false,
      cantonId: 10,
    });
    expect(toast.success).toHaveBeenCalledWith('Parroquia creada', 'Ballenita');
    expect(close).toHaveBeenCalled();
  });

  it('no llama al backend si el nombre esta vacio', () => {
    component.form.setValue({ nombre: '', esUrbana: true });
    component.onSubmit();

    expect(api.crearParroquia).not.toHaveBeenCalled();
    expect(component.form.get('nombre')?.invalid).toBe(true);
  });

  it('la edicion llama al PUT con el id de la parroquia y su canton', () => {
    configurar({
      mode: 'edit',
      cantonId: 10,
      cantonNombre: 'Santa Elena',
      parroquia: {
        id: 100,
        nombre: 'Santa Elena',
        esUrbana: true,
        jrvs: [],
      },
    });

    component.form.setValue({ nombre: 'Santa Elena', esUrbana: true });
    component.onSubmit();

    expect(api.actualizarParroquia).toHaveBeenCalledTimes(1);
    expect(api.actualizarParroquia).toHaveBeenCalledWith(100, {
      nombre: 'Santa Elena',
      esUrbana: true,
      cantonId: 10,
    });
  });

  it('el 409 por nombre repetido en el canton se muestra en el modal', () => {
    api.crearParroquia.mockReturnValue(throwError(() => ({ status: 409 })));
    component.form.setValue({ nombre: 'Santa Elena', esUrbana: true });
    component.onSubmit();

    expect(component.error).toContain('Ya existe una parroquia');
    expect(toast.success).not.toHaveBeenCalled();
  });
});
