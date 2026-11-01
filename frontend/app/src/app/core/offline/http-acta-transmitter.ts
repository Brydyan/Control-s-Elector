import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ActaTransmitter, OpcionesTransmision } from './sync.service';

/**
 * Transmisor HTTP de la cola offline.
 *
 * Es el transmisor por defecto del `SyncService`: mantiene el MISMO contrato
 * HTTP que el observador (mismos endpoints, mismos cuerpos) y es el que
 * reenvía la cabecera de idempotencia a la petición. El token
 * `ACTA_TRANSMITTER` tiene prioridad sobre este: si la aplicación enlaza un
 * transmisor propio, éste no se usa.
 */
export class HttpActaTransmitter implements ActaTransmitter {
  private readonly baseUrl = `${environment.apiUrl}/observer`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Paso 1: `POST /observer/actas`.
   *
   * `opciones.headers` viaja como cabecera HTTP: el backend tiene que
   * honrar `Idempotency-Key` para deduplicar un reintento. El tracking local
   * (`actaEnviada`) es la garantía que no depende del servidor.
   */
  enviarActaDirecta(
    payload: unknown,
    opciones?: OpcionesTransmision,
  ): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(`${this.baseUrl}/actas`, payload, {
      headers: opciones?.headers ?? {},
    });
  }

  /** Paso 2: `POST /observer/actas/{id}/imagen` (multipart). */
  subirImagenActa(actaId: number, blob: Blob): Observable<void> {
    const formData = new FormData();
    formData.append('imagen', blob, nombreEvidencia(blob));
    return this.http.post<void>(
      `${this.baseUrl}/actas/${actaId}/imagen`,
      formData,
    );
  }
}

/**
 * Nombre de archivo para la evidencia. El blob de la cola no tiene nombre
 * (viene de la cámara comprimida), pero la extensión ayuda a que el backend
 * infiera el tipo sin adivinar.
 */
function nombreEvidencia(blob: Blob): string {
  const subtipo = blob.type.split('/')[1]?.split('+')[0] ?? '';
  const extension = /^[a-z0-9]+$/i.test(subtipo) ? subtipo : 'webp';
  return `evidencia.${extension}`;
}
