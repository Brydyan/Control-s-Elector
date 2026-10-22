import { describe, expect, it } from 'vitest';
import {
  BACKOFF_BASE_MS,
  BACKOFF_MAX_MS,
  calcularBackoffMs,
  clasificarFallo,
  estadoHttp,
  mensajeFallo,
} from './fallos';

/**
 * Tests del clasificador de fallos de la cola offline (S3).
 *
 * La decisión que estos tests protegen es la más grave del módulo: un fallo
 * transitorio NUNCA borra el acta, y uno permanente la deja en un estado
 * visible en vez de eliminarla en silencio.
 */
describe('clasificarFallo', () => {
  it('should treat a 4xx rejection as permanent', () => {
    expect(clasificarFallo({ status: 400 })).toBe('permanente');
    expect(clasificarFallo({ status: 403 })).toBe('permanente');
    expect(clasificarFallo({ status: 422 })).toBe('permanente');
  });

  it('should treat a 5xx as transient', () => {
    expect(clasificarFallo({ status: 500 })).toBe('transitorio');
    expect(clasificarFallo({ status: 503 })).toBe('transitorio');
  });

  it('should treat a network failure (status 0) as transient', () => {
    expect(clasificarFallo({ status: 0 })).toBe('transitorio');
  });

  it('should treat a timeout (408) and a rate limit (429) as transient', () => {
    expect(clasificarFallo({ status: 408 })).toBe('transitorio');
    expect(clasificarFallo({ status: 429 })).toBe('transitorio');
  });

  it('should treat an error without HTTP status as transient', () => {
    expect(clasificarFallo(new Error('Network timeout'))).toBe('transitorio');
    expect(clasificarFallo('boom')).toBe('transitorio');
    expect(clasificarFallo(null)).toBe('transitorio');
  });

  it('should read a status nested in the response body', () => {
    expect(clasificarFallo({ error: { status: 409 } })).toBe('permanente');
    expect(estadoHttp({ error: { status: 409 } })).toBe(409);
    expect(estadoHttp({ status: 500 })).toBe(500);
    expect(estadoHttp(new Error('sin status'))).toBeNull();
  });
});

describe('mensajeFallo', () => {
  it('should keep the original message when there is no HTTP status', () => {
    expect(mensajeFallo(new Error('Network timeout'))).toBe('Network timeout');
  });

  it('should prefix the message with the HTTP status', () => {
    expect(mensajeFallo({ status: 422, message: 'Acta inconsistente' })).toBe(
      '422: Acta inconsistente',
    );
  });
});

describe('calcularBackoffMs', () => {
  it('should start at the base delay and grow exponentially', () => {
    expect(calcularBackoffMs(1)).toBe(BACKOFF_BASE_MS);
    expect(calcularBackoffMs(2)).toBe(BACKOFF_BASE_MS * 2);
    expect(calcularBackoffMs(3)).toBe(BACKOFF_BASE_MS * 4);
  });

  it('should cap the delay at the maximum', () => {
    expect(calcularBackoffMs(50)).toBe(BACKOFF_MAX_MS);
  });
});
