import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { ConfigService } from '../../../core/config/config.service';
import { mapError } from '../../../shared/errors/error-handler';

export interface Notificacion {
  id: number;
  usuarioId: number;
  tipo: string;
  acta_id?: number;
  titulo: string;
  mensaje: string;
  leida: boolean;
  fechaCreacion: string;
}

export interface NotificacionResponse {
  data: Notificacion[];
  meta: {
    total: number;
    sinLeer: number;
  };
}

@Injectable({ providedIn: 'root' })
export class NotificacionApi {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);
  private get baseUrl(): string {
    return `${this.configService.getApiUrlSafe()}/notifications`;
  }

  obtenerPendientes(limit: number = 10): Observable<NotificacionResponse> {
    return this.http
      .get<NotificacionResponse>(`${this.baseUrl}/pendientes`, {
        params: new HttpParams().set('limit', String(limit)),
      })
      .pipe(
        map((response) => this.ensurePaged(response)),
        catchError((err) => this.handleError(err)),
      );
  }

  marcarComoLeida(notifId: number): Observable<void> {
    return this.http
      .put(`${this.baseUrl}/${notifId}/leer`, {}, this.noContentOptions())
      .pipe(
        map(() => undefined),
        catchError((err) => this.handleError(err)),
      );
  }

  private noContentOptions(): { observe: 'response'; responseType: 'text' } {
    return { observe: 'response', responseType: 'text' };
  }

  private ensurePaged(response: NotificacionResponse): NotificacionResponse {
    if (!response || !Array.isArray(response.data) || !response.meta)
      throw new Error(
        'Unexpected notificacion response: expected { data, meta } envelope',
      );
    return response;
  }

  private handleError(err: unknown): Observable<never> {
    return throwError(() => mapError(err));
  }
}
