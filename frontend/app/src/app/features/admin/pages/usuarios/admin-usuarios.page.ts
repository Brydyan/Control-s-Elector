import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Menu } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import {
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  SkeletonComponent,
} from '../../../../shared/ui';
import { PaginatorComponent } from '../../../../shared/components/paginator/paginator.component';
import { AuthStore } from '../../../auth/services/auth.store';
import { AdminApi } from '../../data/admin.api';
import {
  EnviarInvitacionResponse,
  InvitationStatus,
  RolUsuario,
  UsuarioConPasswordTemporal,
  UsuarioFiltros,
  UsuarioResponseDto,
  UserInvitationResponse,
} from '../../domain/models/admin.model';

/**
 * Estado en el que una cuenta admite invitacion. El backend rechaza el lote entero (409) si
 * alguna de las cuentas ya esta activa, asi que la pantalla solo deja marcar estas.
 */
const ESTADO_INVITABLE = 'PENDIENTE_INVITACION';

/**
 * Como quedo el ultimo envio de invitaciones. Lo que se muestra en la pantalla, no un toast:
 * el repo no tiene servicio de notificaciones y el mensaje tiene que quedar a la vista
 * mientras el operador decide que hacer con las cuentas que fallaron.
 */
export interface ResultadoInvitacion {
  tipo: 'ok' | 'error';
  titulo: string;
  detalle: string;
  /** Motivo de cada invitacion que no se pudo enviar. */
  fallidos: string[];
}

@Component({
  selector: 'app-admin-usuarios',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    SkeletonComponent,
    PaginatorComponent,
    Dialog,
    InputText,
    Message,
    Select,
    Skeleton,
    TableModule,
    Menu,
  ],
  templateUrl: './admin-usuarios.page.html',
  styleUrls: ['./admin-usuarios.page.scss'],
})
export class AdminUsuariosPage implements OnInit {
  private readonly service = inject(AdminApi);
  private readonly authStore = inject(AuthStore);
  readonly usuarios = signal<UsuarioResponseDto[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  /** Cuentas marcadas para invitar, por id publico. */
  readonly seleccion = signal<ReadonlySet<string>>(new Set<string>());
  /** Hay un envio de invitaciones en vuelo. */
  readonly enviandoInvitaciones = signal(false);
  /** Como quedo el ultimo envio de invitaciones. */
  readonly resultadoInvitacion = signal<ResultadoInvitacion | null>(null);
  /**
   * MASTER-only controls. The role comes from the in-memory session, so a
   * session restored after a reload lights the controls up on its own.
   */
  readonly esMaster = this.authStore.isMaster;
  terminoBusqueda = '';
  rolSeleccionado: RolUsuario | '' = '';
  habilitadoSeleccionado: 'TODOS' | 'HABILITADOS' | 'DESHABILITADOS' = 'TODOS';
  page = 0;
  size = 20;
  readonly totalElements = signal(0);
  modalAbierto = false;
  modalDetalles = false;
  modoEdicion = false;
  usuarioSeleccionado: UsuarioResponseDto | null = null;
  nombre = '';
  apellido = '';
  rol: RolUsuario = 'OBSERVER';
  passwordTemporal: string | null = null;
  /**
   * Correo de contacto de la persona invitada (T-AUTH-INV-02).
   *
   * <p>Es distinto del login: el backend genera el login a partir de nombre,
   * apellido y rol, y el correo de contacto es donde se manda la invitacion.
   */
  emailPersonal = '';
  /** Hay una invitacion en vuelo. */
  readonly invitaando = signal(false);
  /** A quien se le mando la invitacion, para mostrarlo en la pantalla. */
  readonly invitacionEnviada = signal<UserInvitationResponse | null>(null);
  /** Cuando falla el envio de invitacion en el modal de crear usuario, usuario se queda aqui para reintentar. */
  readonly invitacionFallida = signal<UsuarioResponseDto | null>(null);
  /**
   * Toggle "Enviar Invitacion por Email" (ODD #504, A).
   *
   * <p>Apagado, la cuenta se crea y queda pendiente sin correo: el MASTER
   * puede mandarle la invitacion despues con la accion de reenviar.
   */
  readonly enviarInvitacionPorEmail = signal(true);
  /** Cuenta cuya invitacion se va a reenviar (modal de confirmacion). */
  readonly usuarioAReenviar = signal<UsuarioResponseDto | null>(null);
  readonly reenviando = signal(false);
  readonly roles: RolUsuario[] = ['MASTER', 'COORDINATOR', 'OBSERVER', 'ADMIN'];

  /** Cache de items de menú por usuario para evitar re-cálculos en cada render. */
  private accionesCache = new Map<string, MenuItem[]>();

  /** Filter options for the enabled state. */
  readonly estadoOptions: Array<{ label: string; value: string }> = [
    { label: 'Todos', value: 'TODOS' },
    { label: 'Habilitados', value: 'HABILITADOS' },
    { label: 'Deshabilitados', value: 'DESHABILITADOS' },
  ];

  /** Filter options for the role, with the "all roles" entry first. */
  readonly rolFilterOptions: Array<{ label: string; value: RolUsuario | '' }> =
    [
      { label: 'Todos los roles', value: '' },
      { label: 'MASTER', value: 'MASTER' },
      { label: 'COORDINATOR', value: 'COORDINATOR' },
      { label: 'OBSERVER', value: 'OBSERVER' },
      { label: 'ADMIN', value: 'ADMIN' },
    ];

  ngOnInit(): void {
    this.cargarUsuarios();
  }

  cargarUsuarios(): void {
    this.loading.set(true);
    this.error.set(null);
    const filtros: UsuarioFiltros = {
      search: this.terminoBusqueda.trim() || undefined,
      rol: this.rolSeleccionado || undefined,
      habilitado:
        this.habilitadoSeleccionado === 'TODOS'
          ? undefined
          : this.habilitadoSeleccionado === 'HABILITADOS',
      page: this.page,
      size: this.size,
    };
    this.service.listarUsuarios(filtros).subscribe({
      next: (res) => {
        this.usuarios.set(res.data);
        this.totalElements.set(res.meta.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message || 'No se pudieron cargar los usuarios.');
        this.loading.set(false);
      },
    });
  }

  puedeAnular(usuario: UsuarioResponseDto): boolean {
    return this.esMaster() && usuario.habilitado;
  }
  tieneFiltrosActivos(): boolean {
    return (
      !!this.terminoBusqueda.trim() ||
      !!this.rolSeleccionado ||
      this.habilitadoSeleccionado !== 'TODOS'
    );
  }
  limpiarFiltros(): void {
    this.terminoBusqueda = '';
    this.rolSeleccionado = '';
    this.habilitadoSeleccionado = 'TODOS';
    this.page = 0;
    this.cargarUsuarios();
  }
  onPageChange(page: number): void {
    this.page = page;
    this.cargarUsuarios();
  }
  onSizeChange(size: number): void {
    this.size = size;
    this.page = 0;
    this.cargarUsuarios();
  }

  verDetalles(usuario: UsuarioResponseDto): void {
    this.usuarioSeleccionado = usuario;
    this.modalDetalles = true;
  }
  abrirCrear(): void {
    this.modoEdicion = false;
    this.usuarioSeleccionado = null;
    this.nombre = '';
    this.apellido = '';
    this.rol = 'OBSERVER';
    this.passwordTemporal = null;
    this.emailPersonal = '';
    this.invitacionEnviada.set(null);
    this.error.set(null);
    this.modalAbierto = true;
  }
  abrirEditar(usuario: UsuarioResponseDto): void {
    if (!this.esMaster()) return;
    this.modoEdicion = true;
    this.usuarioSeleccionado = usuario;
    this.nombre = usuario.nombre;
    this.apellido = usuario.apellido;
    this.rol = usuario.rolNombre as RolUsuario;
    this.passwordTemporal = null;
    this.error.set(null);
    this.modalAbierto = true;
  }
  cerrarModal(): void {
    if (!this.submitting()) this.modalAbierto = false;
  }
  cerrarDetalles(): void {
    this.modalDetalles = false;
  }
  guardar(): void {
    if (!this.nombre.trim() || !this.apellido.trim()) {
      this.error.set('Nombre y apellido son obligatorios.');
      return;
    }

    // En modo creacion con invitacion, validar email
    if (
      !this.modoEdicion &&
      this.enviarInvitacionPorEmail() &&
      !this.emailValido()
    ) {
      this.error.set('El email debe ser válido para enviar invitación.');
      return;
    }

    this.submitting.set(true);
    this.error.set(null);
    const payload = {
      nombre: this.nombre.trim(),
      apellido: this.apellido.trim(),
      rolNombre: this.rol,
    };
    const request =
      this.modoEdicion && this.usuarioSeleccionado
        ? this.service.actualizarUsuario(
            this.usuarioSeleccionado.publicId,
            payload,
          )
        : this.service.crearUsuario(payload);
    request.subscribe({
      next: (usuario) => {
        if (!this.modoEdicion) {
          this.passwordTemporal =
            (usuario as UsuarioConPasswordTemporal).passwordTemporal ?? null;

          // Si es creacion y checkbox de invitacion esta activado, enviar invitacion
          if (this.enviarInvitacionPorEmail()) {
            this.enviarInvitacionDesdeCar(usuario as UsuarioResponseDto);
          } else {
            // Sin invitacion, solo cerrar modal
            this.submitting.set(false);
            this.invitacionEnviada.set({
              usuarioId: usuario.publicId,
              username: usuario.email,
              emailPersonal: this.emailPersonal,
              status: 'CREATED',
              expiraEn: null as any,
            });
            this.cargarUsuarios();
          }
        } else {
          this.submitting.set(false);
          this.cargarUsuarios();
          this.modalAbierto = false;
        }
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.message || 'No se pudo guardar el usuario.');
      },
    });
  }

  /**
   * Envia invitacion despues de crear el usuario (fase B-C de ODD).
   * Si falla, guarda el usuario en invitacionFallida para reintento.
   */
  private enviarInvitacionDesdeCar(usuarioCreado: UsuarioResponseDto): void {
    this.service
      .invitarUsuario({
        nombre: this.nombre.trim(),
        apellido: this.apellido.trim(),
        emailPersonal: this.emailPersonal.trim(),
        rolNombre: this.rol,
        sendEmail: this.enviarInvitacionPorEmail(),
      })
      .subscribe({
        next: (respuesta) => {
          this.submitting.set(false);
          this.invitacionEnviada.set(respuesta);
          // Limpiar formulario
          this.nombre = '';
          this.apellido = '';
          this.emailPersonal = '';
          this.rol = 'OBSERVER';
          this.cargarUsuarios();
        },
        error: (err) => {
          this.submitting.set(false);
          // Usuario fue creado, solo fallo el email: guardar para reintento
          this.invitacionFallida.set(usuarioCreado);
          this.error.set(
            err?.message ||
              'Usuario creado, pero no se pudo enviar la invitación. Intenta de nuevo.',
          );
        },
      });
  }
  // ── Invitación de una persona (T-AUTH-INV-02) ─────────────────────────────

  /**
   * El correo de contacto tiene que tener forma de correo.
   *
   * <p>Es el subconjunto practico de RFC 5322: una arroba, algo antes y algo
   * despues, y un punto en el dominio. El backend vuelve a validarlo con el
   * validador de bean y con su propia comprobacion: aca es una ayuda, no la
   * garantia.
   */
  emailValido(): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.emailPersonal.trim());
  }

  /**
   * Toggle "Enviar Invitacion por Email" (ODD #504, A).
   *
   * <p>El checkbox es nativo a proposito: el estado vive en una signal y el
   * input solo la refleja.
   */
  cambiarEnviarInvitacionPorEmail(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.enviarInvitacionPorEmail.set(input.checked);
  }

  /**
   * Texto de exito de la invitacion (ODD #504, A.4).
   *
   * <p>El backend lo manda en `mensaje`; este es el respaldo para cuando no
   * viene.
   */
  mensajeDeInvitacion(invitacion: UserInvitationResponse): string {
    if (invitacion.status === 'CREATED') {
      return 'Usuario creado. Puedes enviar invitación después.';
    }
    return `Invitación enviada a ${invitacion.emailPersonal}`;
  }

  /** El boton de invitar solo se habilita con los tres datos y un email valido. */
  puedeInvitar(): boolean {
    return (
      !this.invitaando() &&
      this.nombre.trim().length > 0 &&
      this.apellido.trim().length > 0 &&
      this.emailPersonal.trim().length > 0 &&
      this.emailValido()
    );
  }

  /**
   * Invita a la persona: crea la cuenta y manda el email de activacion.
   *
   * <p>El mensaje de exito dice a que direccion se mando y con que login entra
   * la persona: sin el login, quien recibe el correo no sabria como entrar.
   *
   * <p>NOTA: Mantiene compatibilidad con tests existentes. En la nueva UX,
   * la invitacion se envía como parte de guardar().
   */
  invitar(): void {
    if (!this.puedeInvitar()) {
      this.error.set(
        'Nombre, apellido y un email válido son obligatorios para invitar.',
      );
      return;
    }

    this.invitaando.set(true);
    this.error.set(null);
    this.invitacionEnviada.set(null);

    this.service
      .invitarUsuario({
        nombre: this.nombre.trim(),
        apellido: this.apellido.trim(),
        emailPersonal: this.emailPersonal.trim(),
        rolNombre: this.rol,
        sendEmail: this.enviarInvitacionPorEmail(),
      })
      .subscribe({
        next: (respuesta) => {
          this.invitaando.set(false);
          this.invitacionEnviada.set(respuesta);
          // El formulario se limpia: la invitacion ya salio y dejar los datos
          // cargados invita a mandar dos veces la misma persona.
          this.nombre = '';
          this.apellido = '';
          this.emailPersonal = '';
          this.rol = 'OBSERVER';
          this.cargarUsuarios();
        },
        error: (err) => {
          this.invitaando.set(false);
          this.error.set(err?.message || 'No se pudo enviar la invitación.');
        },
      });
  }

  /**
   * Reintenta enviar invitacion a un usuario ya creado (ODD #504, D).
   *
   * <p>Se dispara desde el modal de error cuando fallo el email. El usuario
   * ya existe en la base de datos, solo se reintenta el email.
   */
  reintentarInvitacion(): void {
    const usuario = this.invitacionFallida();
    if (!usuario) return;

    this.submitting.set(true);
    this.error.set(null);

    this.service
      .invitarUsuario({
        nombre: usuario.nombre,
        apellido: usuario.apellido,
        emailPersonal: this.emailPersonal.trim(),
        rolNombre: usuario.rolNombre as RolUsuario,
        sendEmail: true,
      })
      .subscribe({
        next: (respuesta) => {
          this.submitting.set(false);
          this.invitacionFallida.set(null);
          this.invitacionEnviada.set(respuesta);
          // Limpiar formulario
          this.nombre = '';
          this.apellido = '';
          this.emailPersonal = '';
          this.rol = 'OBSERVER';
          this.cargarUsuarios();
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(
            err?.message ||
              'No se pudo enviar la invitación. Intenta de nuevo.',
          );
        },
      });
  }

  deshabilitar(usuario: UsuarioResponseDto): void {
    if (!this.puedeAnular(usuario)) return;
    const mensaje = `¿Estás seguro de que deseas deshabilitar a ${usuario.email}? Perderá el acceso al sistema.`;
    if (!confirm(mensaje)) return;
    this.submitting.set(true);
    this.service.deshabilitarUsuario(usuario.publicId).subscribe({
      next: () => this.cargarUsuarios(),
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.message || 'No se pudo deshabilitar el usuario.');
      },
    });
  }
  rehabilitar(usuario: UsuarioResponseDto): void {
    if (
      !this.esMaster() ||
      usuario.habilitado ||
      !confirm(`¿Re-habilitar a ${usuario.email}?`)
    )
      return;
    this.service
      .actualizarUsuario(usuario.publicId, {
        nombre: usuario.nombre,
        apellido: usuario.apellido,
        rolNombre: usuario.rolNombre as RolUsuario,
        habilitado: true,
      })
      .subscribe({
        next: () => this.cargarUsuarios(),
        error: (err) =>
          this.error.set(err?.message || 'No se pudo re-habilitar el usuario.'),
      });
  }

  // ── Invitaciones en lote (Task 3.3) ──────────────────────────────────────

  /**
   * Una cuenta admite invitacion solo si sigue esperando la activacion.
   *
   * <p>No es un control de seguridad — el que manda es el backend, que exige
   * `usuario:create` + MASTER. Es la courtesy que evita un 409: el envio de un lote es atomico y
   * se rechaza entero si una sola de las cuentas ya esta activa, asi que marcar una cuenta
   * activa garantiza que no sale ningun email.
   */
  esInvitable(usuario: UsuarioResponseDto): boolean {
    return usuario.estadoUsuario === ESTADO_INVITABLE;
  }

  estaSeleccionado(publicId: string): boolean {
    return this.seleccion().has(publicId);
  }

  cantidadSeleccionada(): number {
    return this.seleccion().size;
  }

  haySeleccion(): boolean {
    return this.seleccion().size > 0;
  }

  /** Marca o desmarca una cuenta. Las cuentas activas no se pueden marcar. */
  alternarSeleccion(usuario: UsuarioResponseDto): void {
    if (!this.esInvitable(usuario)) return;
    const siguiente = new Set(this.seleccion());
    if (siguiente.has(usuario.publicId)) {
      siguiente.delete(usuario.publicId);
    } else {
      siguiente.add(usuario.publicId);
    }
    this.seleccion.set(siguiente);
  }

  /** Cuentas pendientes de la pagina actual: las unicas que se pueden invitar. */
  invitablesDeLaPagina(): UsuarioResponseDto[] {
    return this.usuarios().filter((usuario) => this.esInvitable(usuario));
  }

  /** Marca o desmarca todas las cuentas pendientes de la pagina actual. */
  alternarTodosInvitables(): void {
    const siguiente = new Set(this.seleccion());
    const invitables = this.invitablesDeLaPagina();
    const yaEstanTodas = invitables.every((usuario) =>
      siguiente.has(usuario.publicId),
    );
    for (const usuario of invitables) {
      if (yaEstanTodas) {
        siguiente.delete(usuario.publicId);
      } else {
        siguiente.add(usuario.publicId);
      }
    }
    this.seleccion.set(siguiente);
  }

  todosInvitablesSeleccionados(): boolean {
    const invitables = this.invitablesDeLaPagina();
    return (
      invitables.length > 0 &&
      invitables.every((usuario) => this.seleccion().has(usuario.publicId))
    );
  }

  limpiarSeleccion(): void {
    this.seleccion.set(new Set<string>());
  }

  puedeEnviarInvitaciones(): boolean {
    return (
      this.esMaster() && this.haySeleccion() && !this.enviandoInvitaciones()
    );
  }

  /** Envia la invitacion a las cuentas marcadas. */
  enviarInvitaciones(): void {
    if (!this.puedeEnviarInvitaciones()) return;
    const ids = [...this.seleccion()];
    this.enviandoInvitaciones.set(true);
    this.resultadoInvitacion.set(null);
    this.service.enviarInvitaciones(ids).subscribe({
      next: (respuesta) => {
        this.enviandoInvitaciones.set(false);
        this.resultadoInvitacion.set(this.resumenDeEnvio(respuesta));
        this.limpiarSeleccion();
        this.cargarUsuarios();
      },
      error: (err) => {
        this.enviandoInvitaciones.set(false);
        this.resultadoInvitacion.set({
          tipo: 'error',
          titulo: 'No se pudieron enviar las invitaciones',
          detalle:
            err?.message ??
            'El servidor no acepto el lote de invitaciones. Revisa las cuentas seleccionadas.',
          fallidos: [],
        });
      },
    });
  }

  /**
   * Envia (o reenvia, si ya se habia invitado) la invitacion de una cuenta pendiente.
   *
   * <p>El backend decide el tipo de correo: si la cuenta ya tiene un token vigente le manda el
   * de reenvio, si no, el de invitacion.
   */
  reenviarInvitacion(usuario: UsuarioResponseDto): void {
    if (
      !this.esMaster() ||
      !this.esInvitable(usuario) ||
      this.enviandoInvitaciones()
    )
      return;
    this.enviandoInvitaciones.set(true);
    this.resultadoInvitacion.set(null);
    this.service.enviarInvitaciones([usuario.publicId]).subscribe({
      next: (respuesta) => {
        this.enviandoInvitaciones.set(false);
        this.resultadoInvitacion.set(this.resumenDeEnvio(respuesta));
        this.cargarUsuarios();
      },
      error: (err) => {
        this.enviandoInvitaciones.set(false);
        this.resultadoInvitacion.set({
          tipo: 'error',
          titulo: 'No se pudo enviar la invitación',
          detalle: err?.message ?? 'El servidor no acepto la invitación.',
          fallidos: [],
        });
      },
    });
  }

  /** Traduce la respuesta del backend al mensaje que ve el operador. */
  private resumenDeEnvio(
    respuesta: EnviarInvitacionResponse,
  ): ResultadoInvitacion {
    const fallidos = respuesta.failures ?? [];
    const enviados = respuesta.sentCount ?? 0;
    const titulo = fallidos.length
      ? `${enviados} invitaciones enviadas, ${fallidos.length} con error`
      : `${enviados} ${enviados === 1 ? 'invitación enviada' : 'invitaciones enviadas'}`;
    return {
      tipo: 'ok',
      titulo,
      detalle: fallidos.length
        ? 'Estas cuentas no se pudieron invitar:'
        : 'Los correos quedan en cola y salen en breve.',
      fallidos: fallidos.map((fallido) => fallido.razon),
    };
  }

  /**
   * Estados en los que la cuenta admite una invitacion (ODD #504, D).
   *
   * <p>CREATED entra: la cuenta existe pero nunca se le invito, que es justo
   * el caso para el que existe el reenvio.
   */
  puedeReenviarInvitacion(usuario: UsuarioResponseDto): boolean {
    if (!this.esMaster()) return false;
    const estado = usuario.invitationStatus;
    if (!estado) {
      // El backend no manda el estado (o la cuenta es de las viejas): se cae
      // al criterio anterior, que es el estado de la cuenta.
      return this.esInvitable(usuario);
    }
    return estado === 'INVITED' || estado === 'EXPIRED' || estado === 'CREATED';
  }

  /** Si a la cuenta ya se le mando una invitacion alguna vez. */
  yaFueInvitado(usuario: UsuarioResponseDto): boolean {
    if (usuario.invitationStatus) {
      return usuario.invitationStatus !== 'CREATED';
    }
    return !!usuario.invitationSentAt;
  }

  /**
   * Estado de la invitacion de la cuenta (ODD #504, C).
   *
   * <p>Si el backend no lo manda, se deduce de lo que si viaja: la cuenta
   * activa, la habilitacion y si hubo algun envio.
   */
  estadoInvitacion(usuario: UsuarioResponseDto): InvitationStatus {
    if (usuario.invitationStatus) return usuario.invitationStatus;
    if (!usuario.habilitado) return 'DISABLED';
    if (usuario.estadoUsuario === 'ACTIVO') return 'ACTIVE';
    return usuario.invitationSentAt ? 'INVITED' : 'CREATED';
  }

  /** Texto del badge de estado. */
  etiquetaEstado(usuario: UsuarioResponseDto): string {
    switch (this.estadoInvitacion(usuario)) {
      case 'INVITED':
        return 'Invitado';
      case 'EXPIRED':
        return 'Vencida';
      case 'ACTIVE':
        return 'Activa';
      case 'DISABLED':
        return 'Deshabilitada';
      default:
        return 'Sin invitación';
    }
  }

  /**
   * Si la invitacion esta por caducar (ODD #504, B.3).
   *
   * <p>Con menos de dos horas para vencer, el operador tiene que saberlo:
   * si no, la invitacion se vence sin que nadie la use.
   */
  expiraPronto(usuario: UsuarioResponseDto): boolean {
    if (this.estadoInvitacion(usuario) !== 'INVITED') return false;
    const expira = this.vencimientoDe(usuario);
    if (!expira) return false;
    const faltan = new Date(expira).getTime() - Date.now();
    return faltan > 0 && faltan < 2 * 60 * 60 * 1000;
  }

  /** Momento en que vence la invitacion vigente, si la hay. */
  private vencimientoDe(usuario: UsuarioResponseDto): string | null {
    if (usuario.invitationExpiresAt) return usuario.invitationExpiresAt;
    if (!usuario.invitationSentAt) return null;
    const enviados = new Date(usuario.invitationSentAt).getTime();
    return new Date(enviados + 24 * 60 * 60 * 1000).toISOString();
  }

  /** Abre el modal de confirmacion del reenvio (ODD #504, D.2). */
  abrirReenvio(usuario: UsuarioResponseDto): void {
    if (!this.puedeReenviarInvitacion(usuario)) return;
    this.usuarioAReenviar.set(usuario);
    this.error.set(null);
  }

  /** El dialogo se cerro: se suelta la cuenta seleccionada. */
  onVisibleReenvioChange(visible: boolean): void {
    if (!visible) {
      this.cerrarReenvio();
    }
  }

  /** Cierra el modal de confirmacion. */
  cerrarReenvio(): void {
    if (this.reenviando()) return;
    this.usuarioAReenviar.set(null);
  }

  /**
   * Reenvia la invitacion (ODD #504, D.3).
   *
   * <p>El backend genera un link nuevo y deja sin efecto el anterior.
   */
  confirmarReenvio(): void {
    const usuario = this.usuarioAReenviar();
    if (!usuario || this.reenviando()) return;

    this.reenviando.set(true);
    this.resultadoInvitacion.set(null);

    this.service.reenviarInvitacion(usuario.publicId).subscribe({
      next: (respuesta) => {
        this.reenviando.set(false);
        this.usuarioAReenviar.set(null);
        this.resultadoInvitacion.set({
          tipo: 'ok',
          titulo: 'Invitación re-enviada',
          detalle:
            respuesta.message ??
            `Invitación re-enviada a ${respuesta.emailPersonal}`,
          fallidos: [],
        });
        this.cargarUsuarios();
      },
      error: (err) => {
        this.reenviando.set(false);
        this.usuarioAReenviar.set(null);
        this.resultadoInvitacion.set({
          tipo: 'error',
          titulo: 'No se pudo reenviar la invitación',
          detalle:
            err?.message ??
            'El servidor no acepto el reenvío de la invitación.',
          fallidos: [],
        });
      },
    });
  }

  /**
   * Obtiene acciones del usuario con caching para evitar re-cálculos en cada render.
   * El cache se invalida cuando cambian estados que afectan los items (esMaster, habilitado, invitaciones).
   */
  obtenerAccionesUsuario(usuario: UsuarioResponseDto): MenuItem[] {
    // Genera una clave única basada en TODOS los estados que determinan qué items aparecen
    const cacheKey = `${usuario.publicId}-${this.esMaster()}-${usuario.habilitado}-${this.puedeReenviarInvitacion(usuario)}-${this.enviandoInvitaciones()}-${this.reenviando()}`;

    // Si existe en caché, retorna directamente (evita re-cálculos en renders)
    if (this.accionesCache.has(cacheKey)) {
      return this.accionesCache.get(cacheKey)!;
    }

    const items: MenuItem[] = [
      {
        label: 'Ver detalles',
        icon: 'pi pi-eye',
        command: () => this.verDetalles(usuario),
      },
    ];

    if (this.puedeReenviarInvitacion(usuario)) {
      items.push({
        label: this.yaFueInvitado(usuario)
          ? 'Reenviar invitación'
          : 'Enviar invitación',
        icon: 'pi pi-envelope',
        disabled: this.enviandoInvitaciones() || this.reenviando(),
        command: () => this.abrirReenvio(usuario),
      });
    }

    if (this.esMaster()) {
      items.push({
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.abrirEditar(usuario),
      });
    }

    if (this.puedeAnular(usuario)) {
      items.push({
        label: 'Deshabilitar',
        icon: 'pi pi-ban',
        styleClass: 'text-red-500',
        command: () => this.deshabilitar(usuario),
      });
    }

    if (this.esMaster() && !usuario.habilitado) {
      items.push({
        label: 'Re-habilitar',
        icon: 'pi pi-check',
        command: () => this.rehabilitar(usuario),
      });
    }

    // Guardar en caché
    this.accionesCache.set(cacheKey, items);

    // Limpiar caché antiguo (mantener máximo 50 entradas)
    if (this.accionesCache.size > 50) {
      const firstKey = this.accionesCache.keys().next().value;
      this.accionesCache.delete(firstKey);
    }

    return items;
  }
}
