import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';

import { ToastService } from '../../../../../../core/services/toast.service';
import { sinSoloEspacios } from '../../../../domain/validators/geografia.validator';
import { GeografiaApi } from '../../../../data/geografia.api';
import { ProvinciaNodoDto } from '../../../../domain/models/geografia.model';

/**
 * Modal de alta y edicion de una provincia (SDD 2026-11-16-config-geografica).
 *
 * <p>El codigo es opcional (una provincia puede no tenerlo) pero cuando viene es unico en toda
 * la tabla: el backend responde 409 y el modal lo muestra con su motivo.
 */
@Component({
  selector: 'app-provincia-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="geografia-form">
      <div class="form-group">
        <label for="provincia-nombre">Nombre *</label>
        <input
          id="provincia-nombre"
          type="text"
          formControlName="nombre"
          class="form-input"
          placeholder="Ej. Santa Elena"
          data-testid="input-nombre"
        />
        <span
          class="error"
          *ngIf="form.get('nombre')?.invalid && form.get('nombre')?.touched"
          >El nombre es obligatorio</span
        >
      </div>

      <div class="form-group">
        <label for="provincia-codigo">Codigo</label>
        <input
          id="provincia-codigo"
          type="text"
          formControlName="codigo"
          class="form-input"
          placeholder="Ej. 24"
          data-testid="input-codigo"
        />
        <span class="form-hint"
          >Opcional. Si viene, es unico en el sistema.</span
        >
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
export class ProvinciaModalComponent implements OnInit {
  private readonly api = inject(GeografiaApi);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly toast = inject(ToastService);

  data = this.config.data as
    { mode?: 'create' | 'edit'; provincia?: ProvinciaNodoDto } | undefined;

  form!: FormGroup;
  loading = false;
  error: string | null = null;

  ngOnInit(): void {
    this.form = new FormGroup({
      nombre: new FormControl('', [
        Validators.required,
        sinSoloEspacios,
        Validators.maxLength(100),
      ]),
      codigo: new FormControl('', [Validators.maxLength(10)]),
    });

    if (this.data?.mode === 'edit' && this.data?.provincia) {
      this.form.patchValue({
        nombre: this.data.provincia.nombre,
        codigo: this.data.provincia.codigo ?? '',
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      return;
    }

    this.loading = true;
    this.error = null;

    const payload = {
      nombre: (this.form.get('nombre')?.value ?? '').trim(),
      codigo: (this.form.get('codigo')?.value ?? '').trim(),
    };

    const esEdicion = this.data?.mode === 'edit' && this.data?.provincia;
    const request$ = esEdicion
      ? this.api.actualizarProvincia(this.data!.provincia!.id, payload)
      : this.api.crearProvincia(payload);

    request$.subscribe({
      next: (provincia) => {
        this.loading = false;
        this.toast.success(
          esEdicion ? 'Provincia actualizada' : 'Provincia creada',
          provincia.nombre,
        );
        this.dialogRef.close({ success: true, provincia });
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
      return 'Ya existe una provincia con ese codigo.';
    }
    if (err?.status === 400) {
      return 'Datos invalidos. Revisa el nombre y el codigo.';
    }
    return err?.error?.message ?? 'No se pudo guardar la provincia.';
  }
}
