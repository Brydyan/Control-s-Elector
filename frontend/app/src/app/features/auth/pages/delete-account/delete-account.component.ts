import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormsModule,
  ReactiveFormsModule,
  FormBuilder,
  Validators,
} from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { DeleteAccountApi } from '../../data/delete-account.api';

/**
 * Componente para eliminar cuenta de usuario (T-COMPLIANCE-04).
 *
 * Interfaz en Settings → Seguridad:
 * 1. Botón rojo "Eliminar Cuenta Permanentemente"
 * 2. Modal de confirmación oscuro con entrada de contraseña
 * 3. Explica que se eliminará en 30 días (recuperable 7 días)
 * 4. Backend envía email con link de recuperación
 */
@Component({
  selector: 'app-delete-account',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    MessageModule,
  ],
  templateUrl: './delete-account.component.html',
  styleUrls: ['./delete-account.component.scss'],
})
export class DeleteAccountComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(DeleteAccountApi);
  private readonly messages = inject(MessageService);

  readonly showModal = signal(false);
  readonly isLoading = signal(false);
  readonly password = signal('');
  readonly passwordVisible = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal(false);

  /**
   * Abre el modal de confirmación.
   */
  openDeleteModal(): void {
    this.showModal.set(true);
    this.password.set('');
    this.error.set(null);
    this.success.set(false);
  }

  /**
   * Cierra el modal sin hacer nada.
   */
  closeModal(): void {
    this.showModal.set(false);
    this.password.set('');
    this.error.set(null);
  }

  /**
   * Solicita la eliminación de cuenta.
   *
   * - Valida que la contraseña no esté vacía
   * - Envía POST /api/v1/user/request-deletion
   * - Backend envía email con link de recuperación (válido 7 días)
   */
  async confirmDeletion(): Promise<void> {
    const pwd = this.password().trim();
    if (!pwd) {
      this.error.set('Por favor ingresa tu contraseña');
      return;
    }

    this.isLoading.set(true);
    this.error.set(null);

    try {
      const response = await this.api
        .requestDeletion({ password: pwd })
        .toPromise();

      if (response) {
        this.success.set(true);
        this.showModal.set(false);
        this.password.set('');

        // Muestra mensaje de éxito
        this.messages.add({
          severity: 'success',
          summary: 'Eliminación solicitada',
          detail:
            `Tu cuenta se eliminará el ${new Date(response.hardDeleteDeadline).toLocaleDateString()}. ` +
            `Tienes hasta el ${new Date(response.recoveryDeadline).toLocaleDateString()} para recuperarla.`,
          life: 6000,
        });

        // Opcionalmente redirigir a logout después de unos segundos
        setTimeout(() => {
          // window.location.href = '/auth/login';
        }, 2000);
      }
    } catch (err: any) {
      const message = err?.error?.message || 'Error al solicitar eliminación';
      this.error.set(message);
      this.messages.add({
        severity: 'error',
        summary: 'Error',
        detail: message,
        life: 5000,
      });
    } finally {
      this.isLoading.set(false);
    }
  }

  /**
   * Toggle visibilidad de contraseña.
   */
  togglePasswordVisibility(): void {
    this.passwordVisible.set(!this.passwordVisible());
  }
}
