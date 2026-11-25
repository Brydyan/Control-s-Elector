import { CommonModule } from '@angular/common';
import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  SimpleChanges,
  inject,
  signal,
  ViewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { TableModule } from 'primeng/table';
import { Message } from 'primeng/message';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { GeografiaApi } from '../../data/geografia.api';
import {
  GenerarJrvsRangoRequest,
  JrvResponse,
  RecintoApi,
  RecintoResponse,
} from '../../data/recinto.api';

interface Jrv {
  id: number;
  numero: string;
  totalElectores: number;
  recintoId: number | null;
  asignada: boolean;
}

@Component({
  selector: 'app-recinto-jrvs-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Dialog,
    TableModule,
    Message,
    ButtonModule,
    ConfirmDialogComponent,
  ],
  template: `
    <p-dialog
      [(visible)]="abierto"
      header="Asignar JRVs al Recinto"
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
      <p-message
        *ngIf="mensaje()"
        severity="success"
        [text]="mensaje() || ''"
        styleClass="w-full mb-4"
      ></p-message>

      <section class="generacion-panel">
        <h2>Generación Rápida por Rango</h2>
        <p class="text-sm text-gray-600">
          Las nuevas JRVs se vinculan directamente a
          <strong>{{ recinto?.nombre }}</strong> y reciben todos los cargos activos.
        </p>
        <div class="generacion-campos">
          <label>
            Desde
            <input
              type="number"
              min="1"
              [(ngModel)]="generacion.desde"
              [ngModelOptions]="{ standalone: true }"
            />
          </label>
          <label>
            Hasta
            <input
              type="number"
              min="1"
              [(ngModel)]="generacion.hasta"
              [ngModelOptions]="{ standalone: true }"
            />
          </label>
          <label>
            Sufijo / Género
            <input
              type="text"
              maxlength="8"
              [(ngModel)]="generacion.sufijoGenero"
              [ngModelOptions]="{ standalone: true }"
              (ngModelChange)="actualizarSiguienteNumero()"
              placeholder="Ej: M, F"
            />
          </label>
          <label>
            Electores por mesa
            <input
              type="number"
              min="1"
              [(ngModel)]="generacion.electoresPorMesa"
              [ngModelOptions]="{ standalone: true }"
            />
          </label>
          <button
            pButton
            type="button"
            label="Generar"
            icon="pi pi-bolt"
            [loading]="enviando()"
            (click)="generarJrvs()"
          ></button>
        </div>
      </section>

      <div class="flex justify-between items-center mb-4">
        <p class="text-sm text-gray-600 m-0">
          JRVs de la parroquia disponibles para asignar a
          <strong>{{ recinto?.nombre }}</strong>.
        </p>
        <button
          *ngIf="jrvsSeleccionadas.length > 0"
          pButton
          type="button"
          label="Eliminar seleccionadas ({{ jrvsSeleccionadas.length }})"
          icon="pi pi-trash"
          class="p-button-danger p-button-sm"
          [loading]="eliminando()"
          (click)="eliminarSeleccionadas()"
        ></button>
      </div>

      <div class="table-wrapper">
        <p-table
          [value]="jrvs"
          [(selection)]="jrvsSeleccionadas"
          dataKey="id"
          responsiveLayout="scroll"
          styleClass="app-table"
        >
          <ng-template pTemplate="header">
            <tr>
              <th style="width: 3rem">
                <p-tableHeaderCheckbox
                  [disabled]="!hayJrvsDisponibles"
                ></p-tableHeaderCheckbox>
              </th>
              <th pSortableColumn="numero">
                Número <p-sortIcon field="numero"></p-sortIcon>
              </th>
              <th pSortableColumn="totalElectores">Electores</th>
              <th>Estado</th>
              <th style="width: 4rem; text-align: center">Acciones</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-jrv>
            <tr>
              <td>
                <p-tableCheckbox
                  [value]="jrv"
                  [disabled]="jrv.asignada"
                ></p-tableCheckbox>
              </td>
              <td>{{ jrv.numero }}</td>
              <td>{{ jrv.totalElectores }}</td>
              <td>
                <span [class]="jrv.asignada ? 'tag-asignada' : 'tag-disponible'">
                  {{ jrv.asignada ? 'Asignada' : 'Disponible' }}
                </span>
              </td>
              <td style="text-align: center">
                <button
                  pButton
                  type="button"
                  icon="pi pi-trash"
                  class="p-button-rounded p-button-text p-button-danger p-button-sm"
                  title="Eliminar JRV"
                  [disabled]="eliminando()"
                  (click)="eliminarJrv(jrv)"
                ></button>
              </td>
            </tr>
          </ng-template>
        </p-table>
        <p-message
          *ngIf="!cargando() && jrvs.length === 0"
          severity="info"
          text="No hay JRVs disponibles en esta parroquia."
        ></p-message>
      </div>

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
          label="Asignar seleccionadas"
          icon="pi pi-check"
          [loading]="asignando()"
          [disabled]="jrvsSeleccionadas.length === 0 || asignando()"
          (click)="guardar()"
        ></button>
      </ng-template>
    </p-dialog>

    <app-confirm-dialog
      #confirmDialog
      (confirm$)="ejecutarEliminacionConfirmada()"
    ></app-confirm-dialog>
  `,
  styles: `
    .table-wrapper {
      margin: 1rem 0;
    }

    .generacion-panel {
      border: 1px solid var(--surface-border);
      border-radius: 0.75rem;
      padding: 1rem;
      margin-bottom: 1.5rem;
    }

    .generacion-panel h2 {
      margin: 0 0 0.5rem;
      font-size: 1rem;
    }

    .generacion-campos {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      gap: 0.75rem;
      margin-top: 1rem;
    }

    .generacion-campos label {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .generacion-campos input {
      min-width: 8rem;
      padding: 0.5rem;
      border: 1px solid var(--surface-border);
      border-radius: 0.375rem;
    }

    .tag-asignada {
      padding: 0.2rem 0.5rem;
      border-radius: 0.25rem;
      font-size: 0.8rem;
      background-color: #e0f2fe;
      color: #0369a1;
      font-weight: 500;
    }

    .tag-disponible {
      padding: 0.2rem 0.5rem;
      border-radius: 0.25rem;
      font-size: 0.8rem;
      background-color: #f1f5f9;
      color: #475569;
      font-weight: 500;
    }
  `,
})
export class RecintoJrvsModalComponent implements OnChanges {
  @Input() abierto = false;
  @Input() recinto: RecintoResponse | null = null;
  @Output() guardado = new EventEmitter<void>();
  @Output() cancelado = new EventEmitter<void>();
  @Output() jrvsModificadas = new EventEmitter<void>();

  @ViewChild('confirmDialog') confirmDialog?: ConfirmDialogComponent;

  private readonly recintoApi = inject(RecintoApi);
  private readonly geografiaApi = inject(GeografiaApi);

  readonly error = signal<string | null>(null);
  readonly enviando = signal(false);
  readonly asignando = signal(false);
  readonly eliminando = signal(false);
  readonly cargando = signal(false);
  readonly mensaje = signal<string | null>(null);

  jrvs: Jrv[] = [];
  jrvsSeleccionadas: Jrv[] = [];
  private idsAEliminarPendientes: number[] = [];
  private esEliminacionIndividual = false;
  private mensajeExitoEliminacion = '';
  generacion: GenerarJrvsRangoRequest = {
    desde: 1,
    hasta: 1,
    sufijoGenero: '',
    electoresPorMesa: 350,
  };

  get hayJrvsDisponibles(): boolean {
    return this.jrvs.some((jrv) => !jrv.asignada);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['abierto'] || changes['recinto']) && this.abierto && this.recinto) {
      this.cargarJrvs();
    }
  }

  private cargarJrvs(): void {
    if (!this.recinto) {
      return;
    }

    this.cargando.set(true);
    this.error.set(null);
    this.geografiaApi.obtenerGeografia().subscribe({
      next: (geografia) => {
        const parroquia = geografia.provincias
          .flatMap((provincia) => provincia.cantones)
          .flatMap((canton) => canton.parroquias)
          .find((item) => item.id === this.recinto?.parroquiaId);
        this.jrvs = (parroquia?.jrvs ?? [])
          .filter(
            (jrv) => jrv.recintoId == null || jrv.recintoId === this.recinto?.id,
          )
          .map((jrv) => ({
            id: jrv.id,
            numero: jrv.numero,
            totalElectores: jrv.totalElectores ?? 0,
            recintoId: jrv.recintoId ?? null,
            asignada: jrv.recintoId === this.recinto?.id,
          }));
        this.jrvsSeleccionadas = [];
        this.cargando.set(false);
        this.actualizarSiguienteNumero();
      },
      error: (err: Error) => {
        this.error.set(err.message || 'No se pudieron cargar las JRVs');
        this.cargando.set(false);
      },
    });
  }

  generarJrvs(): void {
    const { desde, hasta, electoresPorMesa } = this.generacion;
    if (
      !this.recinto ||
      !Number.isInteger(desde) ||
      !Number.isInteger(hasta) ||
      !Number.isInteger(electoresPorMesa) ||
      desde < 1 ||
      hasta < desde ||
      electoresPorMesa < 1
    ) {
      this.error.set('Ingresa un rango válido y electores por mesa mayor que cero');
      return;
    }

    this.error.set(null);
    this.mensaje.set(null);
    this.enviando.set(true);
    this.recintoApi.generarJrvsPorRango(this.recinto.id, this.generacion).subscribe({
      next: (generadas) => {
        const nuevas = generadas.map((jrv) => this.aJrvAsignada(jrv));
        const porId = new Map(this.jrvs.map((jrv) => [jrv.id, jrv]));
        nuevas.forEach((jrv) => porId.set(jrv.id, jrv));
        this.jrvs = Array.from(porId.values());
        this.jrvsSeleccionadas = this.jrvsSeleccionadas.filter(
          (jrv) => !nuevas.some((generada) => generada.id === jrv.id),
        );
        this.mensaje.set(`${nuevas.length} JRV(s) generada(s) correctamente`);
        this.enviando.set(false);
        this.actualizarSiguienteNumero();
      },
      error: (err: Error) => {
        this.error.set(err.message || 'No se pudieron generar las JRVs');
        this.enviando.set(false);
      },
    });
  }

  guardar(): void {
    if (!this.recinto || this.jrvsSeleccionadas.length === 0) {
      this.error.set('Selecciona al menos una JRV disponible');
      return;
    }

    this.error.set(null);
    this.mensaje.set(null);
    this.asignando.set(true);
    const jrvIds = this.jrvsSeleccionadas.map((jrv) => jrv.id);
    this.recintoApi.asignarJrvsAlRecinto(this.recinto.id, jrvIds).subscribe({
      next: () => {
        this.jrvs = this.jrvs.map((jrv) =>
          jrvIds.includes(jrv.id)
            ? { ...jrv, asignada: true, recintoId: this.recinto!.id }
            : jrv,
        );
        this.jrvsSeleccionadas = [];
        this.asignando.set(false);
        this.mensaje.set('JRVs asignadas correctamente');
        this.guardado.emit();
      },
      error: (err: Error) => {
        this.error.set(err.message || 'No se pudieron asignar las JRVs');
        this.asignando.set(false);
      },
    });
  }

  eliminarJrv(jrv: Jrv): void {
    this.idsAEliminarPendientes = [jrv.id];
    this.esEliminacionIndividual = true;
    this.mensajeExitoEliminacion = `Mesa ${jrv.numero} eliminada correctamente`;
    this.confirmDialog?.open({
      title: 'Eliminar JRV',
      message: `¿Estás seguro de eliminar la mesa ${jrv.numero}? Esta acción no se puede deshacer.`,
      variant: 'danger',
    });
  }

  eliminarSeleccionadas(): void {
    if (this.jrvsSeleccionadas.length === 0) {
      return;
    }

    const cantidad = this.jrvsSeleccionadas.length;
    this.idsAEliminarPendientes = this.jrvsSeleccionadas.map((j) => j.id);
    this.esEliminacionIndividual = false;
    this.mensajeExitoEliminacion = `${cantidad} mesa(s) eliminada(s) correctamente`;
    this.confirmDialog?.open({
      title: 'Eliminar JRVs seleccionadas',
      message: `¿Estás seguro de eliminar las ${cantidad} mesas seleccionadas? Esta acción no se puede deshacer.`,
      variant: 'danger',
    });
  }

  ejecutarEliminacionConfirmada(): void {
    if (this.idsAEliminarPendientes.length === 0) {
      return;
    }

    this.error.set(null);
    this.mensaje.set(null);
    this.eliminando.set(true);

    const idsParaEliminar = [...this.idsAEliminarPendientes];
    const msjExito = this.mensajeExitoEliminacion;
    const esIndividual = this.esEliminacionIndividual;

    const req$ = esIndividual
      ? this.geografiaApi.eliminarJrv(idsParaEliminar[0])
      : this.geografiaApi.eliminarJrvsBatch(idsParaEliminar);

    req$.subscribe({
      next: () => {
        this.jrvs = this.jrvs.filter((item) => !idsParaEliminar.includes(item.id));
        this.jrvsSeleccionadas = this.jrvsSeleccionadas.filter(
          (item) => !idsParaEliminar.includes(item.id),
        );
        this.idsAEliminarPendientes = [];
        this.eliminando.set(false);
        this.mensaje.set(msjExito);
        this.actualizarSiguienteNumero();
        this.jrvsModificadas.emit();
      },
      error: (err: Error) => {
        this.error.set(
          err.message || 'No se pudieron eliminar las mesas (pueden tener actas asociadas)',
        );
        this.idsAEliminarPendientes = [];
        this.eliminando.set(false);
      },
    });
  }

  cancelar(): void {
    this.error.set(null);
    this.mensaje.set(null);
    this.jrvsSeleccionadas = [];
    this.cancelado.emit();
  }

  actualizarSiguienteNumero(): void {
    const sufijo = (this.generacion.sufijoGenero || '').trim().toUpperCase();
    let maxNumero = 0;

    for (const jrv of this.jrvs) {
      const numStr = (jrv.numero || '').toUpperCase();
      // Casos comunes: JRV-001, JRV-M-001, JRV-F-001, JRV-001-M, etc.
      if (sufijo.length > 0) {
        if (!numStr.includes(`-${sufijo}-`) && !numStr.includes(`-${sufijo}`) && !numStr.includes(`${sufijo}-`)) {
          continue;
        }
      }
      const matches = numStr.match(/(\d+)/g);
      if (matches && matches.length > 0) {
        const ultimoNumero = parseInt(matches[matches.length - 1], 10);
        if (!isNaN(ultimoNumero) && ultimoNumero > maxNumero) {
          maxNumero = ultimoNumero;
        }
      }
    }

    const siguiente = maxNumero > 0 ? maxNumero + 1 : 1;
    this.generacion = {
      ...this.generacion,
      desde: siguiente,
      hasta: siguiente,
    };
  }

  private aJrvAsignada(jrv: JrvResponse): Jrv {
    return {
      id: jrv.id,
      numero: jrv.numero,
      totalElectores: jrv.totalElectores,
      recintoId: jrv.recintoId,
      asignada: jrv.recintoId === this.recinto?.id,
    };
  }
}
