import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConfigService } from '../../../core/config/config.service';
import { EstadoActa } from '../domain/models/observer.model';

/**
 * Acta tal como la devuelve la API.
 *
 * <p>Es el `ActaResponseDto` del backend: los mismos campos y los mismos
 * nombres. Antes esta interfaz describia un acta que el backend nunca emits
 * (`numeroActa`, `jrvNombre`, `fechaTransmision`, `accionLabel`), de modo que
 * la pantalla "Mis Actas" no podia funcionar contra la API real.
 */
export interface ActaDTO {
  id: number;
  jrvId: number;
  cargoId: number;
  registradaPorId: number;
  estado: EstadoActa;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  fechaRegistro: string;
  motivoAnulacion: string | null;
  nombreCargo: string;
  numeroJrv: string;
  totalVotosCandidatos: number | null;
}

/**
 * Metadatos de la pagina, tal como los manda `PagedResponse` del backend.
 */
export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Envoltura paginada de la API.
 *
 * <p>El backend responde `{data, meta}`: no es el envelope de Spring
 * (`{content, currentPage}`), y leer el equivocado deja la tabla vacia.
 */
export interface PageResult<T> {
  data: T[];
  meta: PageMeta;
}

export interface ActaFilters {
  jrvId?: string | null;
  estado?: string | null;
  page: number;
  size: number;
}

@Injectable({
  providedIn: 'root',
})
export class MisActasService {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);
  private get baseUrl(): string {
    return `${this.configService.getApiUrlSafe()}/observer/mis-actas`;
  }

  getMisActas(filters: ActaFilters): Observable<PageResult<ActaDTO>> {
    let params = new HttpParams()
      .set('page', filters.page.toString())
      .set('size', filters.size.toString());

    if (filters.estado) {
      params = params.set('estado', filters.estado);
    }

    if (filters.jrvId) {
      params = params.set('jrvId', filters.jrvId);
    }

    return this.http.get<PageResult<ActaDTO>>(this.baseUrl, { params });
  }
}
