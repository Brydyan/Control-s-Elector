import { ComponentFixture, TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { of, Subject, throwError } from 'rxjs';
import { AdminApi } from '../../data/admin.api';
import type { EnviarInvitacionResponse } from '../../domain/models/admin.model';
import { AuthStore } from '../../../auth/services/auth.store';
import type { Role } from '../../../auth/domain/models/user.model';
import { AdminUsuariosPage } from './admin-usuarios.page';

/**
 * Task 3.3 — invitaciones en lote desde el panel de usuarios.
 *
 * <p>La garantia que sostiene esta pantalla es la del backend: el envio de un lote es atomico y
 * se rechaza entero (409) si alguna de las cuentas ya esta activa. Por eso la UI solo deja
 * seleccionar cuentas PENDIENTE_INVITACION: una seleccion que incluyera una cuenta activa
 * garantiza un 409 y no sale ningun email.
 *
 * <p>El control real de permisos vive en el backend (`usuario:create` + MASTER). Lo que hace la
 * pantalla es no convertir un error 409 en unaolinga para el operador.
 */
describe('AdminUsuariosPage (invitaciones en lote)', () => {
  let fixture: ComponentFixture<AdminUsuariosPage>;
  let page: AdminUsuariosPage;

  const pendiente = {
    publicId: 'u-pendiente',
    email: 'pendiente@cne.gob.ec',
    nombre: 'Ana',
    apellido: 'Perez',
    rolNombre: 'OBSERVER',
    habilitado: true,
    creadoEn: '2026-01-01T00:00:00Z',
    estadoUsuario: 'PENDIENTE_INVITACION' as const,
    invitationSentAt: null,
  };
  const pendienteInvitado = {
    ...pendiente,
    publicId: 'u-invitado',
    email: 'invitado@cne.gob.ec',
    nombre: 'Luis',
    apellido: 'Roca',
    invitationSentAt: '2026-11-05T17:40:00Z',
  };
  const activo = {
    ...pendiente,
    publicId: 'u-activo',
    email: 'activo@cne.gob.ec',
    nombre: 'Sara',
    apellido: 'Cruz',
    estadoUsuario: 'ACTIVO' as const,
  };

  const service = {
    listarUsuarios: vi.fn(() =>
      of({
        data: [pendiente, pendienteInvitado, activo],
        meta: { page: 0, size: 20, totalElements: 3, totalPages: 1 },
      }),
    ),
    enviarInvitaciones: vi.fn(() => of({ sentCount: 2, failures: [] })),
    crearUsuario: () => of({ passwordTemporal: null }),
    actualizarUsuario: () => of({}),
    deshabilitarUsuario: () => of(undefined),
  };

  function loginAs(role: Role): void {
    TestBed.inject(AuthStore).setSession('token', {
      id: 'u-1',
      email: 'master@cne.gob.ec',
      role,
      permissions: [],
    });
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [AdminUsuariosPage],
      providers: [{ provide: AdminApi, useValue: service }, providePrimeNG()],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminUsuariosPage);
    page = fixture.componentInstance;
    loginAs('MASTER');
    fixture.detectChanges();
  });

  it('soloSeSeleccionanLasCuentasPendientesDeInvitacion', () => {
    page.alternarSeleccion(pendiente);

    expect(page.estaSeleccionado(pendiente.publicId)).toBe(true);
    expect(page.cantidadSeleccionada()).toBe(1);
  });

  it('unaCuentaActivaNoSePuedeSeleccionar', () => {
    page.alternarSeleccion(activo);

    expect(page.estaSeleccionado(activo.publicId)).toBe(false);
    expect(page.cantidadSeleccionada()).toBe(0);
  });

  it('enviarInvitaciones_mandaLosIdsPublicosSeleccionados', () => {
    page.alternarSeleccion(pendiente);
    page.alternarSeleccion(pendienteInvitado);

    page.enviarInvitaciones();

    expect(service.enviarInvitaciones).toHaveBeenCalledWith([
      pendiente.publicId,
      pendienteInvitado.publicId,
    ]);
  });

  it('laBarraMasivaSoloApareceCuandoHaySeleccion', () => {
    expect(fixture.nativeElement.querySelector('.bulk-bar')).toBeNull();

    page.alternarSeleccion(pendiente);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.bulk-bar')).not.toBeNull();
  });

  it('laBarraMasivaNoApareceParaQuienNoEsMaster', () => {
    loginAs('ADMIN');
    page.alternarSeleccion(pendiente);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.bulk-bar')).toBeNull();
  });

  it('muestraQueSeEstaEnviando', () => {
    // Un Subject en vez de `of()`: el estado "enviando" tiene que poder observarse
    // mientras la peticion sigue en vuelo, no solo despues de que termine.
    const enVuelo = new Subject<EnviarInvitacionResponse>();
    service.enviarInvitaciones.mockReturnValueOnce(enVuelo.asObservable());
    page.alternarSeleccion(pendiente);

    page.enviarInvitaciones();
    fixture.detectChanges();

    expect(page.enviandoInvitaciones()).toBe(true);
    expect(
      fixture.nativeElement.querySelector('.bulk-bar').textContent,
    ).toContain('Enviando…');
  });

  it('anunciaCuantasInvitacionesSalieron', () => {
    page.alternarSeleccion(pendiente);
    page.alternarSeleccion(pendienteInvitado);

    page.enviarInvitaciones();
    fixture.detectChanges();

    const estado = fixture.nativeElement.querySelector('.bulk-status');
    expect(estado).not.toBeNull();
    expect(estado.textContent).toContain('2 invitaciones enviadas');
    expect(estado.getAttribute('role')).toBe('status');
  });

  it('unFalloDelBackendSeMuestraComoError', () => {
    service.enviarInvitaciones.mockReturnValueOnce(
      throwError(
        () => new Error('El usuario ya está activo: activo@cne.gob.ec'),
      ),
    );
    page.alternarSeleccion(pendiente);

    page.enviarInvitaciones();
    fixture.detectChanges();

    const estado = fixture.nativeElement.querySelector('.bulk-status');
    expect(estado).not.toBeNull();
    expect(estado.getAttribute('role')).toBe('alert');
    expect(estado.textContent).toContain('activo@cne.gob.ec');
  });

  it('trasEnviarSeRecargaLaTabla', () => {
    page.alternarSeleccion(pendiente);
    service.listarUsuarios.mockClear();

    page.enviarInvitaciones();

    expect(service.listarUsuarios).toHaveBeenCalled();
  });

  it('elReenvioDeUnaCuentaLlamaAlEnvioConSuId', () => {
    page.reenviarInvitacion(pendienteInvitado);

    expect(service.enviarInvitaciones).toHaveBeenCalledWith([
      pendienteInvitado.publicId,
    ]);
  });

  it('unaCuentaActivaNoOfreceLaAccionDeReenviar', () => {
    const botones = fixture.nativeElement.querySelectorAll('.btn-invitar');
    const correos = Array.from(botones).map((b: Element) =>
      b.getAttribute('aria-label'),
    );

    expect(correos).toContain('Reenviar invitación a invitado@cne.gob.ec');
    expect(correos).toContain('Enviar invitación a pendiente@cne.gob.ec');
    expect(
      correos.some((c: string | null) => c?.includes('activo@cne.gob.ec')),
    ).toBe(false);
  });

  it('laCasillaDeUnaCuentaActivaVieneDeshabilitadaYExplicaPorQue', () => {
    const casillas = fixture.nativeElement.querySelectorAll(
      'input[type="checkbox"][data-usuario]',
    );
    const activa = Array.from(casillas).find(
      (c: Element) => c.getAttribute('data-usuario') === activo.publicId,
    ) as HTMLInputElement;

    expect(activa.disabled).toBe(true);
    expect(activa.getAttribute('title')).toContain('ya está activa');
  });
});
