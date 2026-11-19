import { CommonModule } from '@angular/common';
import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import {
  FormsModule,
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Message } from 'primeng/message';
import { ButtonComponent } from '../../../../shared/ui';
import {
  RecintoApi,
  RecintoResponse,
  RecintoRequest,
} from '../../data/recinto.api';
import { AdminApi } from '../../data/admin.api';
import { UsuarioResponseDto } from '../../domain/models/admin.model';

interface Provincia {
  id: number;
  nombre: string;
}

interface Canton {
  id: number;
  nombre: string;
  provinciaId: number;
}

interface Parroquia {
  id: number;
  nombre: string;
  cantonId: number;
}

@Component({
  selector: 'app-recinto-form-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    Dialog,
    InputText,
    Select,
    Message,
    ButtonComponent,
  ],
  template: `
    <p-dialog
      [(visible)]="abierto"
      [header]="modoEdicion ? 'Editar Recinto' : 'Crear Recinto'"
      [modal]="true"
      [closable]="true"
      (onHide)="cancelar()"
      [style]="{ width: '90vw' }"
      [maximizable]="true"
    >
      <p-message
        *ngIf="error()"
        severity="error"
        [text]="error() || ''"
        styleClass="w-full mb-4"
      ></p-message>

      <form [formGroup]="formulario" class="form-container">
        <div class="form-group">
          <label>Provincia *</label>
          <p-select
            formControlName="provinciaId"
            [options]="provincias"
            optionLabel="nombre"
            optionValue="id"
            placeholder="Seleccionar provincia"
            (onChange)="onProvinciaChange($event)"
            [showClear]="true"
          ></p-select>
        </div>

        <div class="form-group">
          <label>Cantón *</label>
          <p-select
            formControlName="cantonId"
            [options]="cantones"
            optionLabel="nombre"
            optionValue="id"
            placeholder="Seleccionar cantón"
            [disabled]="cantones.length === 0"
            (onChange)="onCantonChange($event)"
            [showClear]="true"
          ></p-select>
        </div>

        <div class="form-group">
          <label>Parroquia *</label>
          <p-select
            formControlName="parroquiaId"
            [options]="parroquias"
            optionLabel="nombre"
            optionValue="id"
            placeholder="Seleccionar parroquia"
            [disabled]="parroquias.length === 0"
            [showClear]="true"
          ></p-select>
        </div>

        <div class="form-group">
          <label>Nombre del Recinto *</label>
          <input
            pInputText
            formControlName="nombre"
            placeholder="Ej: Escuela República del Ecuador"
            class="w-full"
          />
        </div>

        <div class="form-group">
          <label>Coordinador</label>
          <p-select
            formControlName="coordinadorId"
            [options]="coordinadores"
            optionLabel="nombre"
            optionValue="id"
            placeholder="Seleccionar coordinador"
            [showClear]="true"
          ></p-select>
        </div>

        <div class="form-group">
          <label>Estado</label>
          <p-select
            formControlName="estado"
            [options]="[
              { label: 'Activo', value: 'ACTIVO' },
              { label: 'Inactivo', value: 'INACTIVO' },
            ]"
            optionLabel="label"
            optionValue="value"
          ></p-select>
        </div>
      </form>

      <ng-template pTemplate="footer">
        <button
          pButton
          type="button"
          label="Cancelar"
          icon="pi pi-times"
          class="p-button-text"
          (click)="cancelar()"
        ></button>
        <button
          pButton
          type="button"
          [label]="modoEdicion ? 'Actualizar' : 'Crear'"
          icon="pi pi-check"
          [loading]="enviando()"
          (click)="guardar()"
        ></button>
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .form-container {
      display: flex;
      flex-direction: column;
      gap: 1rem;
      margin-top: 1rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;

      label {
        font-weight: 500;
        font-size: 0.875rem;
        color: #333;
      }
    }
  `,
})
export class RecintoFormModalComponent implements OnInit {
  @Input() abierto = false;
  @Input() recinto: RecintoResponse | null = null;
  @Input() modoEdicion = false;
  @Output() guardado = new EventEmitter<void>();
  @Output() cancelado = new EventEmitter<void>();

  private readonly recintoApi = inject(RecintoApi);
  private readonly adminApi = inject(AdminApi);
  private readonly fb = inject(FormBuilder);

  readonly error = signal<string | null>(null);
  readonly enviando = signal(false);

  formulario!: FormGroup;
  provincias: Provincia[] = [];
  cantones: Canton[] = [];
  parroquias: Parroquia[] = [];
  coordinadores: UsuarioResponseDto[] = [];

  ngOnInit(): void {
    this.inicializarFormulario();
    this.cargarProvincias();
    this.cargarCoordinadores();
  }

  private inicializarFormulario(): void {
    this.formulario = this.fb.group({
      provinciaId: [null, Validators.required],
      cantonId: [null, Validators.required],
      parroquiaId: [null, Validators.required],
      nombre: ['', [Validators.required, Validators.minLength(3)]],
      coordinadorId: [null],
      estado: ['ACTIVO'],
    });
  }

  private cargarProvincias(): void {
    // TODO: Fetch from geografía API
    this.provincias = [
      { id: 1, nombre: 'Pichincha' },
      { id: 2, nombre: 'Guayas' },
      { id: 3, nombre: 'Azuay' },
    ];
  }

  private cargarCoordinadores(): void {
    this.adminApi.listarUsuarios({ page: 0, size: 1000 }).subscribe({
      next: (response) => {
        this.coordinadores =
          response.data?.filter((u) => u.rol === 'COORDINATOR') || [];
      },
      error: (err) => {
        console.error('Error loading coordinators:', err);
      },
    });
  }

  onProvinciaChange(event: any): void {
    const provinciaId = event.value;
    // TODO: Fetch from geografía API based on provinciaId
    this.cantones = [];
    this.parroquias = [];
    this.formulario.patchValue({ cantonId: null, parroquiaId: null });
  }

  onCantonChange(event: any): void {
    const cantonId = event.value;
    // TODO: Fetch from geografía API based on cantonId
    this.parroquias = [];
    this.formulario.patchValue({ parroquiaId: null });
  }

  guardar(): void {
    if (!this.formulario.valid) {
      this.error.set('Por favor completa todos los campos requeridos');
      return;
    }

    this.error.set(null);
    this.enviando.set(true);

    const request: RecintoRequest = {
      nombre: this.formulario.value.nombre,
      parroquiaId: this.formulario.value.parroquiaId,
      coordinadorId: this.formulario.value.coordinadorId,
      estado: this.formulario.value.estado,
    };

    const observable = this.modoEdicion
      ? this.recintoApi.actualizarRecinto(this.recinto!.id, request)
      : this.recintoApi.crearRecinto(request);

    observable.subscribe({
      next: () => {
        this.enviando.set(false);
        this.guardado.emit();
      },
      error: (err) => {
        this.enviando.set(false);
        this.error.set(err.message || 'Error al guardar recinto');
      },
    });
  }

  cancelar(): void {
    this.formulario.reset();
    this.error.set(null);
    this.cancelado.emit();
  }
}
