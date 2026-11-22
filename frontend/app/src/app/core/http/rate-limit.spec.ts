import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import {
  esRateLimit,
  esperaDeRateLimit,
  esperaLegible,
  retryAfterSegundos,
} from './rate-limit';

function error429(retryAfter?: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 429,
    headers:
      retryAfter === undefined
        ? new HttpHeaders()
        : new HttpHeaders({ 'Retry-After': retryAfter }),
  });
}

describe('rate-limit', () => {
  describe('esRateLimit', () => {
    it('reconoce un 429', () => {
      expect(esRateLimit(error429('10'))).toBe(true);
    });

    it('no confunde otros estados ni errores que no son HTTP', () => {
      expect(esRateLimit(new HttpErrorResponse({ status: 401 }))).toBe(false);
      expect(esRateLimit(new Error('429'))).toBe(false);
      expect(esRateLimit(null)).toBe(false);
    });
  });

  describe('retryAfterSegundos', () => {
    it('lee los segundos que manda el backend', () => {
      expect(retryAfterSegundos(error429('900'))).toBe(900);
    });

    it('sin header devuelve null', () => {
      expect(retryAfterSegundos(error429())).toBeNull();
    });

    it('un valor invalido, cero o negativo devuelve null', () => {
      expect(retryAfterSegundos(error429('abc'))).toBeNull();
      expect(retryAfterSegundos(error429('0'))).toBeNull();
      expect(retryAfterSegundos(error429('-5'))).toBeNull();
    });

    it('acepta una fecha HTTP y calcula los segundos que faltan', () => {
      const ahora = Date.parse('Sat, 21 Nov 2026 10:00:00 GMT');
      expect(
        retryAfterSegundos(
          error429('Sat, 21 Nov 2026 10:00:30 GMT'),
          () => ahora,
        ),
      ).toBe(30);
    });

    it('una fecha ya pasada devuelve null', () => {
      const ahora = Date.parse('Sat, 21 Nov 2026 10:00:00 GMT');
      expect(
        retryAfterSegundos(
          error429('Sat, 21 Nov 2026 09:59:00 GMT'),
          () => ahora,
        ),
      ).toBeNull();
    });
  });

  describe('esperaLegible', () => {
    it('menos de un minuto se dice en segundos', () => {
      expect(esperaLegible(1)).toBe('en 1 segundo');
      expect(esperaLegible(37)).toBe('en 37 segundos');
      expect(esperaLegible(59)).toBe('en 59 segundos');
    });

    it('desde un minuto se dice en minutos, redondeando hacia arriba', () => {
      expect(esperaLegible(60)).toBe('en 1 minuto');
      expect(esperaLegible(61)).toBe('en 2 minutos');
      expect(esperaLegible(870)).toBe('en 15 minutos');
      expect(esperaLegible(900)).toBe('en 15 minutos');
    });

    it('sin dato usa un tiempo generico', () => {
      expect(esperaLegible(null)).toBe('en unos minutos');
      expect(esperaLegible(0)).toBe('en unos minutos');
    });
  });

  it('esperaDeRateLimit combina lectura y formato', () => {
    expect(esperaDeRateLimit(error429('900'))).toBe('en 15 minutos');
    expect(esperaDeRateLimit(error429())).toBe('en unos minutos');
  });
});
