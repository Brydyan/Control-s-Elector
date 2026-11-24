package ec.twintailcs.electoral.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.common.application.ratelimit.RateLimitDecision;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy.FailureMode;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy.KeyType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResilientRateLimiter (Redis + fallback)")
class ResilientRateLimiterTest {

  private static final Duration TIMEOUT = Duration.ofMillis(50);

  private final AtomicLong now = new AtomicLong(1_000_000);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private static RateLimitPolicy policy(FailureMode mode) {
    return new RateLimitPolicy("login-account", 2, Duration.ofMinutes(1), KeyType.CUSTOM, mode);
  }

  /** Almacen que siempre falla, como Redis caido o con timeout. */
  private static final RateLimitBackend BROKEN =
      new RateLimitBackend() {
        @Override
        public Counter increment(String key, Duration window, Duration timeout) {
          throw new IllegalStateException("redis caido");
        }

        @Override
        public Counter peek(String key, Duration timeout) {
          throw new IllegalStateException("redis caido");
        }
      };

  private ResilientRateLimiter limiter(RateLimitBackend shared) {
    return new ResilientRateLimiter(
        shared, new LocalRateLimitBackend(100, now::get), TIMEOUT, now::get, meters);
  }

  private double decisions(String outcome) {
    var counter =
        meters
            .find(ResilientRateLimiter.DECISIONS_METRIC)
            .tag("policy", "login-account")
            .tag("outcome", outcome)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  @DisplayName("metricas: cuenta permitidas y bloqueadas por politica")
  void metricasPermitidasYBloqueadas() {
    ResilientRateLimiter rl = limiter(new LocalRateLimitBackend(100, now::get));
    RateLimitPolicy p = policy(FailureMode.LOCAL_FALLBACK);

    rl.consume(p, "a@x.ec");
    rl.consume(p, "a@x.ec");
    rl.consume(p, "a@x.ec");

    assertThat(decisions("allowed")).isEqualTo(2);
    assertThat(decisions("blocked")).isEqualTo(1);
    assertThat(meters.find(ResilientRateLimiter.BACKEND_METRIC).timer().count()).isEqualTo(3);
  }

  @Test
  @DisplayName("metricas: con Redis caido las decisiones permitidas salen como degraded")
  void metricasDegradadas() {
    ResilientRateLimiter rl = limiter(BROKEN);
    RateLimitPolicy p = policy(FailureMode.ALLOW);

    rl.consume(p, "a@x.ec");
    rl.consume(p, "a@x.ec");

    assertThat(decisions("degraded")).isEqualTo(2);
    assertThat(decisions("allowed")).isZero();
  }

  @Test
  @DisplayName("con el almacen sano: permite hasta el limite y rechaza al superarlo")
  void almacenSano() {
    ResilientRateLimiter rl = limiter(new LocalRateLimitBackend(100, now::get));
    RateLimitPolicy p = policy(FailureMode.LOCAL_FALLBACK);

    assertThat(rl.consume(p, "a@x.ec").allowed()).isTrue();
    assertThat(rl.consume(p, "a@x.ec").allowed()).isTrue();
    RateLimitDecision tercero = rl.consume(p, "a@x.ec");

    assertThat(tercero.allowed()).isFalse();
    assertThat(tercero.retryAfterSeconds()).isPositive();
    assertThat(tercero.degraded()).isFalse();
  }

  @Test
  @DisplayName("Redis caido + LOCAL_FALLBACK: sigue limitando con el contador local")
  void fallbackLocalSigueLimitando() {
    ResilientRateLimiter rl = limiter(BROKEN);
    RateLimitPolicy p = policy(FailureMode.LOCAL_FALLBACK);

    assertThat(rl.consume(p, "a@x.ec").allowed()).isTrue();
    assertThat(rl.consume(p, "a@x.ec").allowed()).isTrue();
    RateLimitDecision tercero = rl.consume(p, "a@x.ec");

    assertThat(tercero.allowed()).as("el fallback protege igual").isFalse();
    assertThat(tercero.degraded()).isTrue();
  }

  @Test
  @DisplayName("Redis caido + ALLOW: deja pasar y marca la decision como degradada")
  void allowDejaPasar() {
    ResilientRateLimiter rl = limiter(BROKEN);
    RateLimitPolicy p = policy(FailureMode.ALLOW);

    for (int i = 0; i < 10; i++) {
      RateLimitDecision d = rl.consume(p, "a@x.ec");
      assertThat(d.allowed()).isTrue();
      assertThat(d.degraded()).isTrue();
    }
  }

  @Test
  @DisplayName("check no consume: varias consultas no agotan el limite")
  void checkNoConsume() {
    ResilientRateLimiter rl = limiter(new LocalRateLimitBackend(100, now::get));
    RateLimitPolicy p = policy(FailureMode.LOCAL_FALLBACK);

    for (int i = 0; i < 10; i++) {
      assertThat(rl.check(p, "a@x.ec").allowed()).isTrue();
    }
    rl.consume(p, "a@x.ec");
    rl.consume(p, "a@x.ec");

    assertThat(rl.check(p, "a@x.ec").allowed())
        .as("al llegar al limite, check rechaza antes de la operacion cara")
        .isFalse();
  }

  @Test
  @DisplayName("Redis caido + check con LOCAL_FALLBACK consulta el contador local")
  void checkConFallback() {
    ResilientRateLimiter rl = limiter(BROKEN);
    RateLimitPolicy p = policy(FailureMode.LOCAL_FALLBACK);
    rl.consume(p, "a@x.ec");
    rl.consume(p, "a@x.ec");

    RateLimitDecision d = rl.check(p, "a@x.ec");

    assertThat(d.allowed()).isFalse();
    assertThat(d.degraded()).isTrue();
  }

  @Test
  @DisplayName("identidades distintas no comparten contador")
  void identidadesSeparadas() {
    ResilientRateLimiter rl = limiter(new LocalRateLimitBackend(100, now::get));
    RateLimitPolicy p = policy(FailureMode.LOCAL_FALLBACK);
    rl.consume(p, "a@x.ec");
    rl.consume(p, "a@x.ec");

    assertThat(rl.consume(p, "b@x.ec").allowed()).isTrue();
  }

  @Test
  @DisplayName("las claves van hasheadas: la identidad no aparece en claro")
  void claveSinDatosPersonales() {
    String key = RateLimitKeys.of("login-account", "observador@correo.ec");

    assertThat(key).startsWith("rl:v1:login-account:").doesNotContain("observador");
    assertThat(key).isEqualTo(RateLimitKeys.of("login-account", "observador@correo.ec"));
  }
}
