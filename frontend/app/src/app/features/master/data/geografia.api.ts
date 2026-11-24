import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ConfigService } from '../../../core/config/config.service';

import {
  CantonRequestDto,
  CantonResponseDto,
  GeografiaTreeDto,
  JrvRequestDto,
  JrvResponseDto,
  ParroquiaRequestDto,
  ParroquiaResponseDto,
  ProvinciaRequestDto,
  ProvinciaResponseDto,
} from '../domain/models/geografia.model';

/**
 * API de la configuracion geografica jerarquica (SDD 2026-11-16-config-geografica).
 *
 * <p>Un unico servicio para los cuatro niveles:Provincia → Canton → Parroquia → JRV.
 * El arbol se lee de una vez (`GET /master/geografia`) y cada nivel tiene su CRUD.
 *
 * <p>Las mutaciones exigen `configuracion:write`, que solo MASTER tiene; la lectura del arbol
 * exige `configuracion:read` (MASTER y ADMIN).
 */
@Injectable({ providedIn: 'root' })
export class GeografiaApi {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);
  private get baseUrl(): string {
    return `${this.configService.getApiUrlSafe()}/master`;
  }

  // ---------------------------------------------------------------------------
  // Lectura
  // ---------------------------------------------------------------------------

  /** Arbol geografico completo. */
  obtenerArbol(): Observable<GeografiaTreeDto> {
    return this.http.get<GeografiaTreeDto>(`${this.baseUrl}/geografia`);
  }

  // ---------------------------------------------------------------------------
  // Provincias
  // ---------------------------------------------------------------------------

  /** Crea una provincia. */
  crearProvincia(req: ProvinciaRequestDto): Observable<ProvinciaResponseDto> {
    return this.http.post<ProvinciaResponseDto>(
      `${this.baseUrl}/provincias`,
      req,
    );
  }

  /** Actualiza una provincia. */
  actualizarProvincia(
    id: number,
    req: ProvinciaRequestDto,
  ): Observable<ProvinciaResponseDto> {
    return this.http.put<ProvinciaResponseDto>(
      `${this.baseUrl}/provincias/${id}`,
      req,
    );
  }

  /** Elimina una provincia. 409 si todavia tiene cantones. */
  eliminarProvincia(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/provincias/${id}`);
  }

  // ---------------------------------------------------------------------------
  // Cantones
  // ---------------------------------------------------------------------------

  /** Crea un canton bajo una provincia. */
  crearCanton(req: CantonRequestDto): Observable<CantonResponseDto> {
    return this.http.post<CantonResponseDto>(`${this.baseUrl}/cantones`, req);
  }

  /** Actualiza un canton. */
  actualizarCanton(
    id: number,
    req: CantonRequestDto,
  ): Observable<CantonResponseDto> {
    return this.http.put<CantonResponseDto>(
      `${this.baseUrl}/cantones/${id}`,
      req,
    );
  }

  /** Elimina un canton. 409 si todavia tiene parroquias. */
  eliminarCanton(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/cantones/${id}`);
  }

  // ---------------------------------------------------------------------------
  // Parroquias
  // ---------------------------------------------------------------------------

  /** Crea una parroquia bajo un canton. */
  crearParroquia(req: ParroquiaRequestDto): Observable<ParroquiaResponseDto> {
    return this.http.post<ParroquiaResponseDto>(
      `${this.baseUrl}/parroquias`,
      req,
    );
  }

  /** Actualiza una parroquia. */
  actualizarParroquia(
    id: number,
    req: ParroquiaRequestDto,
  ): Observable<ParroquiaResponseDto> {
    return this.http.put<ParroquiaResponseDto>(
      `${this.baseUrl}/parroquias/${id}`,
      req,
    );
  }

  /** Elimina una parroquia. 409 si todavia tiene JRVs. */
  eliminarParroquia(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/parroquias/${id}`);
  }

  // ---------------------------------------------------------------------------
  // JRVs
  // ---------------------------------------------------------------------------

  /** Crea una JRV bajo una parroquia. */
  crearJrv(req: JrvRequestDto): Observable<JrvResponseDto> {
    return this.http.post<JrvResponseDto>(`${this.baseUrl}/jrvs`, req);
  }

  /** Actualiza una JRV. */
  actualizarJrv(id: number, req: JrvRequestDto): Observable<JrvResponseDto> {
    return this.http.put<JrvResponseDto>(`${this.baseUrl}/jrvs/${id}`, req);
  }

  /** Elimina una JRV. 409 si ya tiene actas registradas. */
  eliminarJrv(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/jrvs/${id}`);
  }
}
