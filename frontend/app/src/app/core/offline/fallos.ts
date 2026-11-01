/**
 * Clasificación de fallos y backoff de la cola offline (S3).
 *
 * Un fallo no es un dato: es el motivo por el que un acta sigue esperando.
 * Distinguir "el servidor nunca va a aceptar esta acta" de "se cayó la red"
 * decide dos cosas:
 *
 *   - Si el registro puede esperar a un reintento automático (transitorio) o
 *     tiene que pasar a un estado fallido que el operador debe ver
 *     (permanente).
 *   - NUNCA se borra un acta por un fallo transitorio. El contador de
 *     reintentos no es un permiso de borrado: la eliminación del registro
 *     electoral exige una confirmación explícita del operador.
 */

/** Clasificación de un fallo de transmisión. */
export type TipoFallo = 'permanente' | 'transitorio';

/** Base del backoff exponencial entre reintentos. */
export const BACKOFF_BASE_MS = 30_000;

/** Techo del backoff: más allá de 15 min no tiene sentido reintentar. */
export const BACKOFF_MAX_MS = 15 * 60_000;

/**
 * Extrae el status HTTP de un error, si lo tiene.
 *
 * `HttpErrorResponse` lo expone en `status`; algunos clientes lo anidan en
 * `error.status`. Un error sin status (una caída de red, un timeout, un
 * `Error` pelado) se considera transitorio: no hay evidencia de que el
 * servidor haya rechazado el acta.
 */
export function estadoHttp(error: unknown): number | null {
  if (typeof error !== 'object' || error === null) return null;

  const directo = (error as { status?: unknown }).status;
  if (typeof directo === 'number' && Number.isFinite(directo)) return directo;

  const cuerpo = (error as { error?: unknown }).error;
  if (typeof cuerpo === 'object' && cuerpo !== null) {
    const anidado = (cuerpo as { status?: unknown }).status;
    if (typeof anidado === 'number' && Number.isFinite(anidado)) return anidado;
  }

  return null;
}

/**
 * Clasifica el fallo: permanente (el servidor no va a aceptar esta acta) o
 * transitorio (la red, el tiempo o el servidor pueden cambiar).
 */
export function clasificarFallo(error: unknown): TipoFallo {
  const status = estadoHttp(error);

  // Sin status HTTP no hay rechazo del servidor: es un fallo de transporte.
  if (status === null) return 'transitorio';

  // `status: 0` es la señal de HttpClient cuando no hubo respuesta.
  if (status === 0) return 'transitorio';

  // 408 y 429 son 4xx que significa "reintenta más tarde", no "rechazado".
  if (status === 408 || status === 429) return 'transitorio';

  if (status >= 400 && status < 500) return 'permanente';
  if (status >= 500) return 'transitorio';

  return 'transitorio';
}

/** Mensaje legible del fallo, con el status HTTP cuando existe. */
export function mensajeFallo(error: unknown): string {
  const status = estadoHttp(error);
  return status !== null
    ? `${status}: ${mensajeCrudo(error)}`
    : mensajeCrudo(error);
}

/** Extrae el mensaje de un error, sea o no una instancia de `Error`. */
function mensajeCrudo(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'object' && error !== null) {
    const mensaje = (error as { message?: unknown }).message;
    if (typeof mensaje === 'string') return mensaje;
  }
  return String(error);
}

/**
 * Backoff exponencial para el reintento `retries` (1 = primer fallo).
 * El primer reintento entra en la misma cadencia del timer de 30 s.
 */
export function calcularBackoffMs(retries: number): number {
  const exponente = Math.max(0, retries - 1);
  return Math.min(BACKOFF_BASE_MS * 2 ** exponente, BACKOFF_MAX_MS);
}
