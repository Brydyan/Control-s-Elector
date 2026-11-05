import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { PagedResponse } from '../../../shared/models/page.model';
import {
  ActaDetalleCompleto,
  ActaFiltros,
  ActaItem,
  CandidatoResumen,
  CantonResumen,
  CargoResumen,
  CorregirActaPayload,
  DisputarActaPayload,
  HistorialEntry,
  JrvResumen,
} from '../domain/models/coordinator.model';

/**
 * Shape que retorna el backend en GET /coordinator/actas/{id}
 * (ActaDetalleCompletoDto.java). Mapeado al modelo FE en
 * {@link CoordinatorService.toActaDetalleCompleto}.
 */
interface BackendActaDetalleCompleto {
  id: number;
  jrvId: number;
  cargoId: number;
  registradaPorId?: number | null;
  estado: ActaItem['estado'];
  totalSufragantes?: number | null;
  votosBlancos?: number | null;
  votosNulos?: number | null;
  fechaRegistro: string;
  motivoAnulacion?: string | null;
  nombreCargo?: string | null;
  numeroJrv?: string | null;
  totalVotosCandidatos?: number | null;
  detalles?: Array<{ candidatoId: number; votos: number }> | null;
  urlEvidencia?: string | null;
  motivoDisputa?: string | null;
}

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
export class CoordinatorApi {
  private readonly http = inject(HttpClient);
  private readonly logger = {
    info: console.log,
    error: console.error,
    warn: console.warn,
  };
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
    if (filtros?.cantonId) {
      params = params.set('cantonId', filtros.cantonId.toString());
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
      .get<PagedResponse<ActaItem>>(`${this.baseUrl}/actas`, {
        params,
      })
      .pipe(map((res) => this.toPagedActas(res)));
  }

  private toPagedActas(res: PagedResponse<ActaItem>): PagedResponse<ActaItem> {
    // The API contract is the { data, meta } envelope. A bare array (or any
    // other shape) means the contract regressed — fail loudly instead of
    // silently wrapping it, so the regression surfaces in tests and logs.
    if (!res || Array.isArray(res) || !Array.isArray(res.data) || !res.meta) {
      console.error(
        '[CoordinatorService] Unexpected actas response shape',
        res,
      );
      throw new Error(
        'Unexpected actas response: expected { data, meta } envelope',
      );
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

  getCantones(): Observable<CantonResumen[]> {
    return this.http
      .get<CantonResumen[]>(`${this.configUrl}/cantones`)
      .pipe(map((cantones) => cantones ?? []));
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
    // T-FE-10 E.2: el backend expone endpoint único que retorna
    // ActaDetalleCompletoDto con `detalles` (votos reales por candidato),
    // `urlEvidencia` (URL prefirmada S3), `motivoDisputa`, etc.
    // Cruzamos con el catálogo de candidatos del cargo para completar
    // nombre, partido y lista.
    return this.http
      .get<BackendActaDetalleCompleto>(`${this.baseUrl}/actas/${actaId}`)
      .pipe(
        switchMap((raw) => {
          if (!raw.cargoId || !raw.detalles || raw.detalles.length === 0) {
            return of(this.toActaDetalleCompleto(raw, []));
          }
          return this.getCandidatosPorCargo(raw.cargoId).pipe(
            map((candidatosCargo) =>
              this.toActaDetalleCompleto(raw, candidatosCargo),
            ),
          );
        }),
      );
  }

  private toActaDetalleCompleto(
    raw: BackendActaDetalleCompleto,
    candidatosCatalogo: CandidatoResumen[] = [],
  ): ActaDetalleCompleto {
    const catalogoMap = new Map(candidatosCatalogo.map((c) => [c.id, c]));

    const acta: ActaItem = {
      id: raw.id,
      jrvId: raw.jrvId,
      cargoId: raw.cargoId,
      registradaPorId: raw.registradaPorId ?? undefined,
      estado: raw.estado,
      urlEvidencia: raw.urlEvidencia ?? null,
      totalSufragantes: raw.totalSufragantes ?? 0,
      votosBlancos: raw.votosBlancos ?? 0,
      votosNulos: raw.votosNulos ?? 0,
      fechaRegistro: raw.fechaRegistro,
      motivoAnulacion: raw.motivoAnulacion ?? null,
      motivoDisputa: raw.motivoDisputa ?? null,
      nombreCargo: raw.nombreCargo ?? null,
      numeroJrv: raw.numeroJrv ?? null,
      totalVotosCandidatos: raw.totalVotosCandidatos ?? null,
      detalles: raw.detalles ?? null,
    };

    const candidatos: CandidatoResumen[] = (raw.detalles ?? []).map((d) => {
      const match = catalogoMap.get(d.candidatoId);
      return {
        id: d.candidatoId,
        nombre: match?.nombre || '',
        orden: match?.orden || 0,
        cargoId: raw.cargoId,
        partidoId: match?.partidoId || 0,
        partidoNombre: match?.partidoNombre,
        partidoSiglas: match?.partidoSiglas,
        partidoNumeroLista: match?.partidoNumeroLista,
        votos: d.votos,
      };
    });

    const jrv: JrvResumen | undefined = raw.numeroJrv
      ? { id: raw.jrvId, numero: raw.numeroJrv, parroquia: null }
      : undefined;

    const cargo: CargoResumen | undefined = raw.nombreCargo
      ? { id: raw.cargoId, nombre: raw.nombreCargo, nivel: '' }
      : undefined;

    // Checksum exacto: SUM(detalles.votos) + blancos + nulos == total.
    const totalVotosCalculado =
      (raw.totalVotosCandidatos ??
        candidatos.reduce((s, c) => s + c.votos, 0)) +
      (raw.votosBlancos ?? 0) +
      (raw.votosNulos ?? 0);
    const diferenciaChecksum =
      totalVotosCalculado - (raw.totalSufragantes ?? 0);
    const checksumValido =
      (raw.totalSufragantes ?? 0) > 0 && diferenciaChecksum === 0;

    return {
      acta,
      jrv,
      cargo,
      candidatos,
      historial: [],
      totalVotosCalculado,
      diferenciaChecksum,
      checksumValido,
    };
  }

  getHistorial(actaId: number): Observable<HistorialEntry[]> {
    return this.http
      .get<HistorialEntry[]>(`${this.baseUrl}/actas/${actaId}/historial`)
      .pipe(map((h) => h ?? []));
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

  /**
   * T-FE-10 E.1: el backend `DELETE /coordinator/actas/{id}` requiere
   * `@Valid @RequestBody AnulacionDto` con `motivo` obligatorio.
   * Enviar el motivo en el body evita 400 `MissingRequestBody`.
   */
  /**
   * T-FE-10 E.4: subida de imagen de contingencia (T-BE-18). El COORDINATOR
   * puede subir el respaldo fotográfico cuando el OBSERVER no pudo hacerlo.
   * Mismo `FormData` con clave `'imagen'` que el endpoint del OBSERVER.
   */
  subirImagenContingencia(actaId: number, file: File): Observable<void> {
    const formData = new FormData();
    formData.append('imagen', file);
    return this.http.post<void>(
      `${this.baseUrl}/actas/${actaId}/imagen`,
      formData,
    );
  }

  /**
   * T-FE-10 E.4 + T-FE-17: Descarga del expediente de impugnación CNE en PDF (T-BE-20).
   * Devuelve `Blob` con `application/pdf` para abrir o descargar.
   *
   * @param actaId — ID del acta (debe ser número > 0)
   * @returns Observable<Blob> — Contenido PDF
   * @throws Lanza error si actaId es inválido
   *
   * **Validaciones**:
   * - actaId debe ser número positivo (rechaza null, negativo, string)
   * - Loguea inicio de descarga con contexto
   * - Error handling: 401, 403, 404, 500 con mensajes específicos
   */
  descargarExpedientePdf(actaId: number): Observable<Blob> {
    // Validar actaId
    if (!Number.isInteger(actaId) || actaId <= 0) {
      this.logger.error('Invalid actaId for PDF download', {
        actaId,
        tipo: typeof actaId,
      });
      throw new Error(`Invalid actaId: ${actaId}. Must be a positive integer.`);
    }

    this.logger.info('Descargando expediente para acta', { actaId });

    return this.http.get(`${this.baseUrl}/actas/${actaId}/expediente-pdf`, {
      responseType: 'blob',
    });
  }

  anularActa(actaId: number, motivo: string): Observable<void> {
    // Backend returns 204 No Content; HttpClient errors on 2xx without a body
    // unless we ask for { responseType: 'text' } + observe: 'response'.
    return this.http
      .delete(`${this.baseUrl}/actas/${actaId}`, {
        body: { motivo },
        observe: 'response',
        responseType: 'text',
      })
      .pipe(map(() => undefined));
  }
}
