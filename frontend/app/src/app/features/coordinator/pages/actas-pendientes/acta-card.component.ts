import { CommonModule } from '@angular/common';
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { ButtonComponent } from '../../../../shared/ui';

export interface ActaSummary {
  id: number;
  jrvNumero: string;
  cargo: string;
  observer: string;
  estado: 'ENVIADA' | 'APROBADA' | 'EN_DISPUTA' | 'CORREGIDA';
  fechaRegistro: string;
  totalMesas?: number;
}

@Component({
  selector: 'app-acta-card',
  standalone: true,
  imports: [CommonModule, ButtonComponent],
  template: `
    <div class="acta-card" [class]="'estado-' + acta.estado.toLowerCase()">
      <div class="card-header">
        <span class="estado-badge">{{ acta.estado }}</span>
        <span class="fecha">{{ acta.fechaRegistro | date: 'short' }}</span>
      </div>

      <div class="card-body">
        <div class="item">
          <span class="label">JRV:</span>
          <span class="value">{{ acta.jrvNumero }}</span>
        </div>
        <div class="item">
          <span class="label">Cargo:</span>
          <span class="value">{{ acta.cargo }}</span>
        </div>
        <div class="item">
          <span class="label">Observador:</span>
          <span class="value">{{ acta.observer }}</span>
        </div>
        <div class="item" *ngIf="acta.totalMesas">
          <span class="label">Mesas:</span>
          <span class="value">{{ acta.totalMesas }}</span>
        </div>
      </div>

      <div class="card-footer">
        <button
          pButton
          type="button"
          label="Ver Detalle"
          icon="pi pi-eye"
          class="p-button-sm p-button-outlined"
          (click)="onVerDetalle()"
        ></button>
      </div>
    </div>
  `,
  styles: `
    .acta-card {
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 0.5rem;
      padding: 1rem;
      display: flex;
      flex-direction: column;
      height: 100%;
      transition: all 0.3s ease;

      &:hover {
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
        border-color: #ccc;
      }

      &.estado-enviada {
        border-left: 4px solid #ff9800;
      }

      &.estado-aprobada {
        border-left: 4px solid #4caf50;
      }

      &.estado-en_disputa {
        border-left: 4px solid #f44336;
      }

      &.estado-corregida {
        border-left: 4px solid #2196f3;
      }
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
      border-bottom: 1px solid #f0f0f0;
      padding-bottom: 0.75rem;

      .estado-badge {
        padding: 0.25rem 0.75rem;
        border-radius: 0.25rem;
        font-size: 0.75rem;
        font-weight: 600;
        text-transform: uppercase;
        background-color: #f5f5f5;
      }

      .fecha {
        font-size: 0.75rem;
        color: #999;
      }
    }

    .card-body {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin-bottom: 1rem;

      .item {
        display: flex;
        justify-content: space-between;

        .label {
          font-weight: 500;
          font-size: 0.875rem;
          color: #666;
        }

        .value {
          font-size: 0.875rem;
          color: #333;
          text-align: right;
          flex: 1;
          margin-left: 0.5rem;
        }
      }
    }

    .card-footer {
      display: flex;
      gap: 0.5rem;
      justify-content: flex-end;
    }
  `,
})
export class ActaCardComponent {
  @Input() acta!: ActaSummary;
  @Output() verDetalle = new EventEmitter<ActaSummary>();

  onVerDetalle(): void {
    this.verDetalle.emit(this.acta);
  }
}
