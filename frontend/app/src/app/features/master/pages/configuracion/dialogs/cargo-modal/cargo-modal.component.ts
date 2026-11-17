import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormGroup,
  FormControl,
  Validators,
} from '@angular/forms';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';

import { ConfiguracionApi } from '../../../../data/configuracion.api';
import {
  CargoDto,
  CargoRequest,
} from '../../../../domain/models/configuracion.model';
import { ToastService } from '../../../../../../core/services/toast.service';

/**
 * T-FE-16 CargoModalComponent — Modal para crear/editar cargos electorales.
 *
 * Uso (crear):
 * this.dialogService.open(CargoModalComponent, {
 *   header: 'Nuevo Cargo',
 *   data: { mode: 'create' }
 * });
 *
 * Uso (editar):
 * this.dialogService.open(CargoModalComponent, {
 *   header: 'Editar Cargo',
 *   data: { mode: 'edit', cargo: cargoDto }
 * });
 */
@Component({
  selector: 'app-cargo-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="cargo-form">
      <div class="form-group">
        <label for="nombre">Nombre *</label>
        <input
          id="nombre"
          type="text"
          formControlName="nombre"
          class="form-input"
          placeholder="ej. Presidente"
          data-testid="input-nombre"
        />
        <span
          class="error"
          *ngIf="form.get('nombre')?.invalid && form.get('nombre')?.touched"
          >Nombre es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="nivel">Nivel Territorial *</label>
        <select
          id="nivel"
          formControlName="nivelTerritorial"
          class="form-input"
          data-testid="select-nivel"
        >
          <option value="">-- Selecciona --</option>
          <option value="NACIONAL">Nacional</option>
          <option value="PROVINCIAL">Provincial</option>
          <option value="CANTONAL">Cantonal</option>
          <option value="PARROQUIAL">Parroquial</option>
        </select>
        <span
          class="error"
          *ngIf="
            form.get('nivelTerritorial')?.invalid &&
            form.get('nivelTerritorial')?.touched
          "
          >Nivel es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="orden">Orden *</label>
        <input
          id="orden"
          type="number"
          formControlName="orden"
          class="form-input"
          placeholder="1"
          data-testid="input-orden"
        />
        <span
          class="error"
          *ngIf="form.get('orden')?.invalid && form.get('orden')?.touched"
          >Orden es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="estado">Estado *</label>
        <select
          formControlName="estado"
          class="form-input"
          data-testid="select-estado"
        >
          <option value="ACTIVO">Activo</option>
          <option value="INACTIVO">Inactivo</option>
        </select>
      </div>

      <div
        class="error-banner"
        *ngIf="error"
        role="alert"
        data-testid="error-banner"
      >
        {{ error }}
      </div>

      <div class="form-actions">
        <button
          type="button"
          class="btn-cancel"
          (click)="onCancel()"
          data-testid="btn-cancel"
        >
          Cancelar
        </button>
        <button
          type="submit"
          class="btn-save"
          [disabled]="form.invalid || loading"
          data-testid="btn-save"
        >
          {{ loading ? 'Guardando...' : 'Guardar' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .cargo-form {
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;

      .form-group {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;

        label {
          font-weight: 600;
          color: var(--color-text-primary);
          font-size: 0.9rem;
        }

        .form-input {
          padding: 0.75rem;
          border: 1px solid var(--color-border-subtle);
          border-radius: 0.375rem;
          font-size: 1rem;
          color: var(--color-text-primary);
          background: var(--color-surface-card);
          transition: border 0.2s ease;

          &:focus {
            outline: none;
            border-color: var(--color-brand-primary);
            border-width: 2px;
          }

          &:invalid {
            border-color: var(--color-status-danger);
          }
        }

        .error {
          font-size: 0.85rem;
          color: var(--color-status-danger);
        }
      }

      .error-banner {
        padding: 1rem;
        background: var(--color-status-danger-bg);
        border: 1px solid var(--color-status-danger-border);
        border-radius: 0.375rem;
        color: var(--color-status-danger);
        font-size: 0.9rem;
      }

      .form-actions {
        display: flex;
        gap: 1rem;
        justify-content: flex-end;
        margin-top: 1rem;

        button {
          padding: 0.75rem 1.5rem;
          border: none;
          border-radius: 0.375rem;
          font-weight: 600;
          cursor: pointer;
          transition: background 0.2s ease;

          &.btn-cancel {
            background: var(--color-surface-raised);
            color: var(--color-text-primary);

            &:hover {
              background: var(--color-border-subtle);
            }
          }

          &.btn-save {
            background: var(--color-brand-primary);
            color: white;

            &:hover:not(:disabled) {
              background: var(--color-brand-primary-strong);
            }

            &:disabled {
              opacity: 0.6;
              cursor: not-allowed;
            }
          }
        }
      }
    }
  `,
})
export class CargoModalComponent implements OnInit {
  private readonly api = inject(ConfiguracionApi);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly toast = inject(ToastService);

  data = this.config.data;
  form!: FormGroup;
  loading = false;
  error: string | null = null;

  ngOnInit(): void {
    this.form = new FormGroup({
      nombre: new FormControl('', Validators.required),
      nivelTerritorial: new FormControl('', Validators.required),
      orden: new FormControl('', Validators.required),
      estado: new FormControl('ACTIVO', Validators.required),
    });

    if (this.data?.mode === 'edit' && this.data?.cargo) {
      this.form.patchValue(this.data.cargo);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.loading = true;
    this.error = null;

    const payload: CargoRequest = this.form.value;

    const request$ =
      this.data?.mode === 'edit' && this.data?.cargo
        ? this.api.updateCargo(this.data.cargo.id, payload)
        : this.api.createCargo(payload);

    request$.subscribe({
      next: (cargo) => {
        this.loading = false;
        // El exito se avisa con un toast (ODD 505, A.4): el modal se cierra y
        // el operador necesita saber que salio bien. El error NO se avisa por
        // toast: el modal ya lo muestra con su `role="alert"` y con el motivo
        // (409, 400, otro), y repetirlo en dos sitios seria avisar dos veces.
        this.toast.success(
          this.data?.mode === 'edit' ? 'Cargo actualizado' : 'Cargo creado',
          cargo.nombre,
        );
        this.dialogRef.close({ success: true, cargo });
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 409) {
          this.error = 'Este cargo ya existe o hay conflicto de integridad.';
        } else if (err.status === 400) {
          this.error = 'Datos inválidos. Verifica los campos.';
        } else {
          this.error = 'Error al guardar el cargo. Intenta nuevamente.';
        }
      },
    });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
