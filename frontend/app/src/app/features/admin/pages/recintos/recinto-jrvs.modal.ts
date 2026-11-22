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
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { TableModule } from 'primeng/table';
import { Message } from 'primeng/message';
import { ButtonModule } from 'primeng/button';
import { RecintoResponse } from '../../data/recinto.api';

interface Jrv {
  id: number;
  numero: string;
  cargo: string;
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

      <p class="text-sm text-gray-600 mb-4">
        Selecciona las JRVs que deseas asignar al recinto
        <strong>{{ recinto?.nombre }}</strong>
      </p>

      <div class="table-wrapper">
        <p-table
          [value]="jrvs"
          dataKey="id"
          responsiveLayout="scroll"
          styleClass="app-table"
        >
          <ng-template pTemplate="header">
            <tr>
              <th style="width: 3rem">
                <p-tableHeaderCheckbox></p-tableHeaderCheckbox>
              </th>
              <th pSortableColumn="numero">
                Número <p-sortIcon field="numero"></p-sortIcon>
              </th>
              <th pSortableColumn="cargo">
                Cargo <p-sortIcon field="cargo"></p-sortIcon>
              </th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-jrv>
            <tr>
              <td>
                <p-tableCheckbox [value]="jrv"></p-tableCheckbox>
              </td>
              <td>{{ jrv.numero }}</td>
              <td>{{ jrv.cargo }}</td>
            </tr>
          </ng-template>
        </p-table>
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
          label="Guardar"
          icon="pi pi-check"
          [loading]="enviando()"
          (click)="guardar()"
        ></button>
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .table-wrapper {
      margin: 1rem 0;
    }
  `,
})
export class RecintoJrvsModalComponent implements OnInit {
  @Input() abierto = false;
  @Input() recinto: RecintoResponse | null = null;
  @Output() guardado = new EventEmitter<void>();
  @Output() cancelado = new EventEmitter<void>();

  readonly error = signal<string | null>(null);
  readonly enviando = signal(false);

  jrvs: Jrv[] = [];
  jrvsSeleccionadas: Jrv[] = [];

  ngOnInit(): void {
    this.cargarJrvs();
  }

  private cargarJrvs(): void {
    // TODO: Fetch JRVs from API based on recinto?.parroquiaId
    this.jrvs = [
      {
        id: 1,
        numero: 'JRV-001',
        cargo: 'Presidente',
        asignada: false,
      },
      {
        id: 2,
        numero: 'JRV-002',
        cargo: 'Vocal',
        asignada: false,
      },
      {
        id: 3,
        numero: 'JRV-003',
        cargo: 'Secretario',
        asignada: false,
      },
    ];
  }

  guardar(): void {
    this.error.set(null);
    this.enviando.set(true);

    // TODO: Call API to assign JRVs to recinto
    setTimeout(() => {
      this.enviando.set(false);
      this.guardado.emit();
    }, 500);
  }

  cancelar(): void {
    this.error.set(null);
    this.jrvsSeleccionadas = [];
    this.cancelado.emit();
  }
}
