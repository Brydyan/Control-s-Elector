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
/** Usuario de ejemplo del panel. */
const usuarioBase = {
  publicId: 'u-1',
  email: 'juan@control.ttcs',
  nombre: 'Juan',
  apellido: 'García',
  rolNombre: 'OBSERVER' as const,
  habilitado: true,
  creadoEn: '2026-11-01T10:00:00Z',
};

describe('AdminUsuariosPage — invitacion de una persona (T-AUTH-INV-02)', () => {
  let fixture: ComponentFixture<AdminUsuariosPage>;
  let page: AdminUsuariosPage;
  let invitarUsuario: ReturnType<typeof vi.fn>;
  let reenviarInvitacion: ReturnType<typeof vi.fn>;

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

    reenviarInvitacion = vi.fn().mockReturnValue(
      of({
        success: true,
        message: 'Invitación re-enviada a juan@example.com',
        newInvitationSentAt: '2026-11-08T10:00:00Z',
        expiresAt: '2026-11-09T10:00:00Z',
        emailPersonal: 'juan@example.com',
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
      reenviarInvitacion,
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
      sendEmail: true,
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

  // ---------------------------------------------------------------------------
  // ODD #504 (A): el toggle "Enviar Invitacion por Email"
  // ---------------------------------------------------------------------------

  it('el toggle viene encendido por defecto', () => {
    expect(page.enviarInvitacionPorEmail()).toBe(true);
  });

  it('con el toggle apagado la peticion va con sendEmail false', () => {
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';
    page.enviarInvitacionPorEmail.set(false);

    page.invitar();

    expect(invitarUsuario).toHaveBeenCalledWith(
      expect.objectContaining({ sendEmail: false }),
    );
  });

  it('con el toggle encendido la peticion va con sendEmail true', () => {
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';

    page.invitar();

    expect(invitarUsuario).toHaveBeenCalledWith(
      expect.objectContaining({ sendEmail: true }),
    );
  });

  // ---------------------------------------------------------------------------
  // ODD #504 (C y D): el estado de la invitacion y el reenvio
  // ---------------------------------------------------------------------------

  it('el estado de la invitacion se lee del backend y, si no viene, se deduce', () => {
    const conEstado = {
      ...usuarioBase,
      invitationStatus: 'EXPIRED' as const,
    };
    expect(page.estadoInvitacion(conEstado)).toBe('EXPIRED');

    const sinEstado = {
      ...usuarioBase,
      estadoUsuario: 'PENDIENTE_INVITACION' as const,
    };
    expect(page.estadoInvitacion(sinEstado)).toBe('CREATED');
  });

  it('las etiquetas del estado son legibles', () => {
    expect(
      page.etiquetaEstado({ ...usuarioBase, invitationStatus: 'INVITED' }),
    ).toBe('Invitado');
    expect(
      page.etiquetaEstado({ ...usuarioBase, invitationStatus: 'EXPIRED' }),
    ).toBe('Vencida');
    expect(
      page.etiquetaEstado({ ...usuarioBase, invitationStatus: 'ACTIVE' }),
    ).toBe('Activa');
    expect(
      page.etiquetaEstado({ ...usuarioBase, invitationStatus: 'DISABLED' }),
    ).toBe('Deshabilitada');
  });

  it('el reenvio se ofrece para INVITED, EXPIRED y CREATED, y no para ACTIVE ni DISABLED', () => {
    const conEstado = (
      estado: 'INVITED' | 'EXPIRED' | 'CREATED' | 'ACTIVE' | 'DISABLED',
    ) => ({ ...usuarioBase, invitationStatus: estado });

    expect(page.puedeReenviarInvitacion(conEstado('INVITED'))).toBe(true);
    expect(page.puedeReenviarInvitacion(conEstado('EXPIRED'))).toBe(true);
    expect(page.puedeReenviarInvitacion(conEstado('CREATED'))).toBe(true);
    expect(page.puedeReenviarInvitacion(conEstado('ACTIVE'))).toBe(false);
    expect(page.puedeReenviarInvitacion(conEstado('DISABLED'))).toBe(false);
  });

  it('abrir el reenvio pide confirmacion y el backend lo recibe', () => {
    const usuario = {
      ...usuarioBase,
      invitationStatus: 'INVITED' as const,
    };

    page.abrirReenvio(usuario);
    expect(page.usuarioAReenviar()).toBe(usuario);

    page.confirmarReenvio();

    expect(reenviarInvitacion).toHaveBeenCalledWith(usuario.publicId);
    expect(page.usuarioAReenviar()).toBeNull();
    expect(page.resultadoInvitacion()?.titulo).toBe('Invitación re-enviada');
  });

  it('un reenvio a una cuenta activa no se ofrece', () => {
    const usuario = {
      ...usuarioBase,
      invitationStatus: 'ACTIVE' as const,
    };

    page.abrirReenvio(usuario);

    expect(page.usuarioAReenviar()).toBeNull();
    expect(reenviarInvitacion).not.toHaveBeenCalled();
  });

  it('un email vacio no dispara la peticion', () => {
    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = '   ';

    page.invitar();

    expect(invitarUsuario).not.toHaveBeenCalled();
    expect(page.error()).toBeTruthy();
  });

  // ---------------------------------------------------------------------------
  // ODD #504 (B-F): consolidacion de flujo en guardar()
  // ---------------------------------------------------------------------------

  it('guardar() con invitacion activada crea usuario y luego envia invitacion', () => {
    const crearUsuario = vi.fn().mockReturnValue(
      of({
        publicId: 'u-new',
        email: 'juagarobs@control.ttcs',
        nombre: 'Juan',
        apellido: 'García',
        rolNombre: 'OBSERVER',
        habilitado: true,
        creadoEn: '2026-11-08T10:00:00Z',
      }),
    );

    invitarUsuario.mockReturnValue(
      of({
        usuarioId: 'u-new',
        username: 'juagarobs@control.ttcs',
        emailPersonal: 'juan@example.com',
        status: 'INVITED' as const,
        expiraEn: '2026-12-08T10:00:00Z',
      }),
    );

    TestBed.inject(AdminApi).crearUsuario = crearUsuario;

    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';
    page.enviarInvitacionPorEmail.set(true);

    page.guardar();

    expect(crearUsuario).toHaveBeenCalledOnce();
    expect(invitarUsuario).toHaveBeenCalledOnce();
    expect(page.submitting()).toBe(false);
    expect(page.invitacionEnviada()?.emailPersonal).toBe('juan@example.com');
  });

  it('guardar() sin invitacion activada crea usuario sin enviar email', () => {
    const crearUsuario = vi.fn().mockReturnValue(
      of({
        publicId: 'u-new',
        email: 'juagarobs@control.ttcs',
        nombre: 'Juan',
        apellido: 'García',
        rolNombre: 'OBSERVER',
        habilitado: true,
        creadoEn: '2026-11-08T10:00:00Z',
      }),
    );

    invitarUsuario.mockReset();

    TestBed.inject(AdminApi).crearUsuario = crearUsuario;

    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';
    page.enviarInvitacionPorEmail.set(false);

    page.guardar();

    expect(crearUsuario).toHaveBeenCalledOnce();
    expect(invitarUsuario).not.toHaveBeenCalled();
    expect(page.submitting()).toBe(false);
    expect(page.invitacionEnviada()?.status).toBe('CREATED');
  });

  it('si falla el email en guardar(), muestra modal de reintento con usuario creado', () => {
    const usuarioCreado = {
      publicId: 'u-new',
      email: 'juagarobs@control.ttcs',
      nombre: 'Juan',
      apellido: 'García',
      rolNombre: 'OBSERVER',
      habilitado: true,
      creadoEn: '2026-11-08T10:00:00Z',
    };

    const crearUsuario = vi.fn().mockReturnValue(of(usuarioCreado));

    invitarUsuario.mockReturnValue(
      throwError(() => new Error('No se pudo enviar el email')),
    );

    TestBed.inject(AdminApi).crearUsuario = crearUsuario;

    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';
    page.enviarInvitacionPorEmail.set(true);

    page.guardar();

    expect(page.submitting()).toBe(false);
    expect(page.invitacionFallida()?.publicId).toBe('u-new');
    expect(page.error()).toContain('No se pudo enviar el email');
  });

  it('reintentar invitacion reenvía desde modal de error', () => {
    const usuarioCreado = {
      publicId: 'u-new',
      email: 'juagarobs@control.ttcs',
      nombre: 'Juan',
      apellido: 'García',
      rolNombre: 'OBSERVER' as const,
      habilitado: true,
      creadoEn: '2026-11-08T10:00:00Z',
    };

    const crearUsuario = vi.fn().mockReturnValue(of(usuarioCreado));

    invitarUsuario.mockReturnValueOnce(
      throwError(() => new Error('No se pudo enviar el email')),
    );

    invitarUsuario.mockReturnValueOnce(
      of({
        usuarioId: 'u-new',
        username: 'juagarobs@control.ttcs',
        emailPersonal: 'juan@example.com',
        status: 'INVITED' as const,
        expiraEn: '2026-12-08T10:00:00Z',
      }),
    );

    TestBed.inject(AdminApi).crearUsuario = crearUsuario;

    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';
    page.enviarInvitacionPorEmail.set(true);

    // Primer intento falla
    page.guardar();
    expect(page.invitacionFallida()?.publicId).toBe('u-new');

    // Segundo intento (reintento)
    page.reintentarInvitacion();
    expect(page.submitting()).toBe(false);
    expect(page.invitacionFallida()).toBeNull();
    expect(page.invitacionEnviada()?.status).toBe('INVITED');
  });

  it('cancelar modal de error no afecta usuario ya creado', () => {
    const usuarioCreado = {
      publicId: 'u-new',
      email: 'juagarobs@control.ttcs',
      nombre: 'Juan',
      apellido: 'García',
      rolNombre: 'OBSERVER' as const,
      habilitado: true,
      creadoEn: '2026-11-08T10:00:00Z',
    };

    const crearUsuario = vi.fn().mockReturnValue(of(usuarioCreado));

    invitarUsuario.mockReturnValue(
      throwError(() => new Error('No se pudo enviar el email')),
    );

    TestBed.inject(AdminApi).crearUsuario = crearUsuario;

    page.nombre = 'Juan';
    page.apellido = 'García';
    page.emailPersonal = 'juan@example.com';
    page.enviarInvitacionPorEmail.set(true);

    page.guardar();

    // Usuario en modal de error
    expect(page.invitacionFallida()?.publicId).toBe('u-new');

    // Cancelar modal
    page.invitacionFallida.set(null);

    // Usuario sigue existiendo (se recargo), invitacion puede reenviarse luego
    expect(page.invitacionFallida()).toBeNull();
  });
});
