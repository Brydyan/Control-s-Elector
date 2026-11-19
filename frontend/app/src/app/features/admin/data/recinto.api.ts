import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { mapError } from '../../../shared/errors/error-handler';
import type { PagedResponse } from '../../../shared/models/page.model';

export interface RecintoResponse {
  id: number;
  nombre: string;
  parroquiaId: number;
  parroquia?: string;
  coordinadorId?: number;
  coordinador?: {
    id: number;
    nombre: string;
    email: string;
  };
  totalMesas?: number;
  estado: string;
  fechaCreacion?: string;
}

export interface RecintoRequest {
  nombre: string;
  parroquiaId: number;
  coordinadorId?: number;
  estado?: string;
}

export interface RecintoFiltros {
  page?: number;
  size?: number;
  estado?: string;
  parroquiaId?: number;
}

@Injectable({ providedIn: 'root' })
export class RecintoApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/admin/recintos`;

  listarRecintos(
    filtros: RecintoFiltros = { page: 0, size: 20 },
  ): Observable<PagedResponse<RecintoResponse>> {
    return this.http
      .get<PagedResponse<RecintoResponse>>(this.baseUrl, {
        params: this.params(filtros),
      })
      .pipe(
        map((response) => this.ensurePaged(response)),
        catchError((err) => this.handleError(err)),
      );
  }

  obtenerRecinto(id: number): Observable<RecintoResponse> {
    return this.http
      .get<RecintoResponse>(`${this.baseUrl}/${id}`)
      .pipe(catchError((err) => this.handleError(err)));
  }

  crearRecinto(payload: RecintoRequest): Observable<RecintoResponse> {
    return this.http
      .post<RecintoResponse>(this.baseUrl, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  actualizarRecinto(
    id: number,
    payload: RecintoRequest,
  ): Observable<RecintoResponse> {
    return this.http
      .put<RecintoResponse>(`${this.baseUrl}/${id}`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  eliminarRecinto(id: number): Observable<void> {
    return this.http
      .delete(`${this.baseUrl}/${id}`, this.noContentOptions())
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
        'Unexpected recinto response: expected { data, meta } envelope',
      );
    return response;
  }

  private handleError(err: unknown): Observable<never> {
    return throwError(() => mapError(err));
  }
}
