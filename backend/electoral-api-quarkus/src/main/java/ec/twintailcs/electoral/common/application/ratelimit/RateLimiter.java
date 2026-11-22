package ec.twintailcs.electoral.common.application.ratelimit;

/**
 * Puerto de rate limiting.
 *
 * <p>La capa de aplicacion depende de este puerto; la implementacion (Redis con fallback local)
 * vive en {@code common.infrastructure.ratelimit}. Ninguna implementacion lanza excepciones por
 * fallas del almacen: degradan segun {@link RateLimitPolicy#onFailure()}.
 *
 * <p>Debe invocarse desde un hilo worker (endpoints bloqueantes), no desde el event loop.
 */
public interface RateLimiter {

  /**
   * Registra una operacion y decide si se permite.
   *
   * @param policy politica a aplicar
   * @param identity identidad contada (IP, subject del usuario o clave de negocio); nunca vacia
   * @return decision; {@code allowed=false} si la operacion supera el limite
   */
  RateLimitDecision consume(RateLimitPolicy policy, String identity);

  /**
   * Consulta si la identidad ya agoto la ventana, sin registrar una operacion.
   *
   * <p>Sirve para chequear antes de una operacion cara (por ejemplo BCrypt en el login) y contar
   * solo si falla, con {@link #consume}.
   *
   * @param policy politica a aplicar
   * @param identity identidad contada; nunca vacia
   * @return decision; {@code allowed=false} si la identidad ya llego al limite
   */
  RateLimitDecision check(RateLimitPolicy policy, String identity);
}
