import { CommonModule } from '@angular/common';
import { Component, Input, Output, EventEmitter, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { Textarea } from 'primeng/textarea';
import { Message } from 'primeng/message';
import { ButtonComponent } from '../../../../shared/ui';

@Component({
  selector: 'app-rechazo-form-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Dialog,
    Textarea,
    Message,
    ButtonComponent,
  ],
  template: `
    <p-dialog
      [(visible)]="abierto"
      header="Rechazar Acta"
      [modal]="true"
      [closable]="true"
      (onHide)="cancelar()"
      [style]="{ width: '90vw', maxWidth: '500px' }"
    >
      <p-message
        severity="info"
        text="Por favor especifica el motivo del rechazo. El observador recibirá una notificación."
        styleClass="w-full mb-4"
      ></p-message>

      <div class="form-group">
        <label>Motivo del Rechazo *</label>
        <textarea
          pTextarea
          [(ngModel)]="motivo"
          placeholder="Describe por qué rechazas esta acta (máx. 500 caracteres)"
          [maxlength]="500"
          rows="5"
          class="w-full"
        ></textarea>
        <small class="char-counter">{{ motivo.length }}/500 caracteres</small>
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
          label="Rechazar"
          icon="pi pi-check"
          class="p-button-danger"
          [loading]="submitting()"
          [disabled]="motivo.trim().length === 0"
          (click)="onRechazar()"
        ></button>
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;

      label {
        font-weight: 500;
        font-size: 0.875rem;
        color: #333;
      }

      .char-counter {
        font-size: 0.75rem;
        color: #999;
        text-align: right;
      }
    }
  `,
})
export class RechazoFormModalComponent {
  @Input() abierto = false;
  @Input() submitting = signal(false);
  @Output() rechazar = new EventEmitter<string>();
  @Output() cancelado = new EventEmitter<void>();

  motivo = '';

  onRechazar(): void {
    if (this.motivo.trim().length > 0 && this.motivo.length <= 500) {
      this.rechazar.emit(this.motivo.trim());
    }
  }

  cancelar(): void {
    this.motivo = '';
    this.cancelado.emit();
  }
}
