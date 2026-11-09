/**
 * Transport DTOs. These mirror the backend contract 1:1
 * (ec.twintailcs.electoral.auth.web.dto). Keep field names exactly as
 * the API returns them; mapping to the domain model happens in the
 * repository, never here.
 */

export interface LoginRequestDto {
  email: string;
  password: string;
}

export interface CurrentUserDto {
  id: string;
  email: string;
  rol: string;
  permisos?: string[];
  /**
   * Version de los terminos aceptada por el usuario. `null` si nunca acepto.
   *
   * Opcional en el DTO: un backend viejo que no lo mande no rompe el login,
   * solo deja al usuario con pendiente de aceptacion.
   */
  acceptedTermsVersion?: string | null;
}

export interface AuthResponseDto {
  accessToken: string;
  tokenType: string; // "Bearer"
  expiresInSeconds: number;
  sesionId: string;
  usuario: CurrentUserDto;
}

/**
 * Respuesta de la validación de un token de activación.
 *
 * `valid` en false no dice si el usuario existe: decirlo permitiría enumerar
 * cuentas. El frontend solo necesita saber si puede mostrar el formulario.
 */
export interface ValidarTokenResponseDto {
  valid: boolean;
  usuarioId: string | null;
  expiresAt: string | null;
  /**
   * Login del usuario (el correo de Control con el que entra).
   *
   * Solo viene cuando el token es válido: quien tiene el token es esa persona.
   */
  username?: string | null;
}

/** Pedido de activación de cuenta. */
export interface ActivarRequestDto {
  token: string;
  password: string;
  passwordConfirm: string;
}

/** Cuenta activada. */
export interface ActivarResponseDto {
  publicId: string;
  nombre: string;
  apellido: string;
  email: string;
  /** Correo de contacto de la persona; null si la cuenta se creó sin correo. */
  emailPersonal?: string | null;
  rolNombre: string;
}
