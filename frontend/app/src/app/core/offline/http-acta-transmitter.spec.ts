import {
  HttpClient,
  provideHttpClient,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { HttpActaTransmitter } from './http-acta-transmitter';
import { IDEMPOTENCY_HEADER } from './sync.service';

/**
 * Tests del transmisor HTTP de la cola offline (S3).
 *
 * El contrato HTTP no cambia: mismos endpoints y mismos cuerpos que el
 * observador. Lo único que se agrega es la cabecera de idempotencia.
 */
describe('HttpActaTransmitter', () => {
  let httpMock: HttpTestingController;
  let transmitter: HttpActaTransmitter;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    transmitter = new HttpActaTransmitter(TestBed.inject(HttpClient));
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('S3: should POST the acta with the idempotency key as a request header', () => {
    let respuesta: { id: number } | undefined;
    transmitter
      .enviarActaDirecta(
        { totalSufragantes: 10 },
        { headers: { [IDEMPOTENCY_HEADER]: 'acta-uuid-1' } },
      )
      .subscribe((r) => (respuesta = r));

    const req = httpMock.expectOne(`${environment.apiUrl}/observer/actas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('acta-uuid-1');
    // El cuerpo es el mismo de siempre: la clave va en la cabecera.
    expect(req.request.body).toEqual({ totalSufragantes: 10 });

    req.flush({ id: 9 });
    expect(respuesta).toEqual({ id: 9 });
  });

  it('S3: should POST the image as multipart on the acta id', () => {
    transmitter
      .subirImagenActa(77, new Blob(['pixeles'], { type: 'image/webp' }))
      .subscribe();

    const req = httpMock.expectOne(
      `${environment.apiUrl}/observer/actas/77/imagen`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    expect((req.request.body as FormData).get('imagen')).toBeTruthy();
    req.flush(null);
  });
});
