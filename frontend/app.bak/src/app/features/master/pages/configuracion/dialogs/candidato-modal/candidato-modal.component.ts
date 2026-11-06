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
  CandidatoDto,
  CandidatoRequest,
} from '../../../../domain/models/configuracion.model';

/**
 * T-FE-16 CandidatoModalComponent — Modal para crear/editar candidatos.
 *
 * Datos requeridos del padre:
 * - cargos: CargoDto[] (para selector)
 * - partidos: PartidoDto[] (para selector)
 * - candidato?: CandidatoDto (si modo edit)
 */
@Component({
  selector: 'app-candidato-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="candidato-form">
      <div class="form-group">
        <label for="nombre">Nombre *</label>
        <input
          id="nombre"
          type="text"
          formControlName="nombre"
          class="form-input"
          placeholder="Juan Pérez García"
          data-testid="input-nombre"
        />
        <span
          class="error"
          *ngIf="form.get('nombre')?.invalid && form.get('nombre')?.touched"
          >Nombre es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="cargoId">Cargo *</label>
        <select
          formControlName="cargoId"
          class="form-input"
          data-testid="select-cargo"
        >
          <option value="">-- Selecciona Cargo --</option>
          <option *ngFor="let cargo of cargos" [value]="cargo.id">
            {{ cargo.nombre }}
          </option>
        </select>
        <span
          class="error"
          *ngIf="form.get('cargoId')?.invalid && form.get('cargoId')?.touched"
          >Cargo es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="partidoId">Partido *</label>
        <select
          formControlName="partidoId"
          class="form-input"
          data-testid="select-partido"
        >
          <option value="">-- Selecciona Partido --</option>
          <option *ngFor="let partido of partidos" [value]="partido.id">
            {{ partido.nombre }} ({{ partido.siglas }})
          </option>
        </select>
        <span
          class="error"
          *ngIf="
            form.get('partidoId')?.invalid && form.get('partidoId')?.touched
          "
          >Partido es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="ordenLista">Orden en Lista *</label>
        <input
          id="ordenLista"
          type="number"
          formControlName="ordenLista"
          class="form-input"
          placeholder="1"
          data-testid="input-ordenLista"
        />
        <span
          class="error"
          *ngIf="
            form.get('ordenLista')?.invalid && form.get('ordenLista')?.touched
          "
          >Orden es requerido</span
        >
      </div>

      <div class="form-group">
        <label for="fotoUrl">URL de Foto</label>
        <input
          id="fotoUrl"
          type="url"
          formControlName="fotoUrl"
          class="form-input"
          placeholder="https://..."
          data-testid="input-fotoUrl"
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
    .candidato-form {
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
export class CandidatoModalComponent implements OnInit {
  private readonly api = inject(ConfiguracionApi);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);

  data = this.config.data;
  form!: FormGroup;
  loading = false;
  error: string | null = null;
  cargos = this.data?.cargos || [];
  partidos = this.data?.partidos || [];

  ngOnInit(): void {
    this.form = new FormGroup({
      nombre: new FormControl('', Validators.required),
      cargoId: new FormControl('', Validators.required),
      partidoId: new FormControl('', Validators.required),
      ordenLista: new FormControl('', Validators.required),
      fotoUrl: new FormControl(''),
      estado: new FormControl('ACTIVO', Validators.required),
    });

    if (this.data?.mode === 'edit' && this.data?.candidato) {
      this.form.patchValue(this.data.candidato);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.loading = true;
    this.error = null;

    const payload: CandidatoRequest = this.form.value;

    const request$ =
      this.data?.mode === 'edit' && this.data?.candidato
        ? this.api.updateCandidato(this.data.candidato.id, payload)
        : this.api.createCandidato(payload);

    request$.subscribe({
      next: (candidato) => {
        this.loading = false;
        this.dialogRef.close({ success: true, candidato });
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 409) {
          this.error = 'Este candidato ya existe o hay conflicto.';
        } else if (err.status === 400) {
          this.error = 'Datos inválidos. Verifica los campos.';
        } else {
          this.error = 'Error al guardar el candidato. Intenta nuevamente.';
        }
      },
    });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
