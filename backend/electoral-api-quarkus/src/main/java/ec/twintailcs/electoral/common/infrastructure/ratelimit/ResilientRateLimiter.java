package ec.twintailcs.electoral.common.infrastructure.ratelimit;

import ec.twintailcs.electoral.common.application.ratelimit.RateLimitConfig;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitDecision;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.jboss.logging.Logger;

/**
 * Implementacion del puerto {@link RateLimiter}: Redis como almacen compartido y memoria local como
 * fallback.
 *
 * <p>Si Redis falla o tarda mas que {@code app.ratelimit.backend-timeout}, la politica decide:
 *
 * <ul>
 *   <li>{@code LOCAL_FALLBACK}: cuenta en memoria de la replica (proteccion aproximada).
 *   <li>{@code ALLOW}: deja pasar.
 * </ul>
 *
 * <p>Ningun modo bloquea por falla de Redis: una caida del almacen no puede tumbar el login ni el
 * envio de actas. Las decisiones degradadas se marcan con {@code degraded=true}.
 *
 * <p>Metricas (puerto de management):
 *
 * <ul>
 *   <li>{@code ratelimit_decisions_total{policy, outcome}} con outcome {@code allowed}, {@code
 *       blocked} o {@code degraded}. Alertar si {@code blocked} supera el 5% en una politica
 *       autenticada (limite mal calibrado) o si {@code degraded} se sostiene (Redis caido).
 *   <li>{@code ratelimit_backend_seconds}: latencia de Redis.
 * </ul>
 *
 * <p>El aviso de falla se loguea como mucho una vez por minuto y nunca incluye la identidad ni la
 * clave (pueden derivar de un email o una IP).
 */
@ApplicationScoped
public class ResilientRateLimiter implements RateLimiter {

  private static final Logger LOG = Logger.getLogger(ResilientRateLimiter.class);
  private static final long WARN_INTERVAL_MILLIS = 60_000;

  static final String DECISIONS_METRIC = "ratelimit.decisions";
  static final String BACKEND_METRIC = "ratelimit.backend";

  @Inject RateLimitConfig config;
  @Inject RedisRateLimitBackend redisBackend;
  @Inject MeterRegistry registry;

  private RateLimitBackend shared;
  private RateLimitBackend local;
  private Duration timeout;
  private LongSupplier nowMillis;
  private MeterRegistry meters;
  private Timer backendTimer;
  private final AtomicLong lastWarnAt = new AtomicLong(Long.MIN_VALUE);

  /** Constructor para CDI. */
  public ResilientRateLimiter() {}

  /** Constructor para tests: permite inyectar almacenes, reloj y registro de metricas. */
  ResilientRateLimiter(
      RateLimitBackend shared,
      RateLimitBackend local,
      Duration timeout,
      LongSupplier nowMillis,
      MeterRegistry meters) {
    this.shared = shared;
    this.local = local;
    this.timeout = timeout;
    this.nowMillis = nowMillis;
    this.meters = meters;
    this.backendTimer = backendTimer(meters);
  }

  @PostConstruct
  void init() {
    this.shared = redisBackend;
    this.nowMillis = System::currentTimeMillis;
    this.local = new LocalRateLimitBackend(config.localMaxEntries(), nowMillis);
    this.timeout = config.backendTimeout();
    this.meters = registry;
    this.backendTimer = backendTimer(registry);
  }

  @Override
  public RateLimitDecision consume(RateLimitPolicy policy, String identity) {
    return decide(policy, identity, false);
  }

  @Override
  public RateLimitDecision check(RateLimitPolicy policy, String identity) {
    return decide(policy, identity, true);
  }

  /**
   * @param peek true para {@code check} (lee sin contar), false para {@code consume}
   */
  private RateLimitDecision decide(RateLimitPolicy policy, String identity, boolean peek) {
    String key = RateLimitKeys.of(policy.name(), identity);
    RateLimitDecision decision;
    try {
      RateLimitBackend.Counter c =
          backendTimer.record(() -> read(shared, key, policy.window(), peek));
      decision =
          RateLimitDecision.fromCounter(c.count(), c.ttlMillis(), policy.limit(), peek, false);
    } catch (RuntimeException e) {
      warnDegraded(policy, e);
      if (policy.onFailure() == RateLimitPolicy.FailureMode.ALLOW) {
        decision = RateLimitDecision.allowDegraded(policy.limit());
      } else {
        RateLimitBackend.Counter c = read(local, key, policy.window(), peek);
        decision =
            RateLimitDecision.fromCounter(c.count(), c.ttlMillis(), policy.limit(), peek, true);
      }
    }
    count(policy, decision);
    return decision;
  }

  private RateLimitBackend.Counter read(
      RateLimitBackend backend, String key, Duration window, boolean peek) {
    return peek ? backend.peek(key, timeout) : backend.increment(key, window, timeout);
  }

  private void count(RateLimitPolicy policy, RateLimitDecision decision) {
    String outcome = !decision.allowed() ? "blocked" : decision.degraded() ? "degraded" : "allowed";
    Counter.builder(DECISIONS_METRIC)
        .description("Decisiones de rate limiting por politica")
        .tag("policy", policy.name())
        .tag("outcome", outcome)
        .register(meters)
        .increment();
  }

  private static Timer backendTimer(MeterRegistry meters) {
    return Timer.builder(BACKEND_METRIC)
        .description("Latencia del almacen compartido de rate limiting (Redis)")
        .register(meters);
  }

  private void warnDegraded(RateLimitPolicy policy, RuntimeException e) {
    long now = nowMillis.getAsLong();
    long last = lastWarnAt.get();
    if ((last == Long.MIN_VALUE || now - last >= WARN_INTERVAL_MILLIS)
        && lastWarnAt.compareAndSet(last, now)) {
      LOG.warnf(
          "Rate limit degradado: almacen compartido no disponible (%s). Politica '%s' usa %s."
              + " Este aviso se repite como mucho una vez por minuto.",
          e.getClass().getSimpleName(), policy.name(), policy.onFailure());
    }
  }
}
