import { CommonModule } from '@angular/common';
import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  inject,
  signal,
} from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { TableModule } from 'primeng/table';
import { Message } from 'primeng/message';
import { ButtonComponent } from '../../../../shared/ui';
import { CoordinatorApi } from '../../data/coordinator.api';

export interface ActaSummary {
  id: number;
  jrvNumero: string;
  cargo: string;
  observer: string;
  estado: 'ENVIADA' | 'APROBADA' | 'EN_DISPUTA' | 'CORREGIDA';
  fechaRegistro: string;
}

interface ActaDetalle {
  id: number;
  urlEvidencia?: string;
  totalSufragantes?: number;
  votosBlancos?: number;
  votosNulos?: number;
  candidatos?: Array<{
    nombre: string;
    orden: number;
    votos: number;
  }>;
}

@Component({
  selector: 'app-acta-detalle-modal',
  standalone: true,
  imports: [CommonModule, Dialog, TableModule, Message, ButtonComponent],
  template: `
    <p-dialog
      [(visible)]="abierto"
      header="Detalle de Acta"
      [modal]="true"
      [closable]="true"
      (onHide)="cerrar()"
      [style]="{ width: '90vw' }"
      [maximizable]="true"
    >
      <div *ngIf="!cargando() && detalle()" class="detalle-content">
        <!-- Foto del acta -->
        <div class="foto-section" *ngIf="detalle()?.urlEvidencia">
          <img
            [src]="detalle()!.urlEvidencia"
            alt="Acta"
            class="acta-foto"
            (error)="onEvidenceError()"
          />
        </div>

        <!-- Datos principales -->
        <div class="datos-section">
          <div class="dato-item">
            <span class="label">JRV:</span>
            <span class="value">{{ acta?.jrvNumero }}</span>
          </div>
          <div class="dato-item">
            <span class="label">Cargo:</span>
            <span class="value">{{ acta?.cargo }}</span>
          </div>
          <div class="dato-item">
            <span class="label">Observador:</span>
            <span class="value">{{ acta?.observer }}</span>
          </div>
          <div class="dato-item">
            <span class="label">Estado:</span>
            <span class="value">{{ acta?.estado }}</span>
          </div>
        </div>

        <!-- Tabla de votos -->
        <div
          class="votos-section"
          *ngIf="detalle()?.totalSufragantes !== undefined"
        >
          <h3>Resumen de Votos</h3>
          <p-table [value]="[detalle()!]" responsiveLayout="scroll">
            <ng-template pTemplate="header">
              <tr>
                <th>Sufragantes</th>
                <th>Blancos</th>
                <th>Nulos</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-data>
              <tr>
                <td>{{ data.totalSufragantes }}</td>
                <td>{{ data.votosBlancos }}</td>
                <td>{{ data.votosNulos }}</td>
              </tr>
            </ng-template>
          </p-table>
        </div>

        <!-- Tabla de candidatos -->
        <div
          class="candidatos-section"
          *ngIf="detalle()?.candidatos && detalle()!.candidatos.length > 0"
        >
          <h3>Votos por Candidato</h3>
          <p-table [value]="detalle()!.candidatos" responsiveLayout="scroll">
            <ng-template pTemplate="header">
              <tr>
                <th>Orden</th>
                <th>Nombre</th>
                <th>Votos</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-candidato>
              <tr>
                <td>{{ candidato.orden }}</td>
                <td>{{ candidato.nombre }}</td>
                <td>{{ candidato.votos }}</td>
              </tr>
            </ng-template>
          </p-table>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <button
          pButton
          type="button"
          label="Cerrar"
          icon="pi pi-times"
          class="p-button-text"
          (click)="cerrar()"
        ></button>
        <button
          pButton
          type="button"
          label="Aprobar"
          icon="pi pi-check"
          class="p-button-success"
          [loading]="submitting()"
          (click)="onAprobar()"
          *ngIf="acta?.estado === 'ENVIADA'"
        ></button>
        <button
          pButton
          type="button"
          label="Rechazar"
          icon="pi pi-times"
          class="p-button-danger"
          [loading]="submitting()"
          (click)="onRechazar()"
          *ngIf="acta?.estado === 'ENVIADA'"
        ></button>
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .detalle-content {
      display: flex;
      flex-direction: column;
      gap: 2rem;

      .foto-section {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 300px;
        background: var(--color-surface-raised);
        border-radius: 0.5rem;

        .acta-foto {
          max-width: 100%;
          max-height: 500px;
          object-fit: contain;
        }
      }

      .datos-section {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 1rem;

        .dato-item {
          display: flex;
          flex-direction: column;
          gap: 0.25rem;

          .label {
            font-weight: 500;
            font-size: 0.875rem;
            color: var(--color-text-muted);
          }

          .value {
            font-size: 0.875rem;
            color: var(--color-text-primary);
          }
        }
      }

      .votos-section,
      .candidatos-section {
        h3 {
          margin: 0 0 1rem 0;
          font-size: 1rem;
          color: var(--color-text-primary);
        }
      }
    }
  `,
})
export class ActaDetalleModalComponent implements OnChanges {
  @Input() abierto = false;
  @Input() acta: ActaSummary | null = null;
  @Input() submitting = signal(false);
  @Output() aprobar = new EventEmitter<string | undefined>();
  @Output() rechazar = new EventEmitter<string>();
  @Output() cerrado = new EventEmitter<void>();

  private readonly api = inject(CoordinatorApi);

  readonly cargando = signal(false);
  readonly detalle = signal<ActaDetalle | null>(null);

  ngOnChanges(): void {
    if (this.abierto && this.acta && !this.detalle()) {
      this.cargarDetalle();
    }
  }

  private cargarDetalle(): void {
    if (!this.acta) return;

    this.cargando.set(true);
    this.api.obtenerActaDetalle(this.acta.id).subscribe({
      next: (detalle) => {
        this.detalle.set(detalle as ActaDetalle);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error loading detalle:', err);
        this.cargando.set(false);
      },
    });
  }

  private evidenceReloadAttempted = false;

  /**
   * La URL prefirmada de la evidencia caduca (15 min). Si la imagen falla, se pide el detalle
   * de nuevo una sola vez y se actualiza solo la URL, sin mostrar el estado de carga.
   */
  onEvidenceError(): void {
    if (this.evidenceReloadAttempted || !this.acta) return;
    this.evidenceReloadAttempted = true;
    this.api.obtenerActaDetalle(this.acta.id).subscribe({
      next: (detalle) => {
        const actual = this.detalle();
        if (actual) {
          this.detalle.set({
            ...actual,
            urlEvidencia: (detalle as ActaDetalle).urlEvidencia,
          });
        }
      },
      error: (err) =>
        console.error('No se pudo renovar la URL de la evidencia:', err),
    });
  }

  onAprobar(): void {
    this.aprobar.emit(undefined);
  }

  onRechazar(): void {
    this.rechazar.emit('');
  }

  cerrar(): void {
    this.detalle.set(null);
    this.evidenceReloadAttempted = false;
    this.cerrado.emit();
  }
}
