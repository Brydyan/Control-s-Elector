import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ToggleSwitch } from 'primeng/toggleswitch';

import { ToastService } from '../../../../../../core/services/toast.service';
import { sinSoloEspacios } from '../../../../domain/validators/geografia.validator';
import { GeografiaApi } from '../../../../data/geografia.api';
import { ParroquiaNodoDto } from '../../../../domain/models/geografia.model';

/**
 * Modal de alta y edicion de una parroquia (SDD 2026-11-16-config-geografica).
 *
 * <p>La parroquia cuelga de un canton. El interruptor de "urbana" es el atributo que
 * distingue la parroquia urbana de la rural.
 */
@Component({
  selector: 'app-parroquia-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ToggleSwitch],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="geografia-form">
      <div class="form-group" *ngIf="cantonNombre">
        <label>Canton</label>
        <span class="form-hint" data-testid="canton-nombre">{{
          cantonNombre
        }}</span>
      </div>

      <div class="form-group">
        <label for="parroquia-nombre">Nombre *</label>
        <input
          id="parroquia-nombre"
          type="text"
          formControlName="nombre"
          class="form-input"
          placeholder="Ej. Ballenita"
          data-testid="input-nombre"
        />
        <span
          class="error"
          *ngIf="form.get('nombre')?.invalid && form.get('nombre')?.touched"
          >El nombre es obligatorio</span
        >
      </div>

      <div class="form-group">
        <label for="parroquia-urbana">Parroquia urbana</label>
        <p-toggleswitch
          inputId="parroquia-urbana"
          formControlName="esUrbana"
          ariaLabel="Parroquia urbana"
        />
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
  styleUrls: ['../geografia-modal.scss'],
})
export class ParroquiaModalComponent implements OnInit {
  private readonly api = inject(GeografiaApi);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly toast = inject(ToastService);

  data = this.config.data as
    | {
        mode?: 'create' | 'edit';
        cantonId?: number;
        cantonNombre?: string;
        parroquia?: ParroquiaNodoDto;
      }
    | undefined;

  form!: FormGroup;
  loading = false;
  error: string | null = null;

  /** Canton al que cuelga la parroquia. */
  cantonId?: number;
  cantonNombre?: string;

  ngOnInit(): void {
    this.form = new FormGroup({
      nombre: new FormControl('', [
        Validators.required,
        sinSoloEspacios,
        Validators.maxLength(100),
      ]),
      esUrbana: new FormControl(true),
    });

    if (this.data?.mode === 'edit' && this.data?.parroquia) {
      this.form.patchValue({
        nombre: this.data.parroquia.nombre,
        esUrbana: this.data.parroquia.esUrbana,
      });
      this.cantonId = this.data.cantonId;
      this.cantonNombre = this.data.cantonNombre;
    } else {
      this.cantonId = this.data?.cantonId;
      this.cantonNombre = this.data?.cantonNombre;
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      return;
    }

    if (this.cantonId === undefined || this.cantonId === null) {
      this.error = 'No se sabe a que canton pertenece la parroquia.';
      return;
    }

    this.loading = true;
    this.error = null;

    const payload = {
      nombre: (this.form.get('nombre')?.value ?? '').trim(),
      esUrbana: Boolean(this.form.get('esUrbana')?.value),
      cantonId: this.cantonId,
    };

    const esEdicion = this.data?.mode === 'edit' && this.data?.parroquia;
    const request$ = esEdicion
      ? this.api.actualizarParroquia(this.data!.parroquia!.id, payload)
      : this.api.crearParroquia(payload);

    request$.subscribe({
      next: (parroquia) => {
        this.loading = false;
        this.toast.success(
          esEdicion ? 'Parroquia actualizada' : 'Parroquia creada',
          parroquia.nombre,
        );
        this.dialogRef.close({ success: true, parroquia });
      },
      error: (err) => {
        this.loading = false;
        this.error = this.mensajeDeError(err);
      },
    });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }

  /** Traduce el status del backend a un mensaje que el operador pueda actuar. */
  private mensajeDeError(err: {
    status?: number;
    error?: { message?: string };
  }): string {
    if (err?.status === 409) {
      return 'Ya existe una parroquia con ese nombre en el canton.';
    }
    if (err?.status === 400) {
      return 'Datos invalidos. Revisa el nombre.';
    }
    return err?.error?.message ?? 'No se pudo guardar la parroquia.';
  }
}
