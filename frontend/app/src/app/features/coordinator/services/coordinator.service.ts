import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, switchMap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { PagedResponse } from '../../../shared/models/page.model';
import {
  ActaDetalleCompleto,
  ActaFiltros,
  ActaItem,
  CandidatoResumen,
  CargoResumen,
  CorregirActaPayload,
  DisputarActaPayload,
  JrvResumen,
} from '../models/coordinator.model';

interface PartidoDto {
  id: number;
  nombre: string;
  siglas: string;
  numeroLista: number;
}

interface CandidatoDto {
  id: number;
  nombre: string;
  orden: number;
  cargoId: number;
  partidoId: number;
}

interface CargoDto {
  id: number;
  nombre: string;
  nivel: string;
}

interface JrvConEstadoDto {
  jrvId: number;
  numero: string;
  parroquia: string | null;
  cargosPendientes: number;
  cargosRegistrados: number;
}

@Injectable({
  providedIn: 'root',
})
export class CoordinatorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/coordinator`;
  private readonly configUrl = `${environment.apiUrl}/configuracion`;
  private readonly observerUrl = `${environment.apiUrl}/observer`;

  getActas(filtros?: ActaFiltros): Observable<PagedResponse<ActaItem>> {
    let params = new HttpParams();
    if (filtros?.estado && filtros.estado !== 'TODAS') {
      params = params.set('estado', filtros.estado);
    }
    if (filtros?.jrvId) {
      params = params.set('jrvId', filtros.jrvId.toString());
    }
    // dignidad ≡ cargo: the UI speaks of "dignidades", the API of cargoId.
    const cargoId = filtros?.cargoId ?? filtros?.dignidad;
    if (cargoId) {
      params = params.set('cargoId', cargoId.toString());
    }
    const search = filtros?.search ?? filtros?.busqueda;
    if (search?.trim()) {
      params = params.set('search', search.trim());
    }
    if (filtros?.page !== undefined) {
      params = params.set('page', filtros.page.toString());
    }
    if (filtros?.size !== undefined) {
      params = params.set('size', filtros.size.toString());
    }
    if (filtros?.sort) {
      params = params.set('sort', filtros.sort);
    }
    if (filtros?.dir) {
      params = params.set('dir', filtros.dir);
    }

    return this.http
      .get<PagedResponse<ActaItem> | ActaItem[]>(`${this.baseUrl}/actas`, {
        params,
      })
      .pipe(map((res) => this.toPagedActas(res)));
  }

  private toPagedActas(
    res: PagedResponse<ActaItem> | ActaItem[],
  ): PagedResponse<ActaItem> {
    // Temporary compat: if the backend ever returns a bare array, wrap it
    // instead of breaking. The main path is the { data, meta } envelope.
    if (Array.isArray(res)) {
      return {
        data: res,
        meta: {
          page: 0,
          size: res.length,
          totalElements: res.length,
          totalPages: 1,
        },
      };
    }
    return res;
  }

  getActaById(id: number): Observable<ActaItem> {
    return this.http.get<ActaItem>(`${this.baseUrl}/actas/${id}`);
  }

  getCargos(): Observable<CargoResumen[]> {
    return this.http.get<CargoDto[]>(`${this.configUrl}/cargos`).pipe(
      map((cargos) =>
        cargos.map((c) => ({
          id: c.id,
          nombre: c.nombre,
          nivel: c.nivel,
        })),
      ),
    );
  }

  getJrvs(): Observable<JrvResumen[]> {
    return this.http.get<JrvConEstadoDto[]>(`${this.observerUrl}/jrvs`).pipe(
      map((jrvs) =>
        jrvs.map((j) => ({
          id: j.jrvId,
          numero: j.numero,
          parroquia: j.parroquia,
        })),
      ),
    );
  }

  getCandidatosPorCargo(cargoId: number): Observable<CandidatoResumen[]> {
    return forkJoin({
      candidatos: this.http.get<CandidatoDto[]>(
        `${this.configUrl}/candidatos?cargoId=${cargoId}`,
      ),
      partidos: this.http.get<PartidoDto[]>(`${this.configUrl}/partidos`),
    }).pipe(
      map(({ candidatos, partidos }) => {
        const partidosMap = new Map(partidos.map((p) => [p.id, p]));
        return candidatos.map((c) => {
          const p = partidosMap.get(c.partidoId);
          return {
            id: c.id,
            nombre: c.nombre,
            orden: c.orden,
            cargoId: c.cargoId,
            partidoId: c.partidoId,
            partidoNombre: p?.nombre,
            partidoSiglas: p?.siglas,
            partidoNumeroLista: p?.numeroLista,
            votos: 0,
          };
        });
      }),
    );
  }

  getActaDetalleCompleto(actaId: number): Observable<ActaDetalleCompleto> {
    return this.getActaById(actaId).pipe(
      switchMap((acta) =>
        forkJoin({
          cargos: this.getCargos(),
          jrvs: this.getJrvs(),
          candidatos: this.getCandidatosPorCargo(acta.cargoId),
        }).pipe(
          map(({ cargos, jrvs, candidatos }) => {
            const cargo = cargos.find((c) => c.id === acta.cargoId);
            const jrv = jrvs.find((j) => j.id === acta.jrvId);

            const totalVotosCalculado =
              (acta.votosBlancos || 0) + (acta.votosNulos || 0);
            const diferenciaChecksum =
              totalVotosCalculado - (acta.totalSufragantes || 0);
            const checksumValido =
              acta.totalSufragantes > 0 &&
              totalVotosCalculado <= acta.totalSufragantes;

            return {
              acta,
              cargo,
              jrv,
              candidatos,
              totalVotosCalculado,
              diferenciaChecksum,
              checksumValido,
            };
          }),
        ),
      ),
    );
  }

  aprobarActa(actaId: number): Observable<ActaItem> {
    return this.http.put<ActaItem>(
      `${this.baseUrl}/actas/${actaId}/aprobar`,
      {},
    );
  }

  disputarActa(
    actaId: number,
    payload: DisputarActaPayload,
  ): Observable<ActaItem> {
    return this.http.put<ActaItem>(
      `${this.baseUrl}/actas/${actaId}/disputar`,
      payload,
    );
  }

  corregirActa(
    actaId: number,
    payload: CorregirActaPayload,
  ): Observable<ActaItem> {
    return this.http.put<ActaItem>(
      `${this.baseUrl}/actas/${actaId}/corregir`,
      payload,
    );
  }
}
