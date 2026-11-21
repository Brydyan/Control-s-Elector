import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, catchError, shareReplay } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  DashboardData,
  Novedad,
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

  /** T-FE-15 B.1: Cache de resumen. */
  private resumen$: Observable<ResumenActas> | null = null;

  /** T-FE-15 B.1: Cache de resultados base. */
  private resultados$: Observable<ResultadoCargo[]> | null = null;

  /** T-FE-15 B.1: Cache de progreso base. */
  private progreso$: Observable<ProgresoCantonDto[]> | null = null;

  /** T-FE-15 B.1: Cache de novedades. */
  private novedades$: Observable<Novedad[]> | null = null;

  getResumen(): Observable<ResumenActas> {
    if (!this.resumen$) {
      this.resumen$ = this.http
        .get<ResumenActas>(`${this.baseUrl}/resumen`)
        .pipe(shareReplay(1));
    }
    return this.resumen$;
  }

  getResultados(): Observable<ResultadoCargo[]> {
    if (!this.resultados$) {
      this.resultados$ = this.http
        .get<ResultadoCargo[]>(`${this.baseUrl}/resultados`)
        .pipe(shareReplay(1));
    }
    return this.resultados$;
  }

  /**
   * Progreso por canton con el desglose por parroquia.
   *
   * <p>El backend devuelve las parroquias anidadas en cada canton (`ProgresoCantonDto`), asi que
   * el tipo de la respuesta es el DTO completo y no la base `ProgresoCanton`.
   */
  getProgreso(): Observable<ProgresoCantonDto[]> {
    if (!this.progreso$) {
      this.progreso$ = this.http
        .get<ProgresoCantonDto[]>(`${this.baseUrl}/progreso`)
        .pipe(shareReplay(1));
    }
    return this.progreso$;
  }

  getNovedades(): Observable<Novedad[]> {
    if (!this.novedades$) {
      this.novedades$ = this.http
        .get<Novedad[]>(`${this.baseUrl}/novedades`)
        .pipe(shareReplay(1));
    }
    return this.novedades$;
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
   * Consume el endpoint canónico `/resultados` y enriquece los datos calculando votos válidos,
   * porcentajes y estados provisionales.
   */
  getResultadosDetalles(): Observable<ResultadoCargoDto[]> {
    if (!this.resultadosDetalles$) {
      this.resultadosDetalles$ = this.getResultados().pipe(
        map((cargos) =>
          cargos.map((cargo) => {
            const votosValidos = cargo.candidatos.reduce(
              (acc, c) => acc + (c.votos || 0),
              0,
            );
            const maxVotos =
              cargo.candidatos.length > 0
                ? Math.max(...cargo.candidatos.map((c) => c.votos || 0))
                : 0;

            const candidatos = cargo.candidatos.map((c) => {
              const votos = c.votos || 0;
              const porcentaje =
                votosValidos > 0 ? (votos / votosValidos) * 100 : 0;
              const esLider = votos === maxVotos && votos > 0;
              return {
                ...c,
                porcentaje,
                estado: esLider ? 'GANADOR_PROVISIONAL' : 'EN_CONTEO',
                fotoUrl: null,
              };
            });

            return {
              cargoId: cargo.cargoId,
              cargoNombre: cargo.cargoNombre,
              votosValidos,
              votosBlancos: 0,
              votosNulos: 0,
              candidatos,
            };
          }),
        ),
        shareReplay(1),
      );
    }
    return this.resultadosDetalles$;
  }

  /**
   * T-FE-15 A.2: Obtener progreso por cantón con desglose de parroquias.
   * Cachea en memoria via shareReplay(1).
   * Consume el endpoint canónico `/progreso`, que ya devuelve el desglose anidado.
   *
   * <p>Antes esta capa fabricaba un `parroquias ?? []` porque el backend no mandaba el
   * desglose: eso convertia un contrato roto en un panel vacio y silencioso. Ahora el dato
   * llega del backend y la ausencia se ve como estado vacío en la UI, no como una tabla muda.
   */
  getProgresoDetalles(): Observable<ProgresoCantonDto[]> {
    if (!this.progresoDetalles$) {
      this.progresoDetalles$ = this.getProgreso().pipe(shareReplay(1));
    }
    return this.progresoDetalles$;
  }

  /**
   * T-FE-15: Invalidar caché (útil para refresh manual en UI).
   * Llamar en caso de que el usuario dispare un "Actualizar" explícito.
   */
  invalidateCache(): void {
    this.resumen$ = null;
    this.resultados$ = null;
    this.progreso$ = null;
    this.novedades$ = null;
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
