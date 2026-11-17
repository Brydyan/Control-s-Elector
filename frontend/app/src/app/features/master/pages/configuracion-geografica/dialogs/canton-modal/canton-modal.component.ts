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
import { CantonNodoDto } from '../../../../domain/models/geografia.model';

/**
 * Modal de alta y edicion de un canton (SDD 2026-11-16-config-geografica).
 *
 * <p>El canton cuelga de una provincia: en el alta la provincia la elige el nodo del arbol del
 * que se salio el modal, y en la edicion es la que ya tenia.
 */
@Component({
  selector: 'app-canton-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="geografia-form">
      <div class="form-group" *ngIf="provinciaNombre">
        <label>Provincia</label>
        <span class="form-hint" data-testid="provincia-nombre">{{
          provinciaNombre
        }}</span>
      </div>

      <div class="form-group">
        <label for="canton-nombre">Nombre *</label>
        <input
          id="canton-nombre"
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
        <label for="canton-codigo">Codigo *</label>
        <input
          id="canton-codigo"
          type="text"
          formControlName="codigo"
          class="form-input"
          placeholder="Ej. 2401"
          data-testid="input-codigo"
        />
        <span
          class="error"
          *ngIf="form.get('codigo')?.invalid && form.get('codigo')?.touched"
          >El codigo es obligatorio</span
        >
        <span class="form-hint">Unico en el sistema.</span>
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
export class CantonModalComponent implements OnInit {
  private readonly api = inject(GeografiaApi);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly toast = inject(ToastService);

  data = this.config.data as
    | {
        mode?: 'create' | 'edit';
        provinciaId?: number;
        provinciaNombre?: string;
        canton?: CantonNodoDto;
      }
    | undefined;

  form!: FormGroup;
  loading = false;
  error: string | null = null;

  /** Provincia a la que cuelga el canton: la del nodo del arbol, o la del canton editado. */
  provinciaId?: number;
  provinciaNombre?: string;

  ngOnInit(): void {
    this.form = new FormGroup({
      nombre: new FormControl('', [
        Validators.required,
        sinSoloEspacios,
        Validators.maxLength(100),
      ]),
      codigo: new FormControl('', [
        Validators.required,
        sinSoloEspacios,
        Validators.maxLength(10),
      ]),
    });

    if (this.data?.mode === 'edit' && this.data?.canton) {
      this.form.patchValue({
        nombre: this.data.canton.nombre,
        codigo: this.data.canton.codigo,
      });
      this.provinciaId = this.data.provinciaId;
      this.provinciaNombre = this.data.provinciaNombre;
    } else {
      this.provinciaId = this.data?.provinciaId;
      this.provinciaNombre = this.data?.provinciaNombre;
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      return;
    }

    if (this.provinciaId === undefined || this.provinciaId === null) {
      this.error = 'No se sabe a que provincia pertenece el canton.';
      return;
    }

    this.loading = true;
    this.error = null;

    const payload = {
      nombre: (this.form.get('nombre')?.value ?? '').trim(),
      codigo: (this.form.get('codigo')?.value ?? '').trim(),
      provinciaId: this.provinciaId,
    };

    const esEdicion = this.data?.mode === 'edit' && this.data?.canton;
    const request$ = esEdicion
      ? this.api.actualizarCanton(this.data!.canton!.id, payload)
      : this.api.crearCanton(payload);

    request$.subscribe({
      next: (canton) => {
        this.loading = false;
        this.toast.success(
          esEdicion ? 'Canton actualizado' : 'Canton creado',
          canton.nombre,
        );
        this.dialogRef.close({ success: true, canton });
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
      return 'Ya existe un canton con ese codigo.';
    }
    if (err?.status === 400) {
      return 'Datos invalidos. Revisa el nombre y el codigo.';
    }
    return err?.error?.message ?? 'No se pudo guardar el canton.';
  }
}
