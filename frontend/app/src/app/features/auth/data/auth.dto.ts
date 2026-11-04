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
}

export interface AuthResponseDto {
  accessToken: string;
  tokenType: string; // "Bearer"
  expiresInSeconds: number;
  sesionId: string;
  usuario: CurrentUserDto;
}
