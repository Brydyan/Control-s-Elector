import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { ConfigService } from '../../../core/config/config.service';
import { mapError } from '../../../shared/errors/error-handler';
import type { PagedResponse } from '../../../shared/models/page.model';
import {
  CreateUsuarioRequest,
  EnviarInvitacionResponse,
  InviteUsuarioRequest,
  ResendInvitationResponse,
  SesionFiltros,
  SesionResponseDto,
  UpdateUsuarioRequest,
  UsuarioConPasswordTemporal,
  UsuarioFiltros,
  UsuarioResponseDto,
  UserInvitationResponse,
} from '../domain/models/admin.model';

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);
  private readonly baseUrl = `${this.configService.getApiUrlSafe()}/admin`;

  listarUsuarios(
    filtros: UsuarioFiltros = { page: 0, size: 20 },
  ): Observable<PagedResponse<UsuarioConPasswordTemporal>> {
    return this.http
      .get<PagedResponse<UsuarioConPasswordTemporal>>(
        `${this.baseUrl}/usuarios`,
        {
          params: this.params(filtros),
        },
      )
      .pipe(
        map((response) => this.ensurePaged(response)),
        catchError((err) => this.handleError(err)),
      );
  }

  obtenerUsuario(publicId: string): Observable<UsuarioResponseDto> {
    return this.http
      .get<UsuarioResponseDto>(`${this.baseUrl}/usuarios/${publicId}`)
      .pipe(catchError((err) => this.handleError(err)));
  }

  crearUsuario(
    payload: CreateUsuarioRequest,
  ): Observable<UsuarioConPasswordTemporal> {
    // Envia sendInvitation; si no viene, el backend usa invitar por compatibilidad
    return this.http
      .post<UsuarioConPasswordTemporal>(`${this.baseUrl}/usuarios`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  actualizarUsuario(
    publicId: string,
    payload: UpdateUsuarioRequest,
  ): Observable<UsuarioResponseDto> {
    return this.http
      .put<UsuarioResponseDto>(`${this.baseUrl}/usuarios/${publicId}`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  deshabilitarUsuario(publicId: string): Observable<void> {
    return this.http
      .delete(`${this.baseUrl}/usuarios/${publicId}`, this.noContentOptions())
      .pipe(
        map(() => undefined),
        catchError((err) => this.handleError(err)),
      );
  }

  /**
   * Envia invitaciones a los usuarios indicados, por id publico.
   *
   * <p>El backend responde 202: los emails no salen en la respuesta, quedan
   * encolados y se mandan despues. Un lote con una cuenta ya activa se
   * rechaza entero (409): por eso la UI solo deja seleccionar cuentas
   * pendientes.
   */
  enviarInvitaciones(
    usuarioIds: string[],
  ): Observable<EnviarInvitacionResponse> {
    return this.http
      .post<EnviarInvitacionResponse>(
        `${this.configService.getApiUrlSafe()}/auth/invitaciones`,
        { usuarioIds },
      )
      .pipe(catchError((err) => this.handleError(err)));
  }

  /**
   * Invita a una persona: crea la cuenta y manda el email de activacion.
   *
   * <p>La respuesta no trae credenciales: el link de activacion sale por
   * email, al correo de contacto que indico el MASTER.
   */
  invitarUsuario(
    payload: InviteUsuarioRequest,
  ): Observable<UserInvitationResponse> {
    return this.http
      .post<UserInvitationResponse>(
        `${this.configService.getApiUrlSafe()}/users/invite`,
        payload,
      )
      .pipe(catchError((err) => this.handleError(err)));
  }

  /**
   * Reenvia la invitacion de una cuenta que todavia no se activo (ODD #504, E).
   *
   * <p>El backend genera un link nuevo y deja sin efecto el anterior: la
   * invitacion que ya no sirve no se puede reutilizar. Solo lo acepta para una
   * cuenta en INVITED, EXPIRED o CREATED.
   */
  reenviarInvitacion(publicId: string): Observable<ResendInvitationResponse> {
    return this.http
      .post<ResendInvitationResponse>(
        `${this.configService.getApiUrlSafe()}/users/${publicId}/resend-invitation`,
        {},
      )
      .pipe(catchError((err) => this.handleError(err)));
  }

  listarSesiones(
    filtros: SesionFiltros = { page: 0, size: 20 },
  ): Observable<PagedResponse<SesionResponseDto>> {
    let params = this.params(filtros);
    if (filtros.usuarioId !== undefined)
      params = params.set('usuarioId', filtros.usuarioId);
    return this.http
      .get<PagedResponse<SesionResponseDto>>(`${this.baseUrl}/sesiones`, {
        params,
      })
      .pipe(
        map((response) => this.ensurePaged(response)),
        catchError((err) => this.handleError(err)),
      );
  }

  obtenerSesion(sesionId: number): Observable<SesionResponseDto> {
    return this.http
      .get<SesionResponseDto>(`${this.baseUrl}/sesiones/${sesionId}`)
      .pipe(catchError((err) => this.handleError(err)));
  }

  revocarSesion(sesionId: number): Observable<void> {
    return this.http
      .delete(`${this.baseUrl}/sesiones/${sesionId}`, this.noContentOptions())
      .pipe(
        map(() => undefined),
        catchError((err) => this.handleError(err)),
      );
  }

  private params(filtros: object): HttpParams {
    let params = new HttpParams();
    Object.entries(filtros as Record<string, unknown>).forEach(
      ([key, value]) => {
        if (value !== undefined && value !== null && value !== '')
          params = params.set(key, String(value));
      },
    );
    return params;
  }

  private noContentOptions(): { observe: 'response'; responseType: 'text' } {
    return { observe: 'response', responseType: 'text' };
  }

  private ensurePaged<T>(response: PagedResponse<T>): PagedResponse<T> {
    if (!response || !Array.isArray(response.data) || !response.meta)
      throw new Error(
        'Unexpected admin response: expected { data, meta } envelope',
      );
    return response;
  }

  private handleError(err: unknown): Observable<never> {
    return throwError(() => mapError(err));
  }
}
