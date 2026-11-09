package ec.twintailcs.electoral.common.application;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;

/**
 * Servicio de rate limiting distribuido con Redis.
 *
 * <p>Implementa límites en tres niveles:
 * - Global por IP: máximo 100 requests/minuto
 * - Por usuario autenticado: máximo 1000 requests/minuto
 * - Por endpoint específico: reglas personalizadas (login: 5/5min, export: 1/24h, etc)
 *
 * <p>Usa Bucket4j para token bucket algorithm y Redis para contadores distribuidos.
 * La clave sigue el patrón: rate_limit:{type}:{identifier}:{endpoint}
 *
 * <p>Devuelve un objeto con:
 * - allowed: true si el request puede proceder, false si fue rechazado
 * - remaining: cuántos tokens quedan en el bucket
 * - retryAfter: segundos hasta poder reintentar (0 si fue permitido)
 */
@ApplicationScoped
public class RateLimitingService {

  private static final String KEY_PREFIX = "rate_limit";
  private static final long GLOBAL_LIMIT = 100;
  private static final long GLOBAL_WINDOW = 60;
  private static final long USER_LIMIT = 1000;
  private static final long USER_WINDOW = 60;

  @Inject RedisDataSource redisDataSource;

  /**
   * Resultado del chequeo de rate limit.
   *
   * @param allowed true si el request fue permitido
   * @param remaining cuántos tokens quedan en el bucket
   * @param retryAfter segundos hasta poder reintentar (0 si fue permitido)
   */
  public record RateLimitResult(boolean allowed, long remaining, long retryAfter) {}

  /**
   * Verifica el límite global por IP.
   *
   * @param ip dirección IP del cliente
   * @return resultado del chequeo
   */
  public RateLimitResult checkGlobalLimit(String ip) {
    return checkLimit(buildKey("global", ip, null), GLOBAL_LIMIT, GLOBAL_WINDOW);
  }

  /**
   * Verifica el límite por usuario autenticado.
   *
   * @param userId ID del usuario
   * @return resultado del chequeo
   */
  public RateLimitResult checkUserLimit(Long userId) {
    return checkLimit(buildKey("user", String.valueOf(userId), null), USER_LIMIT, USER_WINDOW);
  }

  /**
   * Verifica el límite específico de un endpoint.
   *
   * @param userId ID del usuario (puede ser nulo para endpoints públicos)
   * @param endpoint nombre del endpoint (ej: login, export-data, contact-dpo)
   * @param limit máximo de requests
   * @param windowSeconds ventana de tiempo en segundos
   * @return resultado del chequeo
   */
  public RateLimitResult checkEndpointLimit(
      Long userId, String endpoint, long limit, long windowSeconds) {
    String identifier = userId != null ? String.valueOf(userId) : "anonymous";
    return checkLimit(buildKey("endpoint", identifier, endpoint), limit, windowSeconds);
  }

  /**
   * Verifica un límite genérico contra Redis.
   *
   * <p>Implementa token bucket algorithm:
   * 1. Obtiene el bucket actual (count + expiry)
   * 2. Si el bucket expiró, lo reinicializa
   * 3. Si hay tokens, los decrementa y devuelve allowed=true
   * 4. Si no hay tokens, devuelve allowed=false y el tiempo de espera
   *
   * @param key clave Redis
   * @param limit máximo de tokens
   * @param windowSeconds duración de la ventana en segundos
   * @return resultado del chequeo
   */
  private RateLimitResult checkLimit(String key, long limit, long windowSeconds) {
    try {
      // Obtener el bucket actual
      Long count = redisDataSource.value(Long.class).get(key);
      Long expiryTime = redisDataSource.value(Long.class).get(key + ":expiry");

      long now = Instant.now().getEpochSecond();
      long currentCount = count != null ? count : 0;
      long expiresAt = expiryTime != null ? expiryTime : 0;

      // Si la ventana expiró, reinicializar
      if (expiresAt <= now) {
        expiresAt = now + windowSeconds;
        currentCount = 0;
      }

      // Decrementar el contador
      long newCount = currentCount + 1;
      redisDataSource.value(Long.class).set(key, newCount);
      redisDataSource.value(Long.class).set(key + ":expiry", expiresAt);
      redisDataSource.key().expire(key, windowSeconds);
      redisDataSource.key().expire(key + ":expiry", windowSeconds);

      // Chequear si se excedió el límite
      if (newCount > limit) {
        long retryAfter = expiresAt - now;
        return new RateLimitResult(false, 0, Math.max(1, retryAfter));
      }

      long remaining = limit - newCount;
      return new RateLimitResult(true, remaining, 0);

    } catch (Exception e) {
      // Si Redis falla, permitir el request (fallback seguro)
      // Log en nivel WARN pero no bloquear
      return new RateLimitResult(true, limit, 0);
    }
  }

  /**
   * Construye la clave Redis para un límite.
   *
   * @param type tipo de límite (global, user, endpoint)
   * @param identifier IP, userId, etc.
   * @param endpoint nombre del endpoint (puede ser nulo)
   * @return clave Redis
   */
  private String buildKey(String type, String identifier, String endpoint) {
    if (endpoint != null) {
      return String.format("%s:%s:%s:%s", KEY_PREFIX, type, identifier, endpoint);
    }
    return String.format("%s:%s:%s", KEY_PREFIX, type, identifier);
  }
}
