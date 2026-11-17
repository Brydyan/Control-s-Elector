import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  Input,
  Output,
  computed,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { ButtonComponent } from '../../../../shared/ui';
import {
  CandidatoResumen,
  CorregirActaPayload,
} from '../../domain/models/coordinator.model';

/**
 * Modal component for correcting an acta (acta de escrutinio).
 *
 * Extracted from acta-detalle.page.ts for reuse in both list and detail views.
 *
 * **Responsibilities:**
 * - Present a form for correcting vote counts (sufragantes, blancos, nulos, candidatos)
 * - Calculate and validate checksum (sum must equal total sufragantes)
 * - Emit events on confirm/cancel
 * - Display validation errors and API errors
 *
 * **Parent responsibilities:**
 * - Provide candidatos, jrvId, cargoId and initial acta data via @Input
 * - Call coordinatorService.corregirActa and handle response
 * - Refresh data on success
 *
 * **Checksum validation:**
 * The sum of (candidate votes + blank votes + null votes) must equal totalSufragantes.
 * If not, the confirm button is disabled and a message is shown.
 */
@Component({
  selector: 'app-modal-corregir',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Dialog,
    InputText,
    Message,
    ButtonComponent,
  ],
  templateUrl: './modal-corregir.component.html',
  styleUrls: ['./modal-corregir.component.scss'],
})
export class ModalCorregirComponent {
  @Input() visible = false;
  @Input() isSubmitting = false;
  @Input() error: string | null = null;
  @Input() candidatos: CandidatoResumen[] = [];
  @Input() jrvId = 0;
  @Input() cargoId = 0;

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() confirm = new EventEmitter<CorregirActaPayload>();

  /** Local form state — use signals for reactive validation */
  correcionSufragantes = signal(0);
  correcionBlancos = signal(0);
  correcionNulos = signal(0);
  votosCorregidosMap = signal<Map<number, number>>(new Map());
  errorCorregir = signal<string | null>(null);

  /** Computed properties for checksum validation */
  sumaVotosCorregidos = computed(() => {
    let total = 0;
    this.votosCorregidosMap().forEach((v) => {
      total += v;
    });
    return (
      total + (this.correcionBlancos() || 0) + (this.correcionNulos() || 0)
    );
  });

  diferenciaChecksumCorregida = computed(() => {
    return this.sumaVotosCorregidos() - (this.correcionSufragantes() || 0);
  });

  isChecksumCorregidoValido = computed(() => {
    return (
      this.correcionSufragantes() > 0 &&
      this.sumaVotosCorregidos() === this.correcionSufragantes()
    );
  });

  onVisibleChange(next: boolean): void {
    if (!next) {
      // Reset form when closing
      this.correcionSufragantes.set(0);
      this.correcionBlancos.set(0);
      this.correcionNulos.set(0);
      this.votosCorregidosMap.set(new Map());
      this.errorCorregir.set(null);
    }
    this.visibleChange.emit(next);
  }

  /**
   * Initialize the form with existing acta data.
   * Should be called by the parent before opening the modal.
   */
  initializeForm(data: {
    totalSufragantes: number;
    votosBlancos: number;
    votosNulos: number;
    votesPerCandidate: Map<number, number>;
  }): void {
    this.correcionSufragantes.set(data.totalSufragantes);
    this.correcionBlancos.set(data.votosBlancos);
    this.correcionNulos.set(data.votosNulos);
    this.votosCorregidosMap.set(new Map(data.votesPerCandidate));
    this.errorCorregir.set(null);
  }

  setVotoCorregido(candidatoId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = Math.max(0, parseInt(input.value, 10) || 0);
    const map = new Map(this.votosCorregidosMap());
    map.set(candidatoId, value);
    this.votosCorregidosMap.set(map);
  }

  confirmarCorrecion(): void {
    if (!this.isChecksumCorregidoValido()) {
      this.errorCorregir.set(
        'La suma de votos corregidos (candidatos + blancos + nulos) debe ser exactamente igual al total de sufragantes.',
      );
      return;
    }

    const detalles = Array.from(this.votosCorregidosMap().entries()).map(
      ([candidatoId, votos]) => ({
        candidatoId,
        votos,
      }),
    );

    const payload: CorregirActaPayload = {
      jrvId: this.jrvId,
      cargoId: this.cargoId,
      totalSufragantes: this.correcionSufragantes(),
      votosBlancos: this.correcionBlancos(),
      votosNulos: this.correcionNulos(),
      detalles,
    };

    this.errorCorregir.set(null);
    this.confirm.emit(payload);
  }

  get isDisabledConfirm(): boolean {
    return !this.isChecksumCorregidoValido() || this.isSubmitting;
  }
}
