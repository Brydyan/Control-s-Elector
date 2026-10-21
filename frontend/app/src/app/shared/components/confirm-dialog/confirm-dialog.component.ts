import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { ButtonComponent } from '../../ui/button/button.component';

/**
 * T-FE-12 S3: reusable confirmation dialog.
 *
 * Replaces `window.confirm()` with a dialog that:
 * - emits (confirm) / (cancel) instead of returning a boolean
 * - supports the 'danger' variant for irreversible actions
 *
 * The dialog is a `p-dialog`, which brings the focus trap, the escape
 * handling and the overlay behaviour.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [Dialog, ButtonComponent],
  template: `
    <p-dialog
      [visible]="visible"
      (visibleChange)="onVisibleChange($event)"
      [modal]="true"
      [draggable]="false"
      [resizable]="false"
      [closable]="true"
      [style]="{ width: '26rem' }"
      [header]="title"
    >
      @if (message) {
      <p class="message">{{ message }}</p>
      }
      <ng-template #footer>
        <app-button
          variant="secondary"
          (clicked)="onCancel()"
          data-testid="confirm-cancel"
          >{{ cancelLabel }}</app-button
        >
        <app-button
          [variant]="variant"
          (clicked)="onConfirm()"
          data-testid="confirm-accept"
          >{{ confirmLabel }}</app-button
        >
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .message {
      margin: 0;
      color: var(--p-text-muted-color);
    }
  `,
})
export class ConfirmDialogComponent {
  @Input() visible = false;
  @Input() title = '¿Confirma?';
  @Input() message: string | null = null;
  @Input() confirmLabel = 'Confirmar';
  @Input() cancelLabel = 'Cancelar';
  @Input() variant: 'primary' | 'secondary' | 'danger' = 'primary';

  @Output() confirm$ = new EventEmitter<void>();
  @Output() cancel$ = new EventEmitter<void>();

  /**
   * Set while the dialog closes because it was confirmed, so the resulting
   * `visibleChange` does not also report a cancellation.
   */
  private confirmed = false;

  /** Programmatic API — spec S3: `ref.open({title, message})`. */
  open(opts: {
    title: string;
    message?: string;
    variant?: 'primary' | 'danger';
  }): void {
    this.title = opts.title;
    this.message = opts.message ?? null;
    if (opts.variant) this.variant = opts.variant;
    this.confirmed = false;
    this.visible = true;
  }

  close(): void {
    this.visible = false;
  }

  onConfirm(): void {
    this.confirmed = true;
    this.confirm$.emit();
    this.close();
  }

  onCancel(): void {
    if (this.confirmed) {
      this.confirmed = false;
      return;
    }
    this.cancel$.emit();
    this.close();
  }

  onVisibleChange(next: boolean): void {
    this.visible = next;
    if (!next) {
      this.onCancel();
    }
  }
}
