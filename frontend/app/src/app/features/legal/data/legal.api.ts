import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ConfigService } from '../../../core/config/config.service';
import {
  AcceptTermsResponseDto,
  DpoContactRequestDto,
  DpoContactResponseDto,
  ExportDataDto,
  LegalVersionDto,
} from './legal.dto';

/**
 * Capa HTTP del bloque legal.
 *
 * Sin estado y sin efectos: solo habla con el backend. Los endpoints cuelgan
 * de ConfigService.getApiUrl() (`/api/v1`), asi que el interceptor de auth les
 * pega el token en memoria cuando hace falta y no lo manda a terceros.
 */
@Injectable({ providedIn: 'root' })
export class LegalApi {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);
  private readonly base = this.configService.getApiUrlSafe();

  /**
   * Version vigente de los documentos legales.
   *
   * Publica a proposito: la version de un documento no es informacion sensible
   * y asi el frontend puede decidir antes de tener sesion.
   */
  versionVigente(): Observable<LegalVersionDto> {
    return this.http.get<LegalVersionDto>(`${this.base}/legal/current-version`);
  }

  /** Registra la aceptacion de terminos y de la politica de privacidad. */
  aceptarTerminos(): Observable<AcceptTermsResponseDto> {
    return this.http.post<AcceptTermsResponseDto>(
      `${this.base}/user/accept-terms`,
      {},
    );
  }

  /** Exporta los datos personales del usuario (GDPR Art. 15). */
  exportarDatos(): Observable<ExportDataDto> {
    return this.http.get<ExportDataDto>(`${this.base}/user/export-data`);
  }

  /** Envia una solicitud al Delegado de Proteccion de Datos. */
  contactoDpo(pedido: DpoContactRequestDto): Observable<DpoContactResponseDto> {
    return this.http.post<DpoContactResponseDto>(
      `${this.base}/legal/contact-dpo`,
      pedido,
    );
  }
}
