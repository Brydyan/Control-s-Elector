import type { PagedResponse } from '../../../shared/models/page.model';
export interface UsuarioResponseDto {
  publicId: string;
  email: string;
  nombre: string;
  apellido: string;
  rolNombre: string;
  habilitado: boolean;
  creadoEn: string;
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
  rol: RolUsuario;
}

export interface UpdateUsuarioRequest {
  nombre: string;
  apellido: string;
  rol: RolUsuario;
  habilitado?: boolean;
}

export interface UsuarioConPasswordTemporal extends UsuarioResponseDto {
  passwordTemporal?: string | null;
}

export type UsuariosPage = PagedResponse<UsuarioConPasswordTemporal>;
export type SesionesPage = PagedResponse<SesionResponseDto>;
