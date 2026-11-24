import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../services/auth.service';
import { CurrentUser } from '../../domain/models/user.model';
import { Session } from '../../data/auth.api';

import { LoginPage } from './login.page';

/**
 * Tests unitarios para LoginPage (T-FE-05 / B.3 — Alineación por Rol).
 *
 * <p>Cubre:
 * <ul>
 *   <li>S-Login-1: redirección segregada por rol tras login exitoso.</li>
 *   <li>S-Login-2: el signal loading vuelve a false tras finalizar (éxito o error).</li>
 *   <li>S-Login-3: errores HTTP se mapean a errorMessage legible.</li>
 * </ul>
 *
 * <p>Estos tests son los que captura la mutación V.1 (navigateByUrl('/dashboard')
 * hardcodeado). Sin el test OBSERVER, un OBSERVER sería redirigido a /dashboard y
 * dispararía el bucle 403 manejado por auth.interceptor.ts.
 */
describe('LoginPage (T-FE-05)', () => {
  const baseUser: CurrentUser = {
    id: 'user-1',
    email: 'u@test.local',
    role: 'ADMIN',
    permissions: [],
  };

  const buildSession = (role: CurrentUser['role']): Session => ({
    accessToken: 'token-xyz',
    expiresInSeconds: 900,
    sesionId: 'ses-1',
    user: { ...baseUser, role },
  });

  let component: LoginPage;
  let fixture: ComponentFixture<LoginPage>;
  let authService: { login: ReturnType<typeof vi.fn> };
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authService = {
      login: vi.fn(),
    };
    router = {
      navigateByUrl: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });

    fixture = TestBed.createComponent(LoginPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // -------------------------------------------------------------------------
  // S-Login-1: redirección segregada por rol.
  // -------------------------------------------------------------------------

  it('S-Login-1a: tras login exitoso de OBSERVER redirige a /observer/mis-jrvs (no /dashboard)', () => {
    authService.login.mockReturnValue(of(buildSession('OBSERVER')));
    component.form.setValue({ email: 'obs@test.local', password: 'secret123' });

    component.submit();

    expect(authService.login).toHaveBeenCalledWith({
      email: 'obs@test.local',
      password: 'secret123',
    });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/observer/mis-jrvs');
    expect(router.navigateByUrl).not.toHaveBeenCalledWith('/dashboard');
  });

  it('S-Login-1b: tras login exitoso de ADMIN redirige a /dashboard', () => {
    authService.login.mockReturnValue(of(buildSession('ADMIN')));
    component.form.setValue({
      email: 'admin@test.local',
      password: 'secret123',
    });

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('S-Login-1c: tras login exitoso de MASTER redirige a /dashboard', () => {
    authService.login.mockReturnValue(of(buildSession('MASTER')));
    component.form.setValue({
      email: 'master@test.local',
      password: 'secret123',
    });

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('S-Login-1d: tras login exitoso de COORDINATOR redirige a /dashboard', () => {
    authService.login.mockReturnValue(of(buildSession('COORDINATOR')));
    component.form.setValue({
      email: 'coord@test.local',
      password: 'secret123',
    });

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  // -------------------------------------------------------------------------
  // S-Login-2: loading se desactiva tras finalizar.
  // -------------------------------------------------------------------------

  it('S-Login-2: loading vuelve a false después de un login exitoso', () => {
    authService.login.mockReturnValue(of(buildSession('ADMIN')));
    component.form.setValue({ email: 'a@test.local', password: 'secret123' });

    expect(component.loading()).toBe(false);
    component.submit();
    // finalize es síncrono cuando el observable emite en frío
    expect(component.loading()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('S-Login-2b: loading vuelve a false después de un error HTTP', () => {
    const err = new HttpErrorResponse({
      status: 401,
      statusText: 'Unauthorized',
    });
    authService.login.mockReturnValue(throwError(() => err));
    component.form.setValue({ email: 'a@test.local', password: 'secret123' });

    component.submit();

    expect(component.loading()).toBe(false);
    expect(component.errorMessage()).toBeTruthy();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  // -------------------------------------------------------------------------
  // S-Login-3: errorMessage en credenciales inválidas (401).
  // -------------------------------------------------------------------------

  it('S-Login-3: 401 setea errorMessage con mensaje legible', () => {
    const err = new HttpErrorResponse({
      status: 401,
      statusText: 'Unauthorized',
      error: { message: 'Credenciales inválidas' },
    });
    authService.login.mockReturnValue(throwError(() => err));
    component.form.setValue({ email: 'bad@test.local', password: 'wrong1234' });

    component.submit();

    expect(component.errorMessage()).toBe('Credenciales inválidas');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('S-Login-4: 429 explica que hay que esperar y cuánto, según Retry-After', () => {
    const err = new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders({ 'Retry-After': '900' }),
      error: { message: 'Demasiadas solicitudes. Reintente en 900 segundos.' },
    });
    authService.login.mockReturnValue(throwError(() => err));
    component.form.setValue({ email: 'bad@test.local', password: 'wrong1234' });

    component.submit();

    expect(component.errorMessage()).toBe(
      'Demasiados intentos de inicio de sesión. Intenta de nuevo en 15 minutos.',
    );
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  // -------------------------------------------------------------------------
  // Guard: form inválido no llama al servicio.
  // -------------------------------------------------------------------------

  it('no llama a authService.login si el formulario es inválido', () => {
    component.form.setValue({ email: '', password: '' });

    component.submit();

    expect(authService.login).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  // -------------------------------------------------------------------------
  // S11: el error se sigue viendo. La clase `.form-error` del stylesheet
  // estaba muerta (ningún template la llevaba) y se retiró; el mensaje lo
  // pinta `p-message`.
  // -------------------------------------------------------------------------

  it('S11: el error del login se muestra en el mensaje', () => {
    const err = new HttpErrorResponse({
      status: 401,
      statusText: 'Unauthorized',
      error: { message: 'Credenciales inválidas' },
    });
    authService.login.mockReturnValue(throwError(() => err));
    component.form.setValue({ email: 'bad@test.local', password: 'wrong1234' });

    component.submit();
    fixture.detectChanges();

    const mensaje = fixture.nativeElement.querySelector('p-message');
    expect(mensaje).toBeTruthy();
    expect(mensaje.textContent).toContain('Credenciales inválidas');
  });
});
