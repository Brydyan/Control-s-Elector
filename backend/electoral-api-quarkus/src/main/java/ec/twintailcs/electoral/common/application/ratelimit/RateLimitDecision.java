package ec.twintailcs.electoral.common.application.ratelimit;

/**
 * Resultado de evaluar una politica de rate limiting.
 *
 * @param allowed true si la operacion puede continuar
 * @param limit limite de la politica evaluada (va en {@code RateLimit-Limit})
 * @param remaining operaciones que quedan en la ventana actual (va en {@code RateLimit-Remaining})
 * @param retryAfterSeconds segundos hasta que la ventana se reabre; 0 si fue permitida
 * @param degraded true si la decision no salio del almacen compartido (fallback local o permitir)
 */
public record RateLimitDecision(
    boolean allowed, long limit, long remaining, long retryAfterSeconds, boolean degraded) {

  /** Decision permisiva usada cuando la politica es {@code ALLOW} y el almacen fallo. */
  public static RateLimitDecision allowDegraded(long limit) {
    return new RateLimitDecision(true, limit, limit, 0, true);
  }

  /**
   * Arma la decision a partir del contador de la ventana.
   *
   * @param count operaciones registradas en la ventana (incluida la actual si se consumio)
   * @param ttlMillis milisegundos que le quedan a la ventana
   * @param limit limite de la politica
   * @param blockedWhenAtLimit true para {@code check} (bloquea al llegar al limite), false para
   *     {@code consume} (bloquea al superarlo)
   * @param degraded true si el contador viene del fallback local
   */
  public static RateLimitDecision fromCounter(
      long count, long ttlMillis, long limit, boolean blockedWhenAtLimit, boolean degraded) {
    boolean allowed = blockedWhenAtLimit ? count < limit : count <= limit;
    long remaining = Math.max(0, limit - count);
    long retryAfter = allowed ? 0 : Math.max(1, (ttlMillis + 999) / 1000);
    return new RateLimitDecision(allowed, limit, remaining, retryAfter, degraded);
  }
}
