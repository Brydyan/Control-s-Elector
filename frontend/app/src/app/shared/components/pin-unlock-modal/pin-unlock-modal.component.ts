import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonComponent } from '../../ui/button/button.component';
import { PinAuthService } from '../../../core/auth/pin-auth.service';

/**
 * Modal de desbloqueo por PIN local (T-FE-09, A.2, design D1).
 *
 * Uso: cuando el JWT expira y `!navigator.onLine`, el `authGuard`
 * renderiza este modal en lugar de redirigir a `/login`.
 */
@Component({
  selector: 'app-pin-unlock-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonComponent],
  template: `
    <div class="modal-backdrop" role="dialog" aria-modal="true">
      <div class="modal-card">
        <h2>Desbloqueo por PIN</h2>
        <p>
          Sin conexión a internet. Ingresa tu PIN de 4 dígitos para
          continuar.
        </p>
        <input
          type="password"
          inputmode="numeric"
          maxlength="4"
          pattern="[0-9]{4}"
          [(ngModel)]="pin"
          (keyup.enter)="onSubmit()"
          aria-label="PIN de 4 dígitos"
          autocomplete="off"
          data-testid="pin-input"
        />
        @if (errorMessage) {
          <p class="error" role="alert" data-testid="pin-error">
            {{ errorMessage }}
          </p>
        }
        <div class="actions">
          <app-button
            label="Desbloquear"
            [disabled]="pin.length !== 4"
            (click)="onSubmit()"
            data-testid="pin-submit"
          ></app-button>
        </div>
      </div>
    </div>
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
        max-width: 360px;
        width: 90%;
      }
      input {
        display: block;
        width: 100%;
        font-size: 24px;
        letter-spacing: 8px;
        text-align: center;
        padding: 12px;
        margin: 16px 0;
      }
      .error {
        color: #c0392b;
        margin: 8px 0;
      }
    `,
  ],
})
export class PinUnlockModalComponent {
  private readonly pinAuth = inject(PinAuthService);

  @Input() visible = true;
  @Output() unlocked = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  pin = '';
  errorMessage: string | null = null;

  async onSubmit(): Promise<void> {
    if (this.pin.length !== 4) {
      this.errorMessage = 'El PIN debe tener exactamente 4 dígitos.';
      return;
    }
    const ok = await this.pinAuth.validarPin(this.pin);
    if (ok) {
      this.errorMessage = null;
      this.unlocked.emit();
    } else {
      this.errorMessage = 'PIN incorrecto. Intenta nuevamente.';
      this.pin = '';
    }
  }

  onCancel(): void {
    this.cancelled.emit();
  }
}