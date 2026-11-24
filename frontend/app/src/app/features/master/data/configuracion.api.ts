import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { shareReplay } from 'rxjs/operators';

import {
  CargoDto,
  CargoRequest,
  PartidoDto,
  PartidoRequest,
  CandidatoDto,
  CandidatoRequest,
} from '../domain/models/configuracion.model';
import { ConfigService } from '../../../core/config/config.service';
import { ObserverApi } from '../../observer/data/observer.api';
import { DashboardApi } from '../../dashboard/data/dashboard.api';

/**
 * T-FE-16 Fase A.2: Servicio de API para Configuración Electoral
 *
 * Implementa métodos CRUD tipados para Cargos, Partidos y Candidatos.
 * Incluye caché con shareReplay(1) para evitar duplicación de GET al cambiar tabs.
 * Todos los DTOs están fuertemente tipados sin `any`.
 */
@Injectable({ providedIn: 'root' })
export class ConfiguracionApi {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);
  private readonly observerApi = inject(ObserverApi);
  private readonly dashboardApi = inject(DashboardApi);
  private get baseUrl(): string {
    return `${this.configService.getApiUrlSafe()}/configuracion`;
  }

  // ============================================================================
  // CACHÉ PRIVADO (shareReplay(1) previene duplicación de HTTP)
  // ============================================================================

  private cargos$: Observable<CargoDto[]> | null = null;
  private partidos$: Observable<PartidoDto[]> | null = null;
  private candidatos$: Observable<CandidatoDto[]> | null = null;

  // ============================================================================
  // CARGO METHODS
  // ============================================================================

  /**
   * Obtiene listado de cargos electorales.
   * Implementa caché con shareReplay(1): una sola llamada HTTP incluso si múltiples
   * subscriptores lo piden simultáneamente.
   *
   * @returns Observable<CargoDto[]> — Listado de cargos activos
   *
   * @example
   * // Backend response:
   * [
   *   {
   *     "id": 1,
   *     "nombre": "Presidente",
   *     "nivelTerritorial": "NACIONAL",
   *     "orden": 1,
   *     "estado": "ACTIVO"
   *   }
   * ]
   */
  getCargos(): Observable<CargoDto[]> {
    if (!this.cargos$) {
      this.cargos$ = this.http
        .get<CargoDto[]>(`${this.baseUrl}/cargos`)
        .pipe(shareReplay(1));
    }
    return this.cargos$;
  }

  /**
   * Crea un nuevo cargo electoral.
   *
   * @param req CargoRequest — Datos del cargo a crear (nombre, nivel, orden, estado)
   * @returns Observable<CargoDto> — Cargo creado con ID asignado por backend
   *
   * @example
   * // Request:
   * {
   *   "nombre": "Vicepresidente",
   *   "nivelTerritorial": "NACIONAL",
   *   "orden": 2,
   *   "estado": "ACTIVO"
   * }
   * // Response: { id: 2, ...request }
   */
  createCargo(req: CargoRequest): Observable<CargoDto> {
    return this.http.post<CargoDto>(`${this.baseUrl}/cargos`, req);
  }

  /**
   * Actualiza un cargo electoral existente.
   *
   * @param id number — ID del cargo a actualizar
   * @param req CargoRequest — Datos actualizados
   * @returns Observable<CargoDto> — Cargo actualizado
   *
   * @example
   * // Request:
   * {
   *   "nombre": "Presidente de la República",
   *   "nivelTerritorial": "NACIONAL",
   *   "orden": 1,
   *   "estado": "ACTIVO"
   * }
   * // Response: { id: 1, ...request }
   */
  updateCargo(id: number, req: CargoRequest): Observable<CargoDto> {
    return this.http.put<CargoDto>(`${this.baseUrl}/cargos/${id}`, req);
  }

  /**
   * Elimina un cargo electoral.
   *
   * @param id number — ID del cargo a eliminar
   * @returns Observable<void>
   *
   * @throws 409 Conflict si existen candidatos asignados a este cargo
   * @throws 400 Bad Request si hay otros conflictos de integridad
   */
  deleteCargo(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/cargos/${id}`);
  }

  // ============================================================================
  // PARTIDO METHODS
  // ============================================================================

  /**
   * Obtiene listado de partidos políticos registrados.
   * Implementa caché con shareReplay(1).
   *
   * @returns Observable<PartidoDto[]> — Listado de partidos
   *
   * @example
   * // Backend response:
   * [
   *   {
   *     "id": 1,
   *     "nombre": "Fuerza Compromiso Social",
   *     "siglas": "FCS",
   *     "numeroLista": 20,
   *     "logoUrl": "https://cdn.electoral.ec/logos/fcs.png",
   *     "color": "#FF0000",
   *     "estado": "ACTIVO"
   *   }
   * ]
   */
  getPartidos(): Observable<PartidoDto[]> {
    if (!this.partidos$) {
      this.partidos$ = this.http
        .get<PartidoDto[]>(`${this.baseUrl}/partidos`)
        .pipe(shareReplay(1));
    }
    return this.partidos$;
  }

  /**
   * Crea un nuevo partido político.
   *
   * @param req PartidoRequest — Datos del partido (nombre, siglas, número lista, logo, color, estado)
   * @returns Observable<PartidoDto> — Partido creado con ID asignado
   *
   * @example
   * // Request:
   * {
   *   "nombre": "Unión por el Cambio",
   *   "siglas": "UC",
   *   "numeroLista": 21,
   *   "logoUrl": "https://cdn.electoral.ec/logos/uc.png",
   *   "color": "#0000FF",
   *   "estado": "ACTIVO"
   * }
   * // Response: { id: 2, ...request }
   */
  createPartido(req: PartidoRequest): Observable<PartidoDto> {
    return this.http.post<PartidoDto>(`${this.baseUrl}/partidos`, req);
  }

  /**
   * Actualiza un partido político existente.
   *
   * @param id number — ID del partido a actualizar
   * @param req PartidoRequest — Datos actualizados
   * @returns Observable<PartidoDto> — Partido actualizado
   *
   * @example
   * // Request:
   * {
   *   "nombre": "Unión por el Cambio Nacional",
   *   "siglas": "UCN",
   *   "numeroLista": 21,
   *   "logoUrl": "https://cdn.electoral.ec/logos/ucn.png",
   *   "color": "#0000FF",
   *   "estado": "ACTIVO"
   * }
   * // Response: { id: 2, ...request }
   */
  updatePartido(id: number, req: PartidoRequest): Observable<PartidoDto> {
    return this.http.put<PartidoDto>(`${this.baseUrl}/partidos/${id}`, req);
  }

  /**
   * Elimina un partido político.
   *
   * @param id number — ID del partido a eliminar
   * @returns Observable<void>
   *
   * @throws 409 Conflict si existen candidatos asignados a este partido
   * @throws 400 Bad Request si hay otros conflictos
   */
  deletePartido(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/partidos/${id}`);
  }

  // ============================================================================
  // CANDIDATO METHODS
  // ============================================================================

  /**
   * Obtiene listado de candidatos para todos los cargos y partidos.
   * Implementa caché con shareReplay(1).
   *
   * @returns Observable<CandidatoDto[]> — Listado de candidatos
   *
   * @example
   * // Backend response:
   * [
   *   {
   *     "id": 1,
   *     "nombre": "Juan Pérez García",
   *     "fotoUrl": "https://cdn.electoral.ec/fotos/juan-perez.jpg",
   *     "cargoId": 1,
   *     "partidoId": 1,
   *     "ordenLista": 1,
   *     "estado": "ACTIVO"
   *   }
   * ]
   */
  getCandidatos(): Observable<CandidatoDto[]> {
    if (!this.candidatos$) {
      this.candidatos$ = this.http
        .get<CandidatoDto[]>(`${this.baseUrl}/candidatos`)
        .pipe(shareReplay(1));
    }
    return this.candidatos$;
  }

  /**
   * Crea un nuevo candidato electoral.
   *
   * @param req CandidatoRequest — Datos del candidato (nombre, foto, cargoId, partidoId, orden lista, estado)
   * @returns Observable<CandidatoDto> — Candidato creado con ID asignado
   *
   * @example
   * // Request:
   * {
   *   "nombre": "María López Rodríguez",
   *   "fotoUrl": "https://cdn.electoral.ec/fotos/maria-lopez.jpg",
   *   "cargoId": 1,
   *   "partidoId": 1,
   *   "ordenLista": 1,
   *   "estado": "ACTIVO"
   * }
   * // Response: { id: 1, ...request }
   */
  createCandidato(req: CandidatoRequest): Observable<CandidatoDto> {
    return this.http.post<CandidatoDto>(`${this.baseUrl}/candidatos`, req);
  }

  /**
   * Actualiza un candidato electoral existente.
   *
   * @param id number — ID del candidato a actualizar
   * @param req CandidatoRequest — Datos actualizados
   * @returns Observable<CandidatoDto> — Candidato actualizado
   *
   * @example
   * // Request:
   * {
   *   "nombre": "María López Rodríguez García",
   *   "fotoUrl": "https://cdn.electoral.ec/fotos/maria-lopez-new.jpg",
   *   "cargoId": 1,
   *   "partidoId": 1,
   *   "ordenLista": 2,
   *   "estado": "ACTIVO"
   * }
   * // Response: { id: 1, ...request }
   */
  updateCandidato(id: number, req: CandidatoRequest): Observable<CandidatoDto> {
    return this.http.put<CandidatoDto>(`${this.baseUrl}/candidatos/${id}`, req);
  }

  /**
   * Elimina un candidato electoral.
   *
   * @param id number — ID del candidato a eliminar
   * @returns Observable<void>
   *
   * @throws 400 Bad Request si hay conflictos de integridad
   */
  deleteCandidato(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/candidatos/${id}`);
  }

  // ============================================================================
  // INVALIDACIÓN DE CACHÉ
  // ============================================================================

  /**
   * Invalida el caché de una o todas las entidades.
   * Llamar después de crear/actualizar/eliminar para refrescar datos en próxima carga.
   *
   * <p>Además de la caché de este panel, invalida la de los <b>otros</b>
   * servicios que sirven los mismos datos: `ObserverApi` cachea los partidos
   * (el formulario del observador) y `DashboardApi` cachea resumen, resultados,
   * progreso y novedades. Sin esto, un MASTER que crea un partido lo seguiría
   * sin ver en el formulario del observador hasta recargar la página.
   *
   * @param entity 'cargos' | 'partidos' | 'candidatos' | 'all' — Qué caché invalidar
   *
   * @example
   * this.api.createCargo(req).subscribe(() => {
   *   this.api.invalidateCache('cargos');
   * });
   */
  invalidateCache(entity: 'cargos' | 'partidos' | 'candidatos' | 'all'): void {
    if (entity === 'cargos' || entity === 'all') {
      this.cargos$ = null;
    }
    if (entity === 'partidos' || entity === 'all') {
      this.partidos$ = null;
      // El formulario del observador cachea los partidos: sin esto, el partido
      // nuevo no aparece en el formulario hasta que se recarga la pagina.
      this.observerApi.invalidateCache();
    }
    if (entity === 'candidatos' || entity === 'all') {
      this.candidatos$ = null;
    }
    // El dashboard agrega datos de configuracion (candidatos y partidos por
    // cargo): sus caches quedan viejas con el mismo cambio.
    this.dashboardApi.invalidateCache();
  }
}
