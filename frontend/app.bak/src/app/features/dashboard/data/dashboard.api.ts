import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, catchError, shareReplay } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  DashboardData,
  Novedad,
  ProgresoCanton,
  ProgresoCantonDto,
  ResumenActas,
  ResultadoCargo,
  ResultadoCargoDto,
} from '../domain/models/dashboard.model';

/**
 * T-FE-11 A.2: dashboard consume 4 endpoints REST canónicos
 * `/api/v1/dashboard/*`. Backend excluye ANULADA (ver `DashboardController`).
 *
 * G3: rutas canónicas inalterables. NO cambiar `/api/v1/dashboard/*` a
 * `/api/resultados/consolidado` (ver D4 obsoleto en design.md).
 */
@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/dashboard`;

  /** T-FE-15 A.2: Cache de resultados con detalles. Nunca refetch, compartir entre suscriptores. */
  private resultadosDetalles$: Observable<ResultadoCargoDto[]> | null = null;

  /** T-FE-15 A.2: Cache de progreso con desglose de parroquias. */
  private progresoDetalles$: Observable<ProgresoCantonDto[]> | null = null;

  getResumen(): Observable<ResumenActas> {
    return this.http.get<ResumenActas>(`${this.baseUrl}/resumen`);
  }

  getResultados(): Observable<ResultadoCargo[]> {
    return this.http.get<ResultadoCargo[]>(`${this.baseUrl}/resultados`);
  }

  getProgreso(): Observable<ProgresoCanton[]> {
    return this.http.get<ProgresoCanton[]>(`${this.baseUrl}/progreso`);
  }

  getNovedades(): Observable<Novedad[]> {
    return this.http.get<Novedad[]>(`${this.baseUrl}/novedades`);
  }

  /**
   * S1: carga inicial consolidada via forkJoin. Si un endpoint falla
   * individualmente, los demás siguen disponibles y el componente
   * recibe payload parcial con fallback `[]` o ceros para ese slot.
   */
  getDashboardCompleto(): Observable<DashboardData> {
    return forkJoin({
      resumen: this.getResumen().pipe(
        catchError(() => of(this.emptyResumen())),
      ),
      resultados: this.getResultados().pipe(catchError(() => of([]))),
      progreso: this.getProgreso().pipe(catchError(() => of([]))),
      novedades: this.getNovedades().pipe(catchError(() => of([]))),
    }).pipe(
      map(({ resumen, resultados, progreso, novedades }) => ({
        resumen,
        resultados,
        progreso,
        novedades,
      })),
    );
  }

  /**
   * T-FE-15 A.2: Obtener resultados detallados por cargo con estado, foto, porcentaje.
   * Cachea en memoria via shareReplay(1) — una sola llamada HTTP incluso con múltiples suscriptores.
   *
   * @returns Observable<ResultadoCargoDto[]> con detalles de candidatos, votos válidos/blancos/nulos.
   *
   * @example
   * // Backend esperado
   * [
   *   {
   *     cargoId: 1,
   *     cargoNombre: "Presidente",
   *     votosValidos: 250000,
   *     votosBlancos: 5000,
   *     votosNulos: 3000,
   *     candidatos: [
   *       {
   *         candidatoId: 101,
   *         candidatoNombre: "Juan Pérez",
   *         partidoNombre: "Movimiento X",
   *         votos: 120000,
   *         porcentaje: 48.0,
   *         estado: "GANADOR_PROVISIONAL",
   *         fotoUrl: "https://..."
   *       }
   *     ]
   *   }
   * ]
   */
  getResultadosDetalles(): Observable<ResultadoCargoDto[]> {
    if (!this.resultadosDetalles$) {
      this.resultadosDetalles$ = this.http
        .get<ResultadoCargoDto[]>(`${this.baseUrl}/resultados-detallados`)
        .pipe(shareReplay(1));
    }
    return this.resultadosDetalles$;
  }

  /**
   * T-FE-15 A.2: Obtener progreso por cantón con desglose de parroquias.
   * Cachea en memoria via shareReplay(1).
   *
   * @returns Observable<ProgresoCantonDto[]> con métricas territoriales.
   *
   * @example
   * // Backend esperado
   * [
   *   {
   *     cantonId: 1,
   *     cantonNombre: "Santa Elena",
   *     totalJrvs: 79,
   *     jrvsConActa: 42,
   *     porcentaje: 53.2,
   *     parroquias: [
   *       {
   *         parroquiaId: 10,
   *         parroquiaNombre: "Santa Elena",
   *         jrvsEscrutadas: 25,
   *         jrvsTotal: 40,
   *         avance: 62.5
   *       }
   *     ]
   *   }
   * ]
   */
  getProgresoDetalles(): Observable<ProgresoCantonDto[]> {
    if (!this.progresoDetalles$) {
      this.progresoDetalles$ = this.http
        .get<ProgresoCantonDto[]>(`${this.baseUrl}/progreso-detallado`)
        .pipe(shareReplay(1));
    }
    return this.progresoDetalles$;
  }

  /**
   * T-FE-15: Invalidar caché (útil para refresh manual en UI).
   * Llamar en caso de que el usuario dispare un "Actualizar" explícito.
   */
  invalidateCache(): void {
    this.resultadosDetalles$ = null;
    this.progresoDetalles$ = null;
  }

  private emptyResumen(): ResumenActas {
    return {
      enviadas: 0,
      enDisputa: 0,
      corregidas: 0,
      aprobadas: 0,
      totalActivas: 0,
    };
  }
}
