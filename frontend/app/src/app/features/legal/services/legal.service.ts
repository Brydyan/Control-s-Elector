import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { LegalApi } from '../data/legal.api';
import {
  DpoContactRequestDto,
  DpoContactResponseDto,
  ExportDataDto,
} from '../data/legal.dto';

/**
 * Casos de uso del bloque legal.
 *
 * Es la unica puerta que la UI usa: la UI no habla con `HttpClient` ni conoce
 * las rutas del backend.
 */
@Injectable({ providedIn: 'root' })
export class LegalService {
  private readonly api = inject(LegalApi);

  /**
   * Version vigente de los terminos y de la politica.
   *
   * Se toma `version` y, si el backend no lo manda, se cae a `terms_version`:
   * las dos son el mismo numero hoy, pero no tienen por que serlo mañana.
   */
  versionVigente(): Observable<string> {
    return this.api
      .versionVigente()
      .pipe(map((dto) => dto.version ?? dto.terms_version ?? ''));
  }

  /** Registra la aceptacion y devuelve la version que quedo aceptada. */
  aceptarTerminos(): Observable<string> {
    return this.api.aceptarTerminos().pipe(map((dto) => dto.version));
  }

  /** Exporta los datos personales del usuario (GDPR Art. 15). */
  exportarDatos(): Observable<ExportDataDto> {
    return this.api.exportarDatos();
  }

  /** Envia una solicitud al DPO. */
  contactoDpo(pedido: DpoContactRequestDto): Observable<DpoContactResponseDto> {
    return this.api.contactoDpo(pedido);
  }
}

/**
 * Descripcion de un error del bloque legal, lista para mostrar.
 *
 * El caso que importa es el 429 del formulario del DPO: el limite es de 30
 * segundos por IP, asi que el usuario necesita saber cuanto falta, no solo
 * que hay un error. El header `Retry-After` lo dice.
 */
export interface ErrorLegalDescrito {
  mensaje: string;
  /** Segundos que faltan para poder reintentar. 0 si el error no es de tasa. */
  retryAfter: number;
  status: number;
}

/**
 * Traduce un error del backend a un mensaje que se pueda mostrar.
 *
 * Es una funcion pura a proposito: no depende del HttpClient, asi que se
 * prueba sin mocks y sin red.
 */
export function describirErrorLegal(error: unknown): ErrorLegalDescrito {
  if (error instanceof HttpErrorResponse) {
    const retryAfter = Number(error.headers?.get('Retry-After') ?? 0);
    const mensajeBackend =
      typeof error.error?.message === 'string' ? error.error.message : '';

    if (error.status === 429) {
      const segundos = retryAfter > 0 ? retryAfter : 30;
      return {
        status: 429,
        retryAfter: segundos,
        mensaje:
          mensajeBackend ||
          `Demasiadas solicitudes. Intenta de nuevo en ${segundos} segundos.`,
      };
    }

    if (error.status === 0) {
      return {
        status: 0,
        retryAfter: 0,
        mensaje:
          'No hay conexión con el servidor. Revisa tu red e intenta otra vez.',
      };
    }

    return {
      status: error.status,
      retryAfter: retryAfter > 0 ? retryAfter : 0,
      mensaje:
        mensajeBackend ||
        'No se pudo completar la operación. Intenta otra vez en unos minutos.',
    };
  }

  return {
    status: 0,
    retryAfter: 0,
    mensaje: 'Ocurrió un error inesperado. Intenta otra vez.',
  };
}
