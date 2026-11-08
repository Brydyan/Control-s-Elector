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
}

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
  status: 'INVITED' | 'ACTIVATED' | 'EXPIRED';
  expiraEn: string;
}

export interface UsuarioConPasswordTemporal extends UsuarioResponseDto {
  passwordTemporal?: string | null;
}

export type UsuariosPage = PagedResponse<UsuarioConPasswordTemporal>;
export type SesionesPage = PagedResponse<SesionResponseDto>;
