import { TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastService } from '../../../../../../core/services/toast.service';
import { GeografiaApi } from '../../../../data/geografia.api';
import { JrvModalComponent } from './jrv-modal.component';

/**
 * El modal de JRV: alta, edicion y validacion.
 *
 * <p>El numero de JRV es unico en el sistema y el total de electores tiene que ser mayor que
 * cero: el modal avisa del 409 y del 400 sin salir del formulario.
 */
describe('JrvModalComponent', () => {
  let component: JrvModalComponent;
  let api: {
    crearJrv: ReturnType<typeof vi.fn>;
    actualizarJrv: ReturnType<typeof vi.fn>;
  };
  let close: ReturnType<typeof vi.fn>;
  let toast: { success: ReturnType<typeof vi.fn> };

  const configurar = (data: unknown) => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [JrvModalComponent],
      providers: [
        { provide: GeografiaApi, useValue: api },
        { provide: DynamicDialogRef, useValue: { close } },
        { provide: DynamicDialogConfig, useValue: { data } },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(JrvModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(() => {
    api = {
      crearJrv: vi.fn().mockReturnValue(
        of({
          id: 1003,
          numero: 'JRV-NEW-001',
          recinto: 'Escuela Nueva',
          totalElectores: 250,
          parroquiaId: 100,
        }),
      ),
      actualizarJrv: vi.fn().mockReturnValue(
        of({
          id: 1001,
          numero: 'JRV-UPD-001',
          recinto: 'Recinto Actualizado',
          totalElectores: 300,
          parroquiaId: 100,
        }),
      ),
    };
    close = vi.fn();
    toast = { success: vi.fn() };

    configurar({
      mode: 'create',
      parroquiaId: 100,
      parroquiaNombre: 'Santa Elena',
    });
  });

  it('el submit llama al POST de JRV con la parroquia del nodo', () => {
    component.form.setValue({
      numero: 'JRV-NEW-001',
      recinto: 'Escuela Nueva',
      totalElectores: 250,
    });
    component.onSubmit();

    expect(api.crearJrv).toHaveBeenCalledTimes(1);
    expect(api.crearJrv).toHaveBeenCalledWith({
      numero: 'JRV-NEW-001',
      recinto: 'Escuela Nueva',
      totalElectores: 250,
      parroquiaId: 100,
    });
    expect(toast.success).toHaveBeenCalledWith('JRV creada', 'JRV-NEW-001');
    expect(close).toHaveBeenCalled();
  });

  it('la edicion llama al PUT con el id de la JRV', () => {
    configurar({
      mode: 'edit',
      parroquiaId: 100,
      parroquiaNombre: 'Santa Elena',
      jrv: {
        id: 1001,
        numero: 'JRV-SE-001',
        recinto: 'Escuela Abdón Calderón',
        totalElectores: 320,
      },
    });

    component.form.setValue({
      numero: 'JRV-UPD-001',
      recinto: 'Recinto Actualizado',
      totalElectores: 300,
    });
    component.onSubmit();

    expect(api.actualizarJrv).toHaveBeenCalledTimes(1);
    expect(api.actualizarJrv).toHaveBeenCalledWith(1001, {
      numero: 'JRV-UPD-001',
      recinto: 'Recinto Actualizado',
      totalElectores: 300,
      parroquiaId: 100,
    });
    expect(toast.success).toHaveBeenCalledWith(
      'JRV actualizada',
      'JRV-UPD-001',
    );
  });

  it('no llama al backend si el total de electores es cero', () => {
    component.form.setValue({
      numero: 'JRV-NEW-001',
      recinto: 'Escuela Nueva',
      totalElectores: 0,
    });
    component.onSubmit();

    expect(api.crearJrv).not.toHaveBeenCalled();
    expect(component.form.get('totalElectores')?.invalid).toBe(true);
  });

  it('no llama al backend si el recinto esta vacio', () => {
    component.form.setValue({
      numero: 'JRV-NEW-001',
      recinto: '   ',
      totalElectores: 250,
    });
    component.onSubmit();

    expect(api.crearJrv).not.toHaveBeenCalled();
    expect(component.form.get('recinto')?.invalid).toBe(true);
  });

  it('el 409 por numero repetido se muestra en el modal', () => {
    api.crearJrv.mockReturnValue(throwError(() => ({ status: 409 })));
    component.form.setValue({
      numero: 'JRV-SE-001',
      recinto: 'Escuela Nueva',
      totalElectores: 250,
    });
    component.onSubmit();

    expect(component.error).toContain('Ya existe una JRV');
    expect(toast.success).not.toHaveBeenCalled();
  });
});
