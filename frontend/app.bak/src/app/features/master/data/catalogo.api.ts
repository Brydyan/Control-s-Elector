import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { mapError } from '../../../shared/errors/error-handler';
import {
  CandidatoRequestDto,
  CandidatoResponseDto,
  CargoRequestDto,
  CargoResponseDto,
  PartidoRequestDto,
  PartidoResponseDto,
} from '../domain/models/catalogo.model';

@Injectable({ providedIn: 'root' })
export class CatalogoApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/configuracion`;

  // --- CARGOS ---
  listarCargos(): Observable<CargoResponseDto[]> {
    return this.http
      .get<CargoResponseDto[]>(`${this.baseUrl}/cargos`)
      .pipe(catchError((err) => this.handleError(err)));
  }

  crearCargo(payload: CargoRequestDto): Observable<CargoResponseDto> {
    return this.http
      .post<CargoResponseDto>(`${this.baseUrl}/cargos`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  actualizarCargo(
    id: number,
    payload: CargoRequestDto,
  ): Observable<CargoResponseDto> {
    return this.http
      .put<CargoResponseDto>(`${this.baseUrl}/cargos/${id}`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  eliminarCargo(id: number): Observable<void> {
    return this.http
      .delete(`${this.baseUrl}/cargos/${id}`, this.noContentOptions())
      .pipe(
        map(() => undefined),
        catchError((err) => this.handleError(err)),
      );
  }

  // --- PARTIDOS ---
  listarPartidos(): Observable<PartidoResponseDto[]> {
    return this.http
      .get<PartidoResponseDto[]>(`${this.baseUrl}/partidos`)
      .pipe(catchError((err) => this.handleError(err)));
  }

  crearPartido(payload: PartidoRequestDto): Observable<PartidoResponseDto> {
    return this.http
      .post<PartidoResponseDto>(`${this.baseUrl}/partidos`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  actualizarPartido(
    id: number,
    payload: PartidoRequestDto,
  ): Observable<PartidoResponseDto> {
    return this.http
      .put<PartidoResponseDto>(`${this.baseUrl}/partidos/${id}`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  eliminarPartido(id: number): Observable<void> {
    return this.http
      .delete(`${this.baseUrl}/partidos/${id}`, this.noContentOptions())
      .pipe(
        map(() => undefined),
        catchError((err) => this.handleError(err)),
      );
  }

  // --- CANDIDATOS ---
  listarCandidatos(filtros?: {
    cargoId?: number;
    partidoId?: number;
  }): Observable<CandidatoResponseDto[]> {
    let params = new HttpParams();
    if (filtros?.cargoId) {
      params = params.set('cargoId', filtros.cargoId.toString());
    }
    if (filtros?.partidoId) {
      params = params.set('partidoId', filtros.partidoId.toString());
    }
    return this.http
      .get<CandidatoResponseDto[]>(`${this.baseUrl}/candidatos`, { params })
      .pipe(catchError((err) => this.handleError(err)));
  }

  crearCandidato(
    payload: CandidatoRequestDto,
  ): Observable<CandidatoResponseDto> {
    return this.http
      .post<CandidatoResponseDto>(`${this.baseUrl}/candidatos`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  actualizarCandidato(
    id: number,
    payload: CandidatoRequestDto,
  ): Observable<CandidatoResponseDto> {
    return this.http
      .put<CandidatoResponseDto>(`${this.baseUrl}/candidatos/${id}`, payload)
      .pipe(catchError((err) => this.handleError(err)));
  }

  eliminarCandidato(id: number): Observable<void> {
    return this.http
      .delete(`${this.baseUrl}/candidatos/${id}`, this.noContentOptions())
      .pipe(
        map(() => undefined),
        catchError((err) => this.handleError(err)),
      );
  }

  private noContentOptions(): { observe: 'response'; responseType: 'text' } {
    return { observe: 'response', responseType: 'text' };
  }

  private handleError(err: unknown): Observable<never> {
    return throwError(() => mapError(err));
  }
}
