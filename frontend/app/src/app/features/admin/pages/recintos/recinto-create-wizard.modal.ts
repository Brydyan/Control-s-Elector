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
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { Checkbox } from 'primeng/checkbox';
import { Message } from 'primeng/message';
import { Stepper } from 'primeng/stepper';
import { Button } from 'primeng/button';
import {
  RecintoApi,
  CrearRecintoConAsignacionesRequest,
} from '../../data/recinto.api';
import { AdminApi } from '../../data/admin.api';
import {
  GeografiaApi,
  ProvinciaNode,
  CantonNode,
} from '../../data/geografia.api';
import { UsuarioResponseDto } from '../../domain/models/admin.model';

@Component({
  selector: 'app-recinto-create-wizard-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    Dialog,
    InputText,
    InputNumber,
    Select,
    Checkbox,
    Message,
    Stepper,
    Button,
  ],
  template: `
    <p-dialog
      [(visible)]="abierto"
      header="Crear Recinto"
      [modal]="true"
      [closable]="true"
      (onHide)="cancelar()"
      [style]="{ width: '90vw', maxWidth: '900px' }"
      [maximizable]="false"
    >
      <p-message
        *ngIf="error()"
        severity="error"
        [text]="error() || ''"
        styleClass="w-full mb-4"
      ></p-message>

      <p-stepper
        #stepper
        [linear]="true"
        (activeIndexChange)="activeStepIndex = $event"
      >
        <!-- Paso 1: Datos Principales -->
        <p-stepperPanel header="Datos Principales">
          <ng-template pTemplate="content">
            <form [formGroup]="formularioPaso1" class="form-container">
              <div class="form-group">
                <label>Nombre del Recinto *</label>
                <input
                  pInputText
                  formControlName="nombre"
                  placeholder="Ej: Escuela República del Ecuador"
                  class="w-full"
                />
                <small
                  *ngIf="
                    formularioPaso1.get('nombre')?.invalid &&
                    formularioPaso1.get('nombre')?.touched
                  "
                  class="text-red-500"
                >
                  Mínimo 3 caracteres requeridos
                </small>
              </div>

              <div class="grid grid-cols-3 gap-4">
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
              </div>

              <div class="form-group">
                <label>Cantidad de JRVs *</label>
                <p-inputNumber
                  formControlName="cantidadJrvs"
                  [min]="1"
                  [max]="999"
                  placeholder="Ej: 10"
                  inputStyleClass="w-full"
                ></p-inputNumber>
                <small
                  *ngIf="
                    formularioPaso1.get('cantidadJrvs')?.invalid &&
                    formularioPaso1.get('cantidadJrvs')?.touched
                  "
                  class="text-red-500"
                >
                  Debe ser mayor a 0
                </small>
              </div>
            </form>

            <ng-template pTemplate="footer">
              <p-button
                label="Siguiente"
                icon="pi pi-arrow-right"
                iconPos="right"
                (onClick)="stepper.next()"
                [disabled]="formularioPaso1.invalid"
              ></p-button>
            </ng-template>
          </ng-template>
        </p-stepperPanel>

        <!-- Paso 2: Asignación de Usuarios -->
        <p-stepperPanel header="Asignar Usuarios">
          <ng-template pTemplate="content">
            <div class="usuario-assignment-container">
              <!-- Observadores -->
              <div class="user-section">
                <div class="section-header">
                  <h3>Observadores</h3>
                  <span class="count-badge">{{ observadoresCount }}/5+</span>
                </div>
                <p class="text-sm text-gray-600 mb-4">
                  Mínimo 5 observadores requeridos
                </p>

                <div class="checkbox-grid">
                  <div *ngFor="let usuario of usuarios" class="checkbox-item">
                    <p-checkbox
                      [binary]="true"
                      formControlName="observadores"
                      [ngModel]="
                        observadoresSeleccionados().includes(usuario.id)
                      "
                      (onChange)="toggleObservador(usuario.id, $event)"
                      [inputId]="'obs-' + usuario.id"
                      styleClass="mr-2"
                    ></p-checkbox>
                    <label [for]="'obs-' + usuario.id" class="cursor-pointer">
                      {{ usuario.nombre }}
                      <small class="text-gray-500 block">{{
                        usuario.email
                      }}</small>
                    </label>
                  </div>
                </div>

                <small
                  *ngIf="
                    observadoresCount < 5 &&
                    intentoGuardar() &&
                    activeStepIndex === 1
                  "
                  class="text-red-500 block mt-2"
                >
                  <i class="pi pi-exclamation-triangle mr-1"></i>
                  Mínimo 5 observadores requeridos (actual:
                  {{ observadoresCount }})
                </small>
              </div>

              <!-- Coordinadores -->
              <div class="user-section mt-6 pt-6 border-t">
                <div class="section-header">
                  <h3>Coordinadores</h3>
                  <span class="count-badge">{{ coordinadoresCount }}/5</span>
                </div>
                <p class="text-sm text-gray-600 mb-4">
                  Máximo 5 coordinadores permitidos
                </p>

                <div class="checkbox-grid">
                  <div *ngFor="let usuario of usuarios" class="checkbox-item">
                    <p-checkbox
                      [binary]="true"
                      formControlName="coordinadores"
                      [ngModel]="
                        coordinadoresSeleccionados().includes(usuario.id)
                      "
                      (onChange)="toggleCoordinador(usuario.id, $event)"
                      [inputId]="'coord-' + usuario.id"
                      [disabled]="
                        coordinadoresCount >= 5 &&
                        !coordinadoresSeleccionados().includes(usuario.id)
                      "
                      styleClass="mr-2"
                    ></p-checkbox>
                    <label [for]="'coord-' + usuario.id" class="cursor-pointer">
                      {{ usuario.nombre }}
                      <small class="text-gray-500 block">{{
                        usuario.email
                      }}</small>
                    </label>
                  </div>
                </div>

                <small
                  *ngIf="coordinadoresCount > 5 && activeStepIndex === 1"
                  class="text-red-500 block mt-2"
                >
                  <i class="pi pi-exclamation-triangle mr-1"></i>
                  Máximo 5 coordinadores permitidos (actual:
                  {{ coordinadoresCount }})
                </small>
              </div>
            </div>

            <ng-template pTemplate="footer">
              <p-button
                label="Anterior"
                severity="secondary"
                icon="pi pi-arrow-left"
                (onClick)="stepper.previous()"
              ></p-button>
              <p-button
                label="Crear"
                icon="pi pi-check"
                [loading]="enviando()"
                [disabled]="observadoresCount < 5 || coordinadoresCount > 5"
                (onClick)="guardar()"
              ></p-button>
            </ng-template>
          </ng-template>
        </p-stepperPanel>
      </p-stepper>
    </p-dialog>
  `,
  styles: `
    .form-container {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
      margin-top: 1rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;

      label {
        font-weight: 500;
        font-size: 0.875rem;
        color: var(--color-text-primary);
      }
    }

    .grid {
      display: grid;
    }

    .gap-4 {
      gap: 1rem;
    }

    .grid-cols-3 {
      grid-template-columns: repeat(3, 1fr);
    }

    .usuario-assignment-container {
      margin-top: 1rem;
    }

    .user-section {
      h3 {
        font-weight: 600;
        font-size: 1rem;
        color: var(--color-text-primary);
        margin: 0;
      }
    }

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.5rem;
    }

    .count-badge {
      background-color: var(--color-surface-raised);
      padding: 0.25rem 0.75rem;
      border-radius: 1rem;
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--color-text-secondary);
    }

    .checkbox-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
      gap: 0.75rem;
      margin-bottom: 1rem;
    }

    .checkbox-item {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      padding: 0.75rem;
      border-radius: 0.375rem;
      border: 1px solid var(--color-gray-200);
      transition: background-color 0.2s;

      &:hover {
        background-color: var(--color-gray-50);
      }

      label {
        margin: 0;
        font-weight: 400;
        font-size: 0.875rem;
        color: var(--color-text-primary);
        line-height: 1.4;
      }
    }

    .w-full {
      width: 100%;
    }

    .mt-6 {
      margin-top: 1.5rem;
    }

    .pt-6 {
      padding-top: 1.5rem;
    }

    .border-t {
      border-top: 1px solid var(--color-gray-200);
    }

    .mb-4 {
      margin-bottom: 1rem;
    }

    .mb-2 {
      margin-bottom: 0.5rem;
    }

    .mt-2 {
      margin-top: 0.5rem;
    }

    small {
      display: block;
    }

    .text-sm {
      font-size: 0.875rem;
    }

    .text-gray-600 {
      color: var(--color-gray-600);
    }

    .text-gray-500 {
      color: var(--color-gray-500);
    }

    .text-red-500 {
      color: var(--color-red-500);
    }

    .block {
      display: block;
    }

    .cursor-pointer {
      cursor: pointer;
    }

    .mr-2 {
      margin-right: 0.5rem;
    }
  `,
})
export class RecintoCreateWizardModalComponent implements OnInit {
  @Input() abierto = false;
  @Output() guardado = new EventEmitter<void>();
  @Output() cancelado = new EventEmitter<void>();

  private readonly recintoApi = inject(RecintoApi);
  private readonly adminApi = inject(AdminApi);
  private readonly geografiaApi = inject(GeografiaApi);
  private readonly fb = inject(FormBuilder);

  readonly error = signal<string | null>(null);
  readonly enviando = signal(false);
  readonly intentoGuardar = signal(false);

  formularioPaso1!: FormGroup;
  provincias: ProvinciaNode[] = [];
  cantones: CantonNode[] = [];
  parroquias: any[] = [];
  usuarios: UsuarioResponseDto[] = [];

  activeStepIndex = 0;

  readonly observadoresSeleccionados = signal<string[]>([]);
  readonly coordinadoresSeleccionados = signal<string[]>([]);

  get observadoresCount(): number {
    return this.observadoresSeleccionados().length;
  }

  get coordinadoresCount(): number {
    return this.coordinadoresSeleccionados().length;
  }

  ngOnInit(): void {
    this.inicializarFormulario();
    this.cargarGeografia();
    this.cargarUsuarios();
  }

  private inicializarFormulario(): void {
    this.formularioPaso1 = this.fb.group({
      nombre: ['', [Validators.required, Validators.minLength(3)]],
      provinciaId: [null, Validators.required],
      cantonId: [null, Validators.required],
      parroquiaId: [null, Validators.required],
      cantidadJrvs: [null, [Validators.required, Validators.min(1)]],
    });
  }

  private cargarGeografia(): void {
    this.geografiaApi.obtenerGeografia().subscribe({
      next: (data) => {
        this.provincias = data.provincias || [];
      },
      error: (err) => {
        this.error.set('Error al cargar provincias');
        console.error('Error loading geography:', err);
      },
    });
  }

  private cargarUsuarios(): void {
    this.adminApi.listarUsuarios({ page: 0, size: 100 }).subscribe({
      next: (response) => {
        this.usuarios = response.data || [];
      },
      error: (err) => {
        this.error.set('Error al cargar usuarios');
        console.error('Error loading users:', err);
      },
    });
  }

  onProvinciaChange(event: any): void {
    const provinciaId = event.value;

    if (!provinciaId) {
      this.cantones = [];
      this.parroquias = [];
      this.formularioPaso1.patchValue({
        cantonId: null,
        parroquiaId: null,
      });
      return;
    }

    // Encontrar provincia y obtener sus cantones
    const provincia = this.provincias.find((p) => p.id === provinciaId);
    this.cantones = provincia?.cantones || [];
    this.parroquias = [];
    this.formularioPaso1.patchValue({
      cantonId: null,
      parroquiaId: null,
    });
  }

  onCantonChange(event: any): void {
    const cantonId = event.value;

    if (!cantonId) {
      this.parroquias = [];
      this.formularioPaso1.patchValue({ parroquiaId: null });
      return;
    }

    // Encontrar canton y obtener sus parroquias
    const canton = this.cantones.find((c) => c.id === cantonId);
    this.parroquias = canton?.parroquias || [];
    this.formularioPaso1.patchValue({ parroquiaId: null });
  }

  toggleObservador(usuarioId: string, $event: any): void {
    const set = new Set(this.observadoresSeleccionados());
    if ($event.checked) {
      set.add(usuarioId);
    } else {
      set.delete(usuarioId);
    }
    this.observadoresSeleccionados.set(Array.from(set));
  }

  toggleCoordinador(usuarioId: string, $event: any): void {
    const set = new Set(this.coordinadoresSeleccionados());
    if ($event.checked) {
      set.add(usuarioId);
    } else {
      set.delete(usuarioId);
    }
    this.coordinadoresSeleccionados.set(Array.from(set));
  }

  guardar(): void {
    this.intentoGuardar.set(true);

    if (
      !this.formularioPaso1.valid ||
      this.observadoresCount < 5 ||
      this.coordinadoresCount > 5
    ) {
      this.error.set('Por favor completa todos los campos requeridos');
      return;
    }

    this.error.set(null);
    this.enviando.set(true);

    const request: CrearRecintoConAsignacionesRequest = {
      nombre: this.formularioPaso1.value.nombre,
      parroquiaId: Number(this.formularioPaso1.value.parroquiaId),
      cantidadJrvs: this.formularioPaso1.value.cantidadJrvs,
      observadorIds: this.observadoresSeleccionados(),
      coordinadorIds: this.coordinadoresSeleccionados(),
    };

    this.recintoApi.crearRecintoConAsignaciones(request).subscribe({
      next: () => {
        this.enviando.set(false);
        this.guardado.emit();
      },
      error: (err) => {
        this.enviando.set(false);
        this.error.set(err.message || 'Error al crear recinto');
      },
    });
  }

  cancelar(): void {
    this.formularioPaso1.reset();
    this.observadoresSeleccionados.set([]);
    this.coordinadoresSeleccionados.set([]);
    this.error.set(null);
    this.intentoGuardar.set(false);
    this.activeStepIndex = 0;
    this.cancelado.emit();
  }
}
