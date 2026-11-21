package ec.twintailcs.electoral.common.infrastructure.ratelimit;

import java.time.Duration;

/**
 * Almacen de contadores de ventana fija. Implementado por Redis (compartido entre replicas) y por
 * memoria local (fallback).
 */
interface RateLimitBackend {

  /**
   * Suma uno al contador de la clave, abriendo la ventana si no existe.
   *
   * @param key clave de almacenamiento
   * @param window duracion de la ventana (se aplica solo al abrirla)
   * @param timeout espera maxima; si se excede se lanza una excepcion
   * @return contador despues de incrementar y TTL restante
   */
  Counter increment(String key, Duration window, Duration timeout);

  /**
   * Lee el contador sin modificarlo.
   *
   * @return contador actual ({@code count=0} si la ventana no existe)
   */
  Counter peek(String key, Duration timeout);

  /**
   * Estado de una ventana.
   *
   * @param count operaciones registradas
   * @param ttlMillis milisegundos que le quedan a la ventana (0 si no existe)
   */
  record Counter(long count, long ttlMillis) {
    static final Counter EMPTY = new Counter(0, 0);
  }
}
