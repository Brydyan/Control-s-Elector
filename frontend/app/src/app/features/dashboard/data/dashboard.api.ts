import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, catchError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  DashboardData,
  Novedad,
  ProgresoCanton,
  ResumenActas,
  ResultadoCargo,
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
