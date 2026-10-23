import { Component, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { finalize } from 'rxjs';

import { AuthService } from '../../../../core/auth/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, Button, InputText, Message],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
})
export class LoginPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (session) => {
          // OBSERVER must be redirected to /observer/mis-jrvs, not /dashboard —
          // the backend denies /dashboard for that role, which would trigger
          // the 403 → /dashboard loop handled by auth.interceptor.ts.
          const target =
            session.user.role === 'OBSERVER'
              ? '/observer/mis-jrvs'
              : '/dashboard';
          this.router.navigateByUrl(target);
        },
        error: (err: HttpErrorResponse) =>
          this.errorMessage.set(this.readError(err)),
      });
  }

  private readError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Verifica tu conexión.';
    }
    if (err.status === 401) {
      return err.error?.message ?? 'Correo o contraseña incorrectos.';
    }
    return 'Ocurrió un error al iniciar sesión. Intenta nuevamente.';
  }
}
