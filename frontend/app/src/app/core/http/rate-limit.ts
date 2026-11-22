import { HttpErrorResponse } from '@angular/common/http';

/**
 * Lectura del 429 del backend (rate limiting).
 *
 * El backend responde 429 con `Retry-After` en segundos. Este modulo traduce
 * ese header a un tiempo de espera legible ("en 37 segundos", "en 15
 * minutos"), para que cada pantalla arme su propio mensaje con el mismo
 * criterio. Son funciones puras: se prueban sin TestBed ni red.
 *
 * El texto se arma aca y no se toma del body del backend porque el backend
 * siempre habla en segundos ("Reintente en 900 segundos"), y para un bloqueo
 * de 15 minutos eso no es legible.
 */

/** `true` si el error es un 429 del backend. */
export function esRateLimit(err: unknown): err is HttpErrorResponse {
  return err instanceof HttpErrorResponse && err.status === 429;
}

/**
 * Segundos de espera que indica `Retry-After`, o `null` si el header falta o
 * no se puede interpretar.
 *
 * Acepta las dos formas del estandar (RFC 9110): segundos enteros, que es lo
 * que manda el backend, o una fecha HTTP.
 */
export function retryAfterSegundos(
  err: HttpErrorResponse,
  ahora: () => number = Date.now,
): number | null {
  const valor = err.headers?.get('Retry-After')?.trim();
  if (!valor) {
    return null;
  }
  if (/^\d+$/.test(valor)) {
    const segundos = Number(valor);
    return segundos > 0 ? segundos : null;
  }
  const fecha = Date.parse(valor);
  if (Number.isNaN(fecha)) {
    return null;
  }
  const segundos = Math.ceil((fecha - ahora()) / 1000);
  return segundos > 0 ? segundos : null;
}

/**
 * Tiempo de espera legible: "en 1 segundo", "en 37 segundos", "en 1 minuto",
 * "en 15 minutos". Sin dato, "en unos minutos".
 *
 * A partir de 60 segundos redondea hacia arriba en minutos: decir "en 14
 * minutos" cuando faltan 14:30 haria reintentar antes de tiempo.
 */
export function esperaLegible(segundos: number | null): string {
  if (segundos === null || segundos <= 0) {
    return 'en unos minutos';
  }
  if (segundos < 60) {
    return segundos === 1 ? 'en 1 segundo' : `en ${segundos} segundos`;
  }
  const minutos = Math.ceil(segundos / 60);
  return minutos === 1 ? 'en 1 minuto' : `en ${minutos} minutos`;
}

/** Atajo: el tiempo de espera legible de un 429. */
export function esperaDeRateLimit(err: HttpErrorResponse): string {
  return esperaLegible(retryAfterSegundos(err));
}
