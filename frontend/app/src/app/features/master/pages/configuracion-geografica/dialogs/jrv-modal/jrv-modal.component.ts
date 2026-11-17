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
import { JrvNodoDto } from '../../../../domain/models/geografia.model';

/**
 * Modal de alta y edicion de una JRV (SDD 2026-11-16-config-geografica).
 *
 * <p>La JRV cuelga de una parroquia. El numero de JRV es unico en el sistema y el total de
 * electores tiene que ser mayor que cero: el backend responde 409 y 400 y el modal muestra el
 * motivo.
 */
@Component({
  selector: 'app-jrv-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="geografia-form">
      <div class="form-group" *ngIf="parroquiaNombre">
        <label>Parroquia</label>
        <span class="form-hint" data-testid="parroquia-nombre">{{
          parroquiaNombre
        }}</span>
      </div>

      <div class="form-group">
        <label for="jrv-numero">Numero *</label>
        <input
          id="jrv-numero"
          type="text"
          formControlName="numero"
          class="form-input"
          placeholder="Ej. JRV-SE-001"
          data-testid="input-numero"
        />
        <span
          class="error"
          *ngIf="form.get('numero')?.invalid && form.get('numero')?.touched"
          >El numero es obligatorio</span
        >
        <span class="form-hint">Unico en el sistema.</span>
      </div>

      <div class="form-group">
        <label for="jrv-recinto">Recinto *</label>
        <input
          id="jrv-recinto"
          type="text"
          formControlName="recinto"
          class="form-input"
          placeholder="Ej. Escuela Abdón Calderón"
          data-testid="input-recinto"
        />
        <span
          class="error"
          *ngIf="form.get('recinto')?.invalid && form.get('recinto')?.touched"
          >El recinto es obligatorio</span
        >
      </div>

      <div class="form-group">
        <label for="jrv-electores">Total de electores *</label>
        <input
          id="jrv-electores"
          type="number"
          min="1"
          formControlName="totalElectores"
          class="form-input"
          placeholder="Ej. 320"
          data-testid="input-electores"
        />
        <span
          class="error"
          *ngIf="
            form.get('totalElectores')?.invalid &&
            form.get('totalElectores')?.touched
          "
          >Tiene que haber al menos 1 elector</span
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
export class JrvModalComponent implements OnInit {
  private readonly api = inject(GeografiaApi);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly toast = inject(ToastService);

  data = this.config.data as
    | {
        mode?: 'create' | 'edit';
        parroquiaId?: number;
        parroquiaNombre?: string;
        jrv?: JrvNodoDto;
      }
    | undefined;

  form!: FormGroup;
  loading = false;
  error: string | null = null;

  /** Parroquia a la que cuelga la JRV. */
  parroquiaId?: number;
  parroquiaNombre?: string;

  ngOnInit(): void {
    this.form = new FormGroup({
      numero: new FormControl('', [
        Validators.required,
        sinSoloEspacios,
        Validators.maxLength(20),
      ]),
      recinto: new FormControl('', [
        Validators.required,
        sinSoloEspacios,
        Validators.maxLength(200),
      ]),
      totalElectores: new FormControl(100, [
        Validators.required,
        Validators.min(1),
      ]),
    });

    if (this.data?.mode === 'edit' && this.data?.jrv) {
      this.form.patchValue({
        numero: this.data.jrv.numero,
        recinto: this.data.jrv.recinto,
        totalElectores: this.data.jrv.totalElectores,
      });
      this.parroquiaId = this.data.parroquiaId;
      this.parroquiaNombre = this.data.parroquiaNombre;
    } else {
      this.parroquiaId = this.data?.parroquiaId;
      this.parroquiaNombre = this.data?.parroquiaNombre;
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      return;
    }

    if (this.parroquiaId === undefined || this.parroquiaId === null) {
      this.error = 'No se sabe a que parroquia pertenece la JRV.';
      return;
    }

    this.loading = true;
    this.error = null;

    const payload = {
      numero: (this.form.get('numero')?.value ?? '').trim(),
      recinto: (this.form.get('recinto')?.value ?? '').trim(),
      totalElectores: Number(this.form.get('totalElectores')?.value ?? 0),
      parroquiaId: this.parroquiaId,
    };

    const esEdicion = this.data?.mode === 'edit' && this.data?.jrv;
    const request$ = esEdicion
      ? this.api.actualizarJrv(this.data!.jrv!.id, payload)
      : this.api.crearJrv(payload);

    request$.subscribe({
      next: (jrv) => {
        this.loading = false;
        this.toast.success(
          esEdicion ? 'JRV actualizada' : 'JRV creada',
          jrv.numero,
        );
        this.dialogRef.close({ success: true, jrv });
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
      return 'Ya existe una JRV con ese numero.';
    }
    if (err?.status === 400) {
      return 'Datos invalidos. Revisa el numero, el recinto y el total de electores.';
    }
    return err?.error?.message ?? 'No se pudo guardar la JRV.';
  }
}
