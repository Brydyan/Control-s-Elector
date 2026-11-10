import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Dialog } from 'primeng/dialog';
import { ButtonComponent } from '../../../../shared/ui';

/**
 * T-FE-10: Modal for approving an acta.
 *
 * Simple confirmation dialog with no form fields. Emits confirm when user
 * approves the action.
 *
 * **Responsibilities:**
 * - Present a simple confirmation message
 * - Emit events on confirm/cancel
 *
 * **Parent responsibilities:**
 * - Provide actaId and open/close the modal via @Input/@Output
 * - Call coordinatorService.aprobarActa and handle response
 * - Refresh data on success
 *
 * Usage:
 * ```
 * <app-modal-aprobar
 *   [visible]="confirmAprobarAbierto"
 *   [isSubmitting]="submitting()"
 *   (visibleChange)="confirmAprobarAbierto = $event"
 *   (confirm)="confirmarAprobar()"
 * />
 * ```
 */
@Component({
  selector: 'app-modal-aprobar',
  standalone: true,
  imports: [CommonModule, Dialog, ButtonComponent],
  templateUrl: './modal-aprobar.component.html',
  styleUrls: ['./modal-aprobar.component.scss'],
})
export class ModalAprobarComponent {
  @Input() visible = false;
  @Input() isSubmitting = false;

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() confirm = new EventEmitter<void>();

  onVisibleChange(next: boolean): void {
    this.visibleChange.emit(next);
  }

  confirmarAprobar(): void {
    this.confirm.emit();
  }
}
