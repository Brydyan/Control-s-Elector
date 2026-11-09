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
 * La UI es **más estricta** que el backend: pide 12 caracteres y minúscula,
 * mientras que `PasswordValidationResult` acepta 8 y no exige minúscula (política
 * D8 del design). Endurecer el formulario no rechaza nada que el servidor vaya a
 * aceptar: toda contraseña que pasa por acá la acepta el backend.
 */
interface Requisito {
  id: 'length' | 'uppercase' | 'lowercase' | 'number' | 'special_char';
  texto: string;
  cumple: (valor: string) => boolean;
}

const REQUISITOS: Requisito[] = [
  {
    id: 'length',
    texto: 'Mínimo 12 caracteres',
    cumple: (v) => v.length >= 12,
  },
  {
    id: 'uppercase',
    texto: 'Una mayúscula (A-Z)',
    cumple: (v) => /[A-Z]/.test(v),
  },
  {
    id: 'lowercase',
    texto: 'Una minúscula (a-z)',
    cumple: (v) => /[a-z]/.test(v),
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
    emailPersonal: string | null;
    rolNombre: string;
  } | null>(null);

  /**
   * Usuario de Control al que pertenece el token (ODD #504, B.5.1).
   *
   * <p>La pantalla lo muestra para que la persona sepa con qué credencial va a
   * entrar antes de elegir la contraseña.
   */
  readonly usuarioControl = signal<string | null>(null);

  /**
   * El usuario de Control sin dominio: "juagarobs@control.ttcs" -> "juagarobs".
   *
   * <p>Son dos datos distintos y la persona los necesita separados: el usuario
   * que la identifica y el correo con el que entra (ODD #504, A.5.2).
   */
  readonly usuarioCorto = computed(() => {
    const correo = this.usuarioControl();
    if (!correo) return null;
    const arroba = correo.indexOf('@');
    return arroba > 0 ? correo.slice(0, arroba) : correo;
  });

  /**
   * Fuerza estimada de la contraseña (0 a 6).
   *
   * <p>Es una ayuda, no un control: el botón se habilita por los requisitos, no
   * por la fuerza.
   */
  readonly fuerza = computed(() => {
    const valor = this.passwordValue();
    if (!valor) return 0;
    let puntos = 0;
    if (valor.length >= 8) puntos++;
    if (valor.length >= 12) puntos++;
    if (valor.length >= 16) puntos++;
    if (/[A-Z]/.test(valor)) puntos++;
    if (/[a-z]/.test(valor)) puntos++;
    if (/[0-9]/.test(valor)) puntos++;
    if (/[!@#$%^&*]/.test(valor)) puntos++;
    return puntos;
  });

  /** La fuerza, con su nombre. */
  readonly etiquetaFuerza = computed(() => {
    const puntos = this.fuerza();
    if (puntos <= 1) return 'MUY DÉBIL';
    if (puntos === 2) return 'DÉBIL';
    if (puntos === 3) return 'MEDIA';
    if (puntos <= 5) return 'FUERTE';
    return 'MUY FUERTE';
  });

  /**
   * La persona confirmó que la contraseña es definitiva (ODD #504, B.5.1).
   *
   * <p>Es una señal y no un control del formulario a propósito: el botón se
   * habilita con ella, y un `computed` solo se recalcula cuando cambia una
   * señal.
   */
  readonly aceptaDefinitiva = signal(false);

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
      this.todosLosRequisitosCumplidos() &&
      this.coinciden() &&
      this.aceptaDefinitiva() &&
      !this.loading(),
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
            this.usuarioControl.set(respuesta.username ?? null);
            this.estado.set('formulario');
          } else {
            this.estado.set('expirado');
          }
        },
        error: () => this.estado.set('error'),
      });
  }

  /** Marca (o desmarca) la confirmación de la contraseña definitiva. */
  marcarDefinitiva(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.aceptaDefinitiva.set(input.checked);
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
            emailPersonal: cuenta.emailPersonal ?? null,
            rolNombre: cuenta.rolNombre,
          });
          this.estado.set('activado');
          this.contarParaRedirigir();
        },
        error: (err: HttpErrorResponse) =>
          this.errorMessage.set(this.readError(err)),
      });
  }

  /** Cuenta 5 segundos y muestra el resumen de la cuenta. */
  private contarParaRedirigir(): void {
    this.countdown.set(5);
    const tick = setInterval(() => {
      this.countdown.update((c) => c - 1);
      if (this.countdown() <= 0) {
        clearInterval(tick);
        this.verResumen();
      }
    }, 1000);
  }

  /**
   * Va a la pantalla de resumen de la cuenta (ODD #504, B.5.2).
   *
   * <p>Los datos viajan en el estado de la navegacion: la persona llega desde
   * el paso anterior de la misma sesion, no hace falta volver a pedirlos.
   */
  verResumen(): void {
    const cuenta = this.cuentaActivada();
    if (!cuenta) {
      this.irAlLogin();
      return;
    }
    this.router.navigate(['/auth/account-summary'], { state: { cuenta } });
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
