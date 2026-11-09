import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputTextarea } from 'primeng/inputtextarea';
import { Message } from 'primeng/message';
import { ButtonComponent } from '../../../../shared/ui';

/**
 * T-FE-10: Modal for annulling an acta.
 *
 * Dangerous action that requires a reason (motivo). Styled with red warning
 * to indicate irreversible nature. Validates that motivo is provided and not
 * empty.
 *
 * **Responsibilities:**
 * - Present a form for the user to enter an annulment reason
 * - Validate that the reason is not empty
 * - Emit events on confirm/cancel
 * - Display errors from the API
 *
 * **Parent responsibilities:**
 * - Provide actaId and open/close the modal via @Input/@Output
 * - Call coordinatorService.anularActa and handle response
 * - Refresh data on success
 *
 * Usage:
 * ```
 * <app-modal-anular
 *   [visible]="modalAnularAbierto"
 *   [isSubmitting]="submitting()"
 *   [error]="errorAnular"
 *   (visibleChange)="modalAnularAbierto = $event"
 *   (confirm)="confirmarAnular($event)"
 * />
 * ```
 */
@Component({
  selector: 'app-modal-anular',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Dialog,
    InputTextarea,
    Message,
    ButtonComponent,
  ],
  templateUrl: './modal-anular.component.html',
  styleUrls: ['./modal-anular.component.scss'],
})
export class ModalAnularComponent {
  @Input() visible = false;
  @Input() isSubmitting = false;
  @Input() error: string | null = null;

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() confirm = new EventEmitter<string>();

  /** Local form state */
  motivoAnular = signal('');

  onVisibleChange(next: boolean): void {
    if (!next) {
      // Reset form when closing
      this.motivoAnular.set('');
    }
    this.visibleChange.emit(next);
  }

  confirmarAnular(): void {
    const motivo = this.motivoAnular().trim();
    if (!motivo) {
      return;
    }

    this.confirm.emit(motivo);
  }

  get isDisabledConfirm(): boolean {
    return !this.motivoAnular().trim() || this.isSubmitting;
  }
}
