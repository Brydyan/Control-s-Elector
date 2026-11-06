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
  PartidoDto,
  PartidoRequest,
} from '../../../../domain/models/configuracion.model';

/**
 * T-FE-16 PartidoModalComponent — Modal para crear/editar partidos políticos.
 */
@Component({
  selector: 'app-partido-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="partido-form">
      <div class="form-group">
        <label for="nombre">Nombre *</label>
        <input
          id="nombre"
          type="text"
          formControlName="nombre"
          class="form-input"
          placeholder="ej. Fuerza Compromiso Social"
          data-testid="input-nombre"
        />
        <span
          class="error"
          *ngIf="form.get('nombre')?.invalid && form.get('nombre')?.touched"
          >Nombre es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="siglas">Siglas *</label>
        <input
          id="siglas"
          type="text"
          formControlName="siglas"
          class="form-input"
          placeholder="ej. FCS"
          data-testid="input-siglas"
        />
        <span
          class="error"
          *ngIf="form.get('siglas')?.invalid && form.get('siglas')?.touched"
          >Siglas son requeridas</span
        >
      </div>

      <div class="form-group">
        <label for="numeroLista">Número de Lista *</label>
        <input
          id="numeroLista"
          type="number"
          formControlName="numeroLista"
          class="form-input"
          placeholder="20"
          data-testid="input-numeroLista"
        />
        <span
          class="error"
          *ngIf="
            form.get('numeroLista')?.invalid && form.get('numeroLista')?.touched
          "
          >Número de lista es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="color">Color</label>
        <input
          id="color"
          type="text"
          formControlName="color"
          class="form-input"
          placeholder="ej. green, blue"
          data-testid="input-color"
        />
        <small>Ej: green, blue, red</small>
      </div>

      <div class="form-group">
        <label for="logoUrl">URL del Logo</label>
        <input
          id="logoUrl"
          type="url"
          formControlName="logoUrl"
          class="form-input"
          placeholder="https://..."
          data-testid="input-logoUrl"
        />
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
    .partido-form {
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

        small {
          color: var(--color-text-secondary);
          font-size: 0.8rem;
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
export class PartidoModalComponent implements OnInit {
  private readonly api = inject(ConfiguracionApi);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);

  data = this.config.data;
  form!: FormGroup;
  loading = false;
  error: string | null = null;

  ngOnInit(): void {
    this.form = new FormGroup({
      nombre: new FormControl('', Validators.required),
      siglas: new FormControl('', Validators.required),
      numeroLista: new FormControl('', Validators.required),
      color: new FormControl(''),
      logoUrl: new FormControl(''),
      estado: new FormControl('ACTIVO', Validators.required),
    });

    if (this.data?.mode === 'edit' && this.data?.partido) {
      this.form.patchValue(this.data.partido);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.loading = true;
    this.error = null;

    const payload: PartidoRequest = this.form.value;

    const request$ =
      this.data?.mode === 'edit' && this.data?.partido
        ? this.api.updatePartido(this.data.partido.id, payload)
        : this.api.createPartido(payload);

    request$.subscribe({
      next: (partido) => {
        this.loading = false;
        this.dialogRef.close({ success: true, partido });
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 409) {
          this.error = 'Este partido ya existe o hay conflicto.';
        } else if (err.status === 400) {
          this.error = 'Datos inválidos. Verifica los campos.';
        } else {
          this.error = 'Error al guardar el partido. Intenta nuevamente.';
        }
      },
    });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
