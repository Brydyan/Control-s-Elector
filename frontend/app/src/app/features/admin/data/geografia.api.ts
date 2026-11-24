import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { ConfigService } from '../../../core/config/config.service';
import { mapError } from '../../../shared/errors/error-handler';

export interface JrvNode {
  id: number;
  numero: string;
  recinto?: string;
  totalElectores?: number;
}

export interface ParroquiaNode {
  id: number;
  nombre: string;
  esUrbana: boolean;
  jrvs?: JrvNode[];
}

export interface CantonNode {
  id: number;
  nombre: string;
  codigo?: string;
  parroquias: ParroquiaNode[];
}

export interface ProvinciaNode {
  id: number;
  nombre: string;
  codigo?: string;
  cantones: CantonNode[];
}

export interface GeografiaTreeDto {
  provincias: ProvinciaNode[];
}

@Injectable({ providedIn: 'root' })
export class GeografiaApi {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);
  private get baseUrl(): string {
    return `${this.configService.getApiUrlSafe()}/master`;
  }

  /**
   * Obtiene el árbol completo de geografía (provincia → canton → parroquia).
   * Se cachea en cliente para evitar llamadas repetidas.
   */
  obtenerGeografia(): Observable<GeografiaTreeDto> {
    return this.http
      .get<GeografiaTreeDto>(`${this.baseUrl}/geografia`)
      .pipe(catchError((err) => this.handleError(err)));
  }

  private handleError(err: unknown): Observable<never> {
    return throwError(() => mapError(err));
  }
}
