import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputOtp } from 'primeng/inputotp';
import { Message } from 'primeng/message';
import { ButtonComponent } from '../../ui/button/button.component';
import { PinAuthService } from '../../../features/auth/services/pin-auth.service';

/**
 * Local PIN unlock dialog (T-FE-09, A.2, design D1).
 *
 * Use: when the JWT expires and `!navigator.onLine`, the `authGuard`
 * renders this dialog instead of redirecting to `/login`.
 *
 * The dialog is a `p-dialog` and the PIN entry is a `p-inputotp`, so the
 * dialog brings the focus trap and the entry the segmented keypad.
 */
@Component({
  selector: 'app-pin-unlock-modal',
  standalone: true,
  imports: [Dialog, InputOtp, Message, FormsModule, ButtonComponent],
  template: `
    <p-dialog
      [visible]="visible"
      (visibleChange)="onVisibleChange($event)"
      [modal]="true"
      [draggable]="false"
      [resizable]="false"
      [closable]="false"
      [style]="{ width: '24rem' }"
      header="Desbloqueo por PIN"
    >
      <p class="description">
        Sin conexión a internet. Ingresa tu PIN de 4 dígitos para continuar.
      </p>

      <p-inputotp
        class="pin-input"
        [length]="4"
        [integerOnly]="true"
        [(ngModel)]="pin"
        (onChange)="onPinChange()"
        (onKeyUp)="onKeyUp($event)"
        aria-label="PIN de 4 dígitos"
        data-testid="pin-input"
      />

      @if (errorMessage) {
        <p-message
          class="pin-error"
          severity="error"
          [text]="errorMessage!"
          data-testid="pin-error"
        />
      }

      <ng-template #footer>
        <app-button
          label="Desbloquear"
          [disabled]="pin.length !== 4"
          (clicked)="onSubmit()"
          data-testid="pin-submit"
        ></app-button>
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .description {
      margin: 0 0 1rem;
      color: var(--color-text-muted-on-surface);
    }
    .pin-input {
      display: flex;
      justify-content: center;
    }
    .pin-error {
      margin-top: 1rem;
    }
  `,
})
export class PinUnlockModalComponent {
  private readonly pinAuth = inject(PinAuthService);

  @Input() visible = true;
  @Output() unlocked = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  pin = '';
  errorMessage: string | null = null;

  onPinChange(): void {
    if (this.errorMessage && this.pin.length === 4) {
      this.errorMessage = null;
    }
  }

  onKeyUp(event: Event): void {
    if ((event as KeyboardEvent).key === 'Enter') {
      this.onSubmit();
    }
  }

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

  onVisibleChange(next: boolean): void {
    this.visible = next;
    if (!next) {
      this.onCancel();
    }
  }
}
