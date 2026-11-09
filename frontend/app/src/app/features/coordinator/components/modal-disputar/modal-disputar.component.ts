import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Textarea } from 'primeng/textarea';
import { ButtonComponent } from '../../../../shared/ui';
import { DisputarActaPayload } from '../../domain/models/coordinator.model';

/**
 * Modal component for disputing an acta (acta de escrutinio).
 *
 * Extracted from acta-detalle.page.ts for reuse in both list and detail views.
 *
 * **Responsibilities:**
 * - Present a form for the user to enter a dispute reason
 * - Validate that the reason is not empty
 * - Emit events on confirm/cancel
 * - Display errors from the API
 *
 * **Parent responsibilities:**
 * - Provide actaId and open/close the modal via @Input/@Output
 * - Call coordinatorService.disputarActa and handle response
 * - Refresh data on success
 */
@Component({
  selector: 'app-modal-disputar',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Dialog,
    InputText,
    Message,
    Textarea,
    ButtonComponent,
  ],
  templateUrl: './modal-disputar.component.html',
  styleUrls: ['./modal-disputar.component.scss'],
})
export class ModalDisputarComponent {
  @Input() visible = false;
  @Input() isSubmitting = false;
  @Input() error: string | null = null;

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() confirm = new EventEmitter<DisputarActaPayload>();

  /** Local form state */
  motivoDisputa = signal('');
  errorDisputa = signal<string | null>(null);

  onVisibleChange(next: boolean): void {
    if (!next) {
      // Reset form when closing
      this.motivoDisputa.set('');
      this.errorDisputa.set(null);
    }
    this.visibleChange.emit(next);
  }

  confirmarDisputa(): void {
    const motivo = this.motivoDisputa().trim();
    if (!motivo) {
      this.errorDisputa.set('El motivo de la disputa es obligatorio.');
      return;
    }

    this.errorDisputa.set(null);
    this.confirm.emit({ motivo });
  }

  get isDisabledConfirm(): boolean {
    return !this.motivoDisputa().trim() || this.isSubmitting;
  }
}
