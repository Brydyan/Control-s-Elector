import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  Input,
  Output,
  ViewChild,
  ElementRef,
  inject,
} from '@angular/core';
import { ButtonComponent } from '../../ui/button/button.component';
import { FocusTrapDirective } from '../../a11y/focus-trap.directive';

/**
 * T-FE-12 S3: modal de confirmación reutilizable.
 *
 * Reemplaza `window.confirm()` con un modal Ionic-style que:
 * - emite (confirm) / (cancel) en vez de retornar boolean
 * - aplica FocusTrapDirective para accesibilidad
 * - soporta variant 'danger' para acciones irreversibles (anular, aprobar con fraude)
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, ButtonComponent, FocusTrapDirective],
  template: `
    @if (visible) {
      <div
        class="modal-backdrop"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
      >
        <div class="modal-card" [appFocusTrap]="true">
          <h2 id="confirm-title">{{ title }}</h2>
          @if (message) {
            <p class="message">{{ message }}</p>
          }
          <div class="actions">
            <app-button
              variant="secondary"
              (click)="onCancel()"
              data-testid="confirm-cancel"
              >{{ cancelLabel }}</app-button
            >
            <app-button
              [variant]="variant"
              (click)="onConfirm()"
              data-testid="confirm-accept"
              >{{ confirmLabel }}</app-button
            >
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .modal-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.6);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
      }
      .modal-card {
        background: #fff;
        padding: 24px;
        border-radius: 12px;
        max-width: 400px;
        width: 90%;
      }
      .actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        margin-top: 16px;
      }
    `,
  ],
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

  /** Programmatic API — spec S3: `ref.open({title, message})`. */
  open(opts: {
    title: string;
    message?: string;
    variant?: 'primary' | 'danger';
  }): void {
    this.title = opts.title;
    this.message = opts.message ?? null;
    if (opts.variant) this.variant = opts.variant;
    this.visible = true;
  }

  close(): void {
    this.visible = false;
  }

  onConfirm(): void {
    this.confirm$.emit();
    this.close();
  }

  onCancel(): void {
    this.cancel$.emit();
    this.close();
  }
}
