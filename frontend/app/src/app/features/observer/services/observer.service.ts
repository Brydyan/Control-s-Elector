import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  ActaRequest,
  ActaResponse,
  CandidatoInfo,
  CargoConEstado,
  JrvConEstado,
} from '../models/observer.model';

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

@Injectable({
  providedIn: 'root',
})
export class ObserverService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/observer`;
  private readonly configUrl = `${environment.apiUrl}/configuracion`;

  getMisJrvs(): Observable<JrvConEstado[]> {
    return this.http.get<JrvConEstado[]>(`${this.baseUrl}/jrvs`);
  }

  getCargosDeJrv(jrvId: number): Observable<CargoConEstado[]> {
    return this.http.get<CargoConEstado[]>(
      `${this.baseUrl}/jrv/${jrvId}/cargos`,
    );
  }

  getCandidatosPorCargo(cargoId: number): Observable<CandidatoInfo[]> {
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
            ...c,
            partidoNombre: p?.nombre,
            partidoSiglas: p?.siglas,
            partidoNumeroLista: p?.numeroLista,
          };
        });
      }),
    );
  }

  guardarBorrador(acta: ActaRequest): Observable<ActaResponse> {
    return this.http.post<ActaResponse>(`${this.baseUrl}/actas/borrador`, acta);
  }

  enviarActaDirecta(acta: ActaRequest): Observable<ActaResponse> {
    return this.http.post<ActaResponse>(`${this.baseUrl}/actas`, acta);
  }

  enviarBorrador(actaId: number): Observable<ActaResponse> {
    return this.http.post<ActaResponse>(
      `${this.baseUrl}/actas/${actaId}/enviar`,
      {},
    );
  }

  subirImagenActa(actaId: number, file: File): Observable<void> {
    const formData = new FormData();
    formData.append('imagen', file);
    return this.http.post<void>(
      `${this.baseUrl}/actas/${actaId}/imagen`,
      formData,
    );
  }

  getMisActas(): Observable<ActaResponse[]> {
    return this.http.get<ActaResponse[]>(`${this.baseUrl}/mis-actas`);
  }

  getCorreccionesPendientes(): Observable<ActaResponse[]> {
    return this.http.get<ActaResponse[]>(
      `${this.baseUrl}/correcciones-pendientes`,
    );
  }
}
