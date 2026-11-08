import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { describirErrorLegal } from './legal.service';

/**
 * Tests de la traduccion de errores del bloque legal.
 *
 * <p>El caso que importa es el 429 del formulario del DPO: el limite es de 30 segundos por IP, asi
 * que el usuario necesita saber <i>cuando</i> puede reintentar, no solo que hay un error. El
 * header {@code Retry-After} es la unica fuente de ese dato.
 */
describe('describirErrorLegal', () => {
  it('un 429 con Retry-After dice cuanto falta para reintentar', () => {
    const err = new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders({ 'Retry-After': '15' }),
    });

    const descrito = describirErrorLegal(err);

    expect(descrito.status).toBe(429);
    expect(descrito.retryAfter).toBe(15);
    expect(descrito.mensaje).toContain('15');
  });

  it('un 429 sin Retry-After usa la ventana por defecto', () => {
    const err = new HttpErrorResponse({ status: 429 });

    const descrito = describirErrorLegal(err);

    expect(descrito.retryAfter).toBe(30);
    expect(descrito.mensaje).toContain('30');
  });

  it('el mensaje del backend se respeta cuando viene', () => {
    const err = new HttpErrorResponse({
      status: 429,
      error: {
        message: 'Max 1 request per 30 seconds. Retry after 15 seconds.',
      },
      headers: new HttpHeaders({ 'Retry-After': '15' }),
    });

    const descrito = describirErrorLegal(err);

    expect(descrito.mensaje).toBe(
      'Max 1 request per 30 seconds. Retry after 15 seconds.',
    );
  });

  it('un error de red se distingue de un error del servidor', () => {
    const err = new HttpErrorResponse({ status: 0 });

    const descrito = describirErrorLegal(err);

    expect(descrito.status).toBe(0);
    expect(descrito.mensaje).toContain('conexión');
  });

  it('un error que no es HTTP tambien produce un mensaje', () => {
    const descrito = describirErrorLegal(new Error('boom'));

    expect(descrito.mensaje.length).toBeGreaterThan(0);
    expect(descrito.status).toBe(0);
  });
});
