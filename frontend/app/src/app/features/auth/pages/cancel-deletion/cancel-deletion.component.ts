import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';

import { DeleteAccountApi } from '../../data/delete-account.api';

/**
 * Componente para cancelar eliminación de cuenta (T-COMPLIANCE-04).
 *
 * Ruta pública: /auth/cancel-deletion?token=XXX
 * El usuario clickea el link en el email y recupera su cuenta dentro de 7 días.
 *
 * Flujo:
 * 1. Extrae el token de la query string
 * 2. Valida y procesa POST /api/v1/auth/cancel-deletion/{token}
 * 3. Muestra estado: exitoso o expirado/inválido
 * 4. Redirige a login si es exitoso
 */
@Component({
  selector: 'app-cancel-deletion',
  standalone: true,
  imports: [CommonModule, ButtonModule, MessageModule],
  templateUrl: './cancel-deletion.component.html',
  styleUrls: ['./cancel-deletion.component.scss'],
})
export class CancelDeletionComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(DeleteAccountApi);

  readonly isLoading = signal(true);
  readonly status = signal<'success' | 'error' | 'expired'>('error');
  readonly message = signal('');

  ngOnInit(): void {
    this.processCancellation();
  }

  /**
   * Procesa la cancelación de eliminación.
   *
   * - Extrae el token de la query string
   * - Llama a POST /api/v1/auth/cancel-deletion/{token}
   * - Muestra resultado (éxito, expirado, inválido)
   */
  private async processCancellation(): Promise<void> {
    try {
      const token = this.route.snapshot.queryParamMap.get('token');

      if (!token) {
        this.status.set('error');
        this.message.set(
          'Token de recuperación inválido. Por favor verifica el link en tu email.',
        );
        this.isLoading.set(false);
        return;
      }

      // Procesa la cancelación
      await this.api.cancelDeletion(token).toPromise();

      this.status.set('success');
      this.message.set(
        'Tu cuenta ha sido recuperada exitosamente. Redirigiendo a login...',
      );

      // Redirige a login después de 3 segundos
      setTimeout(() => {
        this.router.navigate(['/auth/login']);
      }, 3000);
    } catch (err: any) {
      const errorMessage = err?.error?.message || '';

      // Diferencia entre expirado e inválido/error
      if (errorMessage.includes('expiró') || errorMessage.includes('plazo')) {
        this.status.set('expired');
        this.message.set(
          'El plazo para recuperar tu cuenta expiró (máximo 7 días). ' +
            'Por favor contacta al administrador para más ayuda.',
        );
      } else if (errorMessage.includes('marcada')) {
        this.status.set('error');
        this.message.set(
          'Esta cuenta no está marcada para eliminación o ya fue recuperada.',
        );
      } else {
        this.status.set('error');
        this.message.set(
          'No se pudo procesar tu solicitud. ' +
            'Por favor verifica el link en tu email e intenta de nuevo.',
        );
      }
    } finally {
      this.isLoading.set(false);
    }
  }

  /**
   * Redirige a login.
   */
  goToLogin(): void {
    this.router.navigate(['/auth/login']);
  }
}
