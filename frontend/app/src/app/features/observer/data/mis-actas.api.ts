import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface ActaDTO {
  id: string;
  numeroActa: string;
  jrvNombre: string;
  fechaTransmision: string; // ISO 8601
  estado: 'TRANSMITIDO' | 'REVISION' | 'BORRADOR' | 'ENVIADA';
  accionLabel: string;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
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
  private readonly baseUrl = `${environment.apiUrl}/observer/mis-actas`;

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
