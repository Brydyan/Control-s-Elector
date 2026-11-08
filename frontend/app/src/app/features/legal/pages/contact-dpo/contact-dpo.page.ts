import { Component, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Textarea } from 'primeng/textarea';
import { finalize } from 'rxjs';

import { LegalService } from '../../services/legal.service';
import { describirErrorLegal } from '../../services/legal.service';
import { ButtonComponent } from '../../../../shared/ui/button/button.component';

/**
 * Formulario de contacto con el DPO (T-LEGAL-01 / spec dpo-contact).
 *
 * <p>La pagina es publica a proposito: el derecho a pedir la supresion de la
 * cuenta lo puede ejercer alguien que ya no tiene sesion, y en ese caso no
 * puede usar la aplicacion para pedirlo.
 *
 * <p>La validacion del cliente y la del servidor dicen lo mismo a proposito:
 * los mensajes son los mismos en los dos lados, para que un rechazo del
 * servidor no se vea como un fallo raro.
 */
@Component({
  selector: 'app-contact-dpo-page',
  standalone: true,
  imports: [ReactiveFormsModule, InputText, Textarea, Message, ButtonComponent],
  templateUrl: './contact-dpo.page.html',
  styleUrl: './contact-dpo.page.scss',
})
export class ContactDpoPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly legal = inject(LegalService);

  /** Estado del envio. */
  readonly loading = signal(false);
  readonly enviado = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly confirmationMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    email: [
      '',
      [Validators.required, Validators.email, Validators.maxLength(254)],
    ],
    subject: ['', [Validators.required, Validators.maxLength(255)]],
    message: ['', [Validators.required, Validators.maxLength(5000)]],
  });

  /**
   * Envia la solicitud.
   *
   * <p>Si el formulario tiene errores no se manda nada: el servidor volveria a
   * rechazarla y el usuario veria el mismo error dos veces.
   */
  onEnviar(): void {
    if (this.loading()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Revisa los campos marcados antes de enviar.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.confirmationMessage.set(null);

    const valor = this.form.getRawValue();
    this.legal
      .contactoDpo(valor)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (respuesta) => {
          this.enviado.set(true);
          this.confirmationMessage.set(
            respuesta.message ??
              'Mensaje recibido. DPO responderá en 5 días hábiles.',
          );
          this.form.reset();
        },
        error: (err: unknown) => {
          this.errorMessage.set(describirErrorLegal(err).mensaje);
        },
      });
  }

  /** Mensaje de error de un campo, si lo tiene. */
  errorDe(campo: 'name' | 'email' | 'subject' | 'message'): string | null {
    const control = this.form.controls[campo];
    if (!control.touched || !control.errors) {
      return null;
    }
    if (control.errors['required']) {
      return this.mensajeRequerido(campo);
    }
    if (control.errors['email']) {
      return 'El email debe ser válido';
    }
    if (control.errors['maxlength']) {
      const max = control.errors['maxlength'].requiredLength as number;
      return `No puede pasar de ${max} caracteres`;
    }
    return null;
  }

  /** Mismo texto que usa el backend para el campo vacio. */
  private mensajeRequerido(
    campo: 'name' | 'email' | 'subject' | 'message',
  ): string {
    switch (campo) {
      case 'name':
        return 'El nombre es requerido';
      case 'email':
        return 'El email es requerido';
      case 'subject':
        return 'El asunto es requerido';
      case 'message':
        return 'El mensaje es requerido';
    }
  }
}
