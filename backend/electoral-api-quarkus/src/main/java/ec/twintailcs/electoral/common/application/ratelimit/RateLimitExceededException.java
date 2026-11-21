package ec.twintailcs.electoral.common.application.ratelimit;

/**
 * Se lanza cuando una operacion supera su politica de rate limiting.
 *
 * <p>La capa web la traduce a 429 con {@code Retry-After} y cabeceras {@code RateLimit-*}.
 */
public class RateLimitExceededException extends RuntimeException {

  private final String policy;
  private final transient RateLimitDecision decision;

  public RateLimitExceededException(String policy, RateLimitDecision decision) {
    super("Demasiadas solicitudes. Reintente en " + decision.retryAfterSeconds() + " segundos.");
    this.policy = policy;
    this.decision = decision;
  }

  /** Nombre de la politica que se supero. */
  public String policy() {
    return policy;
  }

  /** Decision con limite, restante y tiempo de espera. */
  public RateLimitDecision decision() {
    return decision;
  }
}
