import type { PagedResponse } from '../../../../shared/models/page.model';

/** Estado de activacion de una cuenta. */
export type EstadoUsuario = 'ACTIVO' | 'PENDIENTE_INVITACION';

export interface UsuarioResponseDto {
  publicId: string;
  email: string;
  nombre: string;
  apellido: string;
  rolNombre: string;
  habilitado: boolean;
  creadoEn: string;
  /**
   * Estado de activacion de la cuenta. El panel lo usa para decidir a quien se
   * puede invitar y a quien hay que reenviarle el link: una cuenta activa no
   * admite invitacion.
   */
  estadoUsuario?: EstadoUsuario;
  /** Ultimo envio de invitacion. Ausente si a la cuenta nunca se le invito. */
  invitationSentAt?: string | null;
  /**
   * Estado de la INVITACION (ODD #504, C). No es lo mismo que el estado de la
   * cuenta: una cuenta activa puede conservar una invitacion vieja sin usar.
   */
  invitationStatus?: InvitationStatus;
  /** Momento en que vence la invitacion vigente; null si no hay invitacion. */
  invitationExpiresAt?: string | null;
}

/**
 * Estados de una invitacion (ODD #504, C).
 *
 * <p>`CREATED` es la cuenta que existe pero nunca recibio el correo: por eso el
 * reenvio tambien se le ofrece a ese estado.
 */
export type InvitationStatus =
  'CREATED' | 'INVITED' | 'EXPIRED' | 'ACTIVE' | 'DISABLED';

/** Una invitacion que no se pudo encolar, con el motivo. */
export interface InvitacionFailure {
  usuarioId: string;
  razon: string;
}

/** Resultado de un envio de invitaciones en lote. */
export interface EnviarInvitacionResponse {
  sentCount: number;
  failures: InvitacionFailure[];
}

export interface SesionResponseDto {
  id: number;
  publicId: string;
  usuarioId: number;
  usuarioEmail: string;
  ipCliente: string | null;
  userAgent: string | null;
  creadoEn: string;
  ultimoUsoEn: string;
  expiraEn: string;
}

export type { PagedResponse };

export type RolUsuario = 'MASTER' | 'COORDINATOR' | 'OBSERVER' | 'ADMIN';
export type EstadoHabilitado = 'TODOS' | 'HABILITADOS' | 'DESHABILITADOS';

export interface UsuarioFiltros {
  search?: string;
  rol?: RolUsuario;
  habilitado?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface SesionFiltros {
  search?: string;
  usuarioId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface CreateUsuarioRequest {
  nombre: string;
  apellido: string;
  rolNombre: RolUsuario;
  /**
   * Si la invitacion por email se debe enviar (ODD Invitación Inversible).
   *
   * <p>Si es true, la cuenta se crea con estado PENDING_INVITATION e invitacion por email.
   * Si es false, la cuenta se crea ACTIVA sin invitacion.
   */
  sendInvitation?: boolean;
}

export interface UpdateUsuarioRequest {
  nombre: string;
  apellido: string;
  rolNombre: RolUsuario;
  habilitado?: boolean;
}

/**
 * Datos para invitar a una persona (T-AUTH-INV-02).
 *
 * `emailPersonal` es el correo de contacto: a donde se manda la invitacion. Es
 * distinto del login, que el backend genera solo a partir de nombre, apellido
 * y rol.
 */
export interface InviteUsuarioRequest {
  nombre: string;
  apellido: string;
  emailPersonal: string;
  rolNombre: RolUsuario;
  /**
   * Si el correo de invitacion sale (ODD #504, A).
   *
   * <p>Apagado, la cuenta se crea y queda pendiente sin correo: el MASTER puede
   * mandarle la invitacion despues con la accion de reenviar. El backend lo
   * trata como `true` cuando no viene.
   */
  sendEmail?: boolean;
}

/**
 * Resultado de una invitacion.
 *
 * No trae ninguna credencial: la unica credencial de la invitacion es el link
 * de activacion, que sale por email.
 */
export interface UserInvitationResponse {
  usuarioId: string;
  username: string;
  emailPersonal: string;
  status: 'CREATED' | 'INVITED' | 'EXPIRED' | 'ACTIVE' | 'DISABLED';
  expiraEn: string | null;
  /** Siempre true: un fallo llega como error, no como respuesta. */
  success?: boolean;
  /** Texto para el operador: distinto si el correo salio o no. */
  mensaje?: string;
}

/**
 * Resultado de reenviar una invitacion (ODD #504, D/E).
 *
 * <p>Tampoco trae credenciales: lo unico que viaja es el link, por email.
 */
export interface ResendInvitationResponse {
  success: boolean;
  message: string;
  newInvitationSentAt: string;
  expiresAt: string;
  emailPersonal: string;
}

export interface UsuarioConPasswordTemporal extends UsuarioResponseDto {
  passwordTemporal?: string | null;
}

export type UsuariosPage = PagedResponse<UsuarioConPasswordTemporal>;
export type SesionesPage = PagedResponse<SesionResponseDto>;
