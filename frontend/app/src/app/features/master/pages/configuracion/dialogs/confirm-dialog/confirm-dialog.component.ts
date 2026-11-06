import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';

/**
 * T-FE-16 Confirm Dialog — Diálogo de confirmación reutilizable para eliminación.
 *
 * Uso:
 * this.dialogService.open(ConfirmDialogComponent, {
 *   header: 'Eliminar Cargo',
 *   data: { message: '¿Estás seguro de que deseas eliminar "Presidente"?' }
 * });
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="confirm-dialog"
      role="alertdialog"
      aria-labelledby="confirm-title"
    >
      <p id="confirm-title" class="confirm-message">{{ data?.message }}</p>
      <div class="dialog-actions">
        <button
          class="btn-cancel"
          (click)="onCancel()"
          aria-label="Cancelar"
          data-testid="btn-cancel"
        >
          Cancelar
        </button>
        <button
          class="btn-delete"
          (click)="onConfirm()"
          aria-label="Eliminar"
          data-testid="btn-confirm-delete"
        >
          Eliminar
        </button>
      </div>
    </div>
  `,
  styles: `
    .confirm-dialog {
      padding: 1.5rem;

      .confirm-message {
        font-size: 1rem;
        color: var(--color-text-primary);
        margin: 0 0 1.5rem 0;
        line-height: 1.5;
      }

      .dialog-actions {
        display: flex;
        gap: 1rem;
        justify-content: flex-end;

        button {
          padding: 0.5rem 1.5rem;
          border: none;
          border-radius: 0.375rem;
          font-weight: 600;
          cursor: pointer;
          transition: background 0.2s ease;

          &.btn-cancel {
            background: var(--color-surface-raised);
            color: var(--color-text-primary);

            &:hover {
              background: var(--color-border-subtle);
            }
          }

          &.btn-delete {
            background: var(--color-status-danger);
            color: white;

            &:hover {
              opacity: 0.9;
            }

            &:active {
              opacity: 0.8;
            }
          }
        }
      }
    }
  `,
})
export class ConfirmDialogComponent {
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  data = this.config.data;

  onCancel(): void {
    this.dialogRef.close(false);
  }

  onConfirm(): void {
    this.dialogRef.close(true);
  }
}
