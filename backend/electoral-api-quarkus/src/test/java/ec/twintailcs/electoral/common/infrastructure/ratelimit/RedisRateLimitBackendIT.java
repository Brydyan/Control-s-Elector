package ec.twintailcs.electoral.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.common.application.ratelimit.RateLimitDecision;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicies;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy.FailureMode;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy.KeyType;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimiter;
import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import ec.twintailcs.electoral.shared.testing.ValkeyTestResource;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rate limiting contra Redis real (Testcontainers).
 *
 * <p>Lo que importa probar aca y no en unit tests: la atomicidad del script Lua con requests
 * concurrentes, el TTL real de Redis y que todas las claves rl:* queden con expiracion.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@QuarkusTestResource(ValkeyTestResource.class)
@DisplayName("Rate limiting con Redis")
class RedisRateLimitBackendIT {

  @Inject RateLimiter rateLimiter;
  @Inject RateLimitPolicies policies;
  @Inject RedisDataSource redis;

  private static RateLimitPolicy policy(long limit, Duration window) {
    return new RateLimitPolicy(
        "it-" + UUID.randomUUID(), limit, window, KeyType.USER, FailureMode.LOCAL_FALLBACK);
  }

  @Test
  @DisplayName("la operacion N+1 se rechaza con Retry-After y sin degradar")
  void rechazaAlSuperar() {
    RateLimitPolicy p = policy(3, Duration.ofMinutes(1));

    for (int i = 0; i < 3; i++) {
      assertThat(rateLimiter.consume(p, "user-1").allowed()).isTrue();
    }
    var cuarta = rateLimiter.consume(p, "user-1");

    assertThat(cuarta.allowed()).isFalse();
    assertThat(cuarta.retryAfterSeconds()).isBetween(1L, 60L);
    assertThat(cuarta.degraded()).as("la decision salio de Redis").isFalse();
  }

  @Test
  @DisplayName("50 requests concurrentes: pasan exactamente 'limit' (el script es atomico)")
  void atomicoBajoConcurrencia() throws Exception {
    int limit = 10;
    int requests = 50;
    RateLimitPolicy p = policy(limit, Duration.ofMinutes(1));
    ExecutorService pool = Executors.newFixedThreadPool(requests);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<RateLimitDecision>> results = new ArrayList<>();
      for (int i = 0; i < requests; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return rateLimiter.consume(p, "user-concurrente");
                }));
      }
      start.countDown();

      long permitidos = 0;
      long degradadas = 0;
      for (Future<RateLimitDecision> f : results) {
        RateLimitDecision d = f.get(10, TimeUnit.SECONDS);
        permitidos += d.allowed() ? 1 : 0;
        degradadas += d.degraded() ? 1 : 0;
      }
      assertThat(degradadas)
          .as("ninguna decision cae al fallback: el pool de Redis absorbe el pico")
          .isZero();
      assertThat(permitidos).isEqualTo(limit);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("al vencer la ventana el contador se reinicia")
  void reiniciaAlVencer() throws Exception {
    RateLimitPolicy p = policy(1, Duration.ofSeconds(1));
    assertThat(rateLimiter.consume(p, "user-ventana").allowed()).isTrue();
    assertThat(rateLimiter.consume(p, "user-ventana").allowed()).isFalse();

    Thread.sleep(1_200);

    assertThat(rateLimiter.consume(p, "user-ventana").allowed()).isTrue();
  }

  @Test
  @DisplayName("check no consume y rechaza al llegar al limite")
  void checkNoConsume() {
    RateLimitPolicy p = policy(2, Duration.ofMinutes(1));

    assertThat(rateLimiter.check(p, "user-check").allowed()).isTrue();
    assertThat(rateLimiter.check(p, "user-check").allowed()).isTrue();
    rateLimiter.consume(p, "user-check");
    rateLimiter.consume(p, "user-check");

    assertThat(rateLimiter.check(p, "user-check").allowed()).isFalse();
  }

  @Test
  @DisplayName("toda clave rl:* queda con TTL (requisito de maxmemory-policy volatile-ttl)")
  void todaClaveTieneTtl() {
    RateLimitPolicy p = policy(5, Duration.ofMinutes(1));
    rateLimiter.consume(p, "user-ttl");

    String key = RateLimitKeys.of(p.name(), "user-ttl");
    long ttlSeconds = redis.key().ttl(key);

    assertThat(ttlSeconds).isBetween(1L, 60L);
  }

  @Test
  @DisplayName("convive con el cache en el mismo Redis: prefijos separados, sin pisarse")
  void conviveConCache() {
    String cacheKey = "cache:dashboard:resumen";
    redis.value(String.class).setex(cacheKey, 60, "{\"enviadas\":3}");
    RateLimitPolicy p = policy(5, Duration.ofMinutes(1));

    for (int i = 0; i < 5; i++) {
      rateLimiter.consume(p, "user-cache");
    }

    assertThat(redis.value(String.class).get(cacheKey))
        .as("el rate limiting no toca claves de cache")
        .isEqualTo("{\"enviadas\":3}");
    assertThat(redis.key().keys("rl:v1:*")).allMatch(k -> k.startsWith(RateLimitKeys.PREFIX));
    assertThat(redis.key().keys("rl:v1:*")).noneMatch(k -> k.startsWith("cache:"));
  }

  @Test
  @DisplayName("las politicas configuradas se cargan al arrancar")
  void politicasConfiguradas() {
    assertThat(policies.names())
        .contains("login-account-ip", "login-account", "reauth", "user-global", "observer-actas");
    assertThat(policies.get("login-account-ip").limit()).isEqualTo(5);
    assertThat(policies.get("user-global").onFailure()).isEqualTo(FailureMode.ALLOW);
  }
}
