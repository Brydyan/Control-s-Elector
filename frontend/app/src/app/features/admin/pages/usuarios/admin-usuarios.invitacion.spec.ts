import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminApi } from '../../data/admin.api';
import { AuthStore } from '../../../auth/services/auth.store';
import type { Role } from '../../../auth/domain/models/user.model';
import { AdminUsuariosPage } from './admin-usuarios.page';

registerLocaleData(localeEs);

/** Puts a session of the given role in the in-memory store. */
function loginAs(role: Role): void {
  TestBed.inject(AuthStore).setSession('token', {
    id: 'u-1',
    email: 'user@cne.gob.ec',
    role,
    permissions: [],
    acceptedTermsVersion: '1.0',
  });
}

/**
 * Invitacion de una persona desde el panel (T-AUTH-INV-02).
 *
 * <p>El boton de invitar tiene que estar bloqueado mientras falte un dato o el email no sea
 * valido: el endpoint responde 400 y la cuenta no se crea, asi que el panel tiene que frenarlo
 * antes de gastar un viaje.
 */
describe('AdminUsuariosPage — invitacion de una persona (T-AUTH-INV-02)', () => {
  let fixture: ComponentFixture<AdminUsuariosPage>;
  let page: AdminUsuariosPage;
  let invitarUsuario: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    invitarUsuario = vi.fn().mockReturnValue(
      of({
        usuarioId: 'uuid-1',
        username: 'juagarobs@control.ttcs',
        emailPersonal: 'juan@example.com',
        status: 'INVITED' as const,
        expiraEn: '2026-12-08T10:00:00Z',
      }),
    );

    const service = {
      listarUsuarios: () =>
        of({
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
        }),
      crearUsuario: () => of({ passwordTemporal: null }),
      actualizarUsuario: () => of({}),
      deshabilitarUsuario: () => of(undefined),
      invitarUsuario,
    };

    await TestBed.configureTestingModule({
      imports: [AdminUsuariosPage],
      providers: [{ provide: AdminApi, useValue: service }, providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUsuariosPage);
    page = fixture.componentInstance;
    loginAs('MASTER');
    page.abrirCrear();
    fixture.detectChanges();
  });

  it('el boton de invitar esta bloqueado sin nombre, apellido y email', () => {
    expect(page.puedeInvitar()).toBe(false);

    page.nombre = 'Juan';
    page.apellido = 'García';
    expect(page.puedeInvitar()).toBe(false);

    page.emailPersonal = 'juan@example.com';
    expect(page.puedeInvitar()).toBe(true);
  });

  it('un email con formato invalido no habilita el boton', () => {
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'no-es-un-email';

    expect(page.emailValido()).toBe(false);
    expect(page.puedeInvitar()).toBe(false);
  });

  it('invitar manda los datos de la persona y muestra el resultado', () => {
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';
    page.rol = 'OBSERVER';

    page.invitar();

    expect(invitarUsuario).toHaveBeenCalledOnce();
    expect(invitarUsuario).toHaveBeenCalledWith({
      nombre: 'Juan',
      apellido: 'García',
      emailPersonal: 'juan@example.com',
      rolNombre: 'OBSERVER',
    });
    expect(page.invitacionEnviada()?.emailPersonal).toBe('juan@example.com');
    expect(page.invitacionEnviada()?.username).toBe('juagarobs@control.ttcs');
    expect(page.invitaando()).toBe(false);
  });

  it('el mensaje de exito dice a quien se invito y con que usuario entra', () => {
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';

    page.invitar();
    fixture.detectChanges();

    const texto = document.body.textContent ?? '';
    expect(texto).toContain('Invitación enviada a juan@example.com');
    expect(texto).toContain('juagarobs@control.ttcs');
  });

  it('un error del backend se muestra y no deja el estado de envio colgado', () => {
    invitarUsuario.mockReturnValue(
      throwError(() => new Error('Solo MASTER puede invitar usuarios')),
    );
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';

    page.invitar();

    expect(page.error()).toBe('Solo MASTER puede invitar usuarios');
    expect(page.invitaando()).toBe(false);
    expect(page.invitacionEnviada()).toBeNull();
  });

  it('el formulario se limpia despues de invitar', () => {
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';

    page.invitar();

    expect(page.nombre).toBe('');
    expect(page.apellido).toBe('');
    expect(page.emailPersonal).toBe('');
  });

  it('un email vacio no dispara la peticion', () => {
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = '   ';

    page.invitar();

    expect(invitarUsuario).not.toHaveBeenCalled();
    expect(page.error()).toBeTruthy();
  });
});
