import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { finalize } from 'rxjs';

import { AuthApi } from '../../data/auth.api';

/**
 * Requisitos de complejidad de la contraseña.
 *
 * Los identificadores son los mismos que usa el backend en `PasswordValidationResult`:
 * el checklist y el mensaje de error tienen que hablar el mismo idioma, y si
 * divergen el frontend muestra un ✓ por un requisito que el servidor va a
 * rechazar.
 */
interface Requisito {
  id: 'length' | 'uppercase' | 'number' | 'special_char';
  texto: string;
  cumple: (valor: string) => boolean;
}

const REQUISITOS: Requisito[] = [
  {
    id: 'length',
    texto: 'Mínimo 8 caracteres',
    cumple: (v) => v.length >= 8,
  },
  {
    id: 'uppercase',
    texto: 'Una mayúscula (A-Z)',
    cumple: (v) => /[A-Z]/.test(v),
  },
  { id: 'number', texto: 'Un número (0-9)', cumple: (v) => /[0-9]/.test(v) },
  {
    id: 'special_char',
    texto: 'Un carácter especial (!@#$%^&*)',
    cumple: (v) => /[!@#$%^&*]/.test(v),
  },
];

/**
 * Pantalla de activación de cuenta.
 *
 * El flujo tiene tres estados: validando el token, pidiendo la contraseña, y
 * la cuenta activada. Se validan por separado porque el mensaje importa: un link
 * vencido y un link corrupto llevan a acciones distintas (pedir una invitación
 * nueva vs. suspectar del enlace).
 */
@Component({
  selector: 'app-activate',
  standalone: true,
  imports: [ReactiveFormsModule, Button, InputText, Message],
  templateUrl: './activate.page.html',
  styleUrl: './activate.page.scss',
})
export class ActivatePage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(AuthApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Estado de la pantalla. */
  readonly estado = signal<
    'validando' | 'formulario' | 'expirado' | 'error' | 'activado'
  >('validando');

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly countdown = signal(0);
  readonly cuentaActivada = signal<{
    nombre: string;
    email: string;
    rolNombre: string;
  } | null>(null);

  readonly form = this.fb.group({
    password: ['', [Validators.required]],
    passwordConfirm: ['', [Validators.required]],
  });

  /**
   * El valor de la contraseña como signal.
   *
   * <p>Un `computed()` solo se recalcula cuando cambian las signals que lee. El valor de un
   * control de formulario no es una signal: leer `form.controls.password.value` dentro de un
   * `computed` lo calcula una vez y lo deja ahí, y el checklist deja de marcar los requisitos
   * mientras el usuario escribe. Por eso el valor entra por `valueChanges`, que sí es un flujo
   * con estado.
   */
  private readonly passwordValue = toSignal(
    this.form.controls.password.valueChanges,
    {
      initialValue: '',
    },
  );

  private readonly passwordConfirmValue = toSignal(
    this.form.controls.passwordConfirm.valueChanges,
    { initialValue: '' },
  );

  /** El checklist, recalculado contra lo que el usuario escribe. */
  readonly requisitos = computed(() => {
    const valor = this.passwordValue();
    return REQUISITOS.map((req) => ({
      id: req.id,
      texto: req.texto,
      cumple: req.cumple(valor),
    }));
  });

  /** Todos los requisitos cumplidos. */
  readonly todosLosRequisitosCumplidos = computed(() =>
    this.requisitos().every((r) => r.cumple),
  );

  /** Las dos contraseñas coinciden. */
  readonly coinciden = computed(
    () =>
      !!this.passwordValue() &&
      this.passwordValue() === this.passwordConfirmValue(),
  );

  /** El botón se habilita solo cuando todo está en su lugar. */
  readonly puedeEnviar = computed(
    () =>
      this.todosLosRequisitosCumplidos() && this.coinciden() && !this.loading(),
  );

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!token) {
      this.estado.set('expirado');
      return;
    }
    this.validar(token);
  }

  /** Consulta al backend si el token sirve, antes de mostrar el formulario. */
  private validar(token: string): void {
    this.api
      .validarToken(token)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (respuesta) => {
          if (respuesta.valid) {
            this.estado.set('formulario');
          } else {
            this.estado.set('expirado');
          }
        },
        error: () => this.estado.set('error'),
      });
  }

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  submit(): void {
    const token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.puedeEnviar() || !token) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.api
      .activar({
        token,
        password: this.form.getRawValue().password,
        passwordConfirm: this.form.getRawValue().passwordConfirm,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (cuenta) => {
          this.cuentaActivada.set({
            nombre: `${cuenta.nombre} ${cuenta.apellido}`.trim(),
            email: cuenta.email,
            rolNombre: cuenta.rolNombre,
          });
          this.estado.set('activado');
          this.contarParaRedirigir();
        },
        error: (err: HttpErrorResponse) =>
          this.errorMessage.set(this.readError(err)),
      });
  }

  /** Cuenta 5 segundos y manda al login. */
  private contarParaRedirigir(): void {
    this.countdown.set(5);
    const tick = setInterval(() => {
      this.countdown.update((c) => c - 1);
      if (this.countdown() <= 0) {
        clearInterval(tick);
        this.irAlLogin();
      }
    }, 1000);
  }

  irAlLogin(): void {
    this.router.navigateByUrl('/login');
  }

  private readError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Verifica tu conexión.';
    }
    if (err.status === 409) {
      return (
        err.error?.message ??
        'Este link ya fue usado o la invitación fue reenviada. Pedí una invitación nueva.'
      );
    }
    if (err.status === 429) {
      return 'Demasiados intentos. Esperá un minuto e intentá de nuevo.';
    }
    return (
      err.error?.message ?? 'No se pudo activar la cuenta. Intentá nuevamente.'
    );
  }
}
