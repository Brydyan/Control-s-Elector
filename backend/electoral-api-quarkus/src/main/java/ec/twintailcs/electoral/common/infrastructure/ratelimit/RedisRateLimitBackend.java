package ec.twintailcs.electoral.common.infrastructure.ratelimit;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;

/**
 * Contadores de ventana fija en Redis, compartidos entre todas las replicas.
 *
 * <p>Cada operacion es un unico script Lua: Redis lo ejecuta de forma atomica, asi que dos requests
 * concurrentes nunca leen el mismo valor (el problema del GET + SET del servicio anterior). Es una
 * sola ida y vuelta a Redis por chequeo.
 */
@ApplicationScoped
class RedisRateLimitBackend implements RateLimitBackend {

  /**
   * INCR y, si abre la ventana, PEXPIRE. Si la clave quedo sin TTL por cualquier motivo, se lo
   * reasigna para que ningun contador quede huerfano (todas las claves rl:* tienen TTL, requisito
   * de {@code maxmemory-policy volatile-ttl}).
   */
  static final String INCREMENT_SCRIPT =
      """
      local c = redis.call('INCR', KEYS[1])
      if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
      local ttl = redis.call('PTTL', KEYS[1])
      if ttl < 0 then
        redis.call('PEXPIRE', KEYS[1], ARGV[1])
        ttl = tonumber(ARGV[1])
      end
      return {c, ttl}
      """;

  /** Lectura del contador y su TTL en una sola ida y vuelta. */
  static final String PEEK_SCRIPT =
      """
      local c = redis.call('GET', KEYS[1])
      if not c then return {0, 0} end
      local ttl = redis.call('PTTL', KEYS[1])
      if ttl < 0 then ttl = 0 end
      return {tonumber(c), ttl}
      """;

  @Inject ReactiveRedisDataSource redis;

  @Override
  public Counter increment(String key, Duration window, Duration timeout) {
    return eval(INCREMENT_SCRIPT, key, String.valueOf(window.toMillis()), timeout);
  }

  @Override
  public Counter peek(String key, Duration timeout) {
    return eval(PEEK_SCRIPT, key, "0", timeout);
  }

  private Counter eval(String script, String key, String arg, Duration timeout) {
    Response response =
        redis
            .execute("EVAL", script, "1", key, arg)
            .ifNoItem()
            .after(timeout)
            .fail()
            .await()
            .indefinitely();
    if (response == null || response.size() < 2) {
      throw new IllegalStateException("Respuesta inesperada de Redis para el rate limit");
    }
    return new Counter(response.get(0).toLong(), response.get(1).toLong());
  }
}
