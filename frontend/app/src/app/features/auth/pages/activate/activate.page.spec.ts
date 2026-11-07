import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthApi } from '../../data/auth.api';

import { ActivatePage } from './activate.page';

/**
 * Tests de la pantalla de activación (T-AUTH-INV-01, Task 3.1).
 *
 * <p>Cubre lo que el spec pide y lo que de verdad importa:
 * <ul>
 *   <li>El token se valida contra el backend antes de mostrar el formulario, y un token
 *       inválido lleva a la pantalla de "expiró", no a un formulario inútil.</li>
 *   <li>El checklist de requisitos se recalcula en tiempo real.</li>
 *   <li>El botón se habilita solo cuando los cuatro requisitos están y las dos
 *       contraseñas coinciden: es lo que captura la mutación del validador.</li>
 *   <li>La cuenta activada muestra los datos y redirige al login.</li>
 * </ul>
 */
describe('ActivatePage (T-AUTH-INV-01)', () => {
  let component: ActivatePage;
  let fixture: ComponentFixture<ActivatePage>;
  let api: {
    validarToken: ReturnType<typeof vi.fn>;
    activar: ReturnType<typeof vi.fn>;
  };
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };

  /**
   * Crea el componente con el token de la URL.
   *
   * <p>El TestBed se configura una sola vez por test: `beforeEach` deja los dobles
   * listos y cada test arma el componente con el token que necesita.
   */
  const crear = (token: string | null = 'token-valido'): void => {
    const queryParamMap = { get: vi.fn().mockReturnValue(token) };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthApi, useValue: api },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActivatePage);
    component = fixture.componentInstance;
  };

  beforeEach(() => {
    api = {
      validarToken: vi.fn().mockReturnValue(
        of({
          valid: true,
          usuarioId: 'u-1',
          expiresAt: '2026-11-07T00:00:00Z',
        }),
      ),
      activar: vi.fn().mockReturnValue(
        of({
          publicId: 'u-1',
          nombre: 'Ana',
          apellido: 'Perez',
          email: 'ana@control.ttcs',
          rolNombre: 'OBSERVER',
        }),
      ),
    };
    router = { navigateByUrl: vi.fn() };
  });

  it('valida el token de la URL antes de mostrar el formulario', () => {
    crear();
    fixture.detectChanges();

    expect(api.validarToken).toHaveBeenCalledWith('token-valido');
    expect(component.estado()).toBe('formulario');
  });

  it('un token inválido lleva a la pantalla de link expirado, no a un formulario', () => {
    api.validarToken.mockReturnValue(
      of({ valid: false, usuarioId: null, expiresAt: null }),
    );
    crear();
    fixture.detectChanges();

    expect(component.estado()).toBe('expirado');
  });

  it('sin token en la URL no intenta validar nada', () => {
    crear(null);
    fixture.detectChanges();

    expect(api.validarToken).not.toHaveBeenCalled();
    expect(component.estado()).toBe('expirado');
  });

  it('el checklist marca los requisitos cumplidos en tiempo real', () => {
    crear();
    fixture.detectChanges();

    component.form.controls.password.setValue('abc');
    expect(component.requisitos().filter((r) => r.cumple).length).toBe(0);

    component.form.controls.password.setValue('Abc123!x');
    expect(component.requisitos().every((r) => r.cumple)).toBe(true);
    expect(component.todosLosRequisitosCumplidos()).toBe(true);
  });

  it('el botón se habilita solo con los requisitos cumplidos y las contraseñas iguales', () => {
    crear();
    fixture.detectChanges();

    component.form.controls.password.setValue('Abc123!x');
    component.form.controls.passwordConfirm.setValue('Abc123!x');
    expect(component.puedeEnviar()).toBe(true);

    component.form.controls.passwordConfirm.setValue('Otra123!x');
    expect(component.coinciden()).toBe(false);
    expect(component.puedeEnviar()).toBe(false);
  });

  it('una contraseña débil deja el botón deshabilitado', () => {
    crear();
    fixture.detectChanges();

    component.form.controls.password.setValue('corta');
    component.form.controls.passwordConfirm.setValue('corta');

    expect(component.puedeEnviar()).toBe(false);
  });

  it('la activación manda el token y las dos contraseñas', () => {
    crear();
    fixture.detectChanges();

    component.form.controls.password.setValue('Observer@2025!');
    component.form.controls.passwordConfirm.setValue('Observer@2025!');
    component.submit();

    expect(api.activar).toHaveBeenCalledWith({
      token: 'token-valido',
      password: 'Observer@2025!',
      passwordConfirm: 'Observer@2025!',
    });
    expect(component.estado()).toBe('activado');
    expect(component.cuentaActivada()).toEqual({
      nombre: 'Ana Perez',
      email: 'ana@control.ttcs',
      rolNombre: 'OBSERVER',
    });
  });

  it('un 409 se muestra como mensaje y no como pantalla activada', () => {
    api.activar.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'El token ya fue consumido' },
          }),
      ),
    );
    crear();
    fixture.detectChanges();

    component.form.controls.password.setValue('Observer@2025!');
    component.form.controls.passwordConfirm.setValue('Observer@2025!');
    component.submit();

    expect(component.estado()).not.toBe('activado');
    expect(component.errorMessage()).toBe('El token ya fue consumido');
  });

  it('la cuenta activada ofrece ir al login a mano', () => {
    crear();
    fixture.detectChanges();

    component.irAlLogin();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
