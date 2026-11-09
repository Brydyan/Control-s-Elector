import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastService } from '../../../../../../core/services/toast.service';
import { ConfiguracionApi } from '../../../../data/configuracion.api';
import { CargoModalComponent } from './cargo-modal.component';

/**
 * El modal de cargo con el toast (ODD 505, A.4).
 *
 * <p>El exito se avisa con un toast: el modal se cierra y el operador se queda
 * sin la confirmacion de que su accion salio. El error NO se duplica en un
 * toast: el modal ya lo muestra con su motivo.
 */
describe('CargoModalComponent — toast (ODD 505, A.4)', () => {
  let fixture: ComponentFixture<CargoModalComponent>;
  let component: CargoModalComponent;
  let toast: { success: ReturnType<typeof vi.fn> };
  let api: {
    createCargo: ReturnType<typeof vi.fn>;
    updateCargo: ReturnType<typeof vi.fn>;
  };
  let close: ReturnType<typeof vi.fn>;

  const llenarFormulario = () => {
    component.form.setValue({
      nombre: 'Presidente',
      nivelTerritorial: 'NACIONAL',
      orden: 1,
      estado: 'ACTIVO',
    });
  };

  beforeEach(async () => {
    toast = { success: vi.fn() };
    close = vi.fn();
    api = {
      createCargo: vi.fn().mockReturnValue(
        of({
          id: 1,
          nombre: 'Presidente',
          nivelTerritorial: 'NACIONAL',
          orden: 1,
          estado: 'ACTIVO',
        }),
      ),
      updateCargo: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [CargoModalComponent],
      providers: [
        { provide: ConfiguracionApi, useValue: api },
        { provide: DynamicDialogRef, useValue: { close } },
        {
          provide: DynamicDialogConfig,
          useValue: { data: { mode: 'create' } },
        },
        { provide: ToastService, useValue: toast },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CargoModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('avisa con un toast cuando el cargo se crea', () => {
    llenarFormulario();
    component.onSubmit();

    expect(api.createCargo).toHaveBeenCalledTimes(1);
    expect(toast.success).toHaveBeenCalledWith('Cargo creado', 'Presidente');
    expect(close).toHaveBeenCalled();
  });

  it('el error se muestra en el modal y no se duplica en un toast', () => {
    api.createCargo.mockReturnValue(throwError(() => ({ status: 409 })));
    llenarFormulario();
    component.onSubmit();

    expect(component.error).toContain('ya existe');
    expect(toast.success).not.toHaveBeenCalled();
  });
});
