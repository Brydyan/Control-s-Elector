package ec.twintailcs.electoral.common.infrastructure.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Contadores de ventana fija en memoria de la replica. Es el fallback cuando Redis no responde.
 *
 * <p>La proteccion es aproximada: cada replica cuenta por su lado, asi que con N replicas el limite
 * efectivo puede llegar a N veces el configurado mientras dure la falla. Es preferible a no tener
 * limite.
 *
 * <p>La memoria esta acotada a {@code maxEntries} contadores: al llenarse se purgan las ventanas
 * vencidas, y si aun asi no hay lugar la operacion se permite sin contarla. Una caida larga de
 * Redis con muchas identidades nuevas no puede agotar el heap.
 */
final class LocalRateLimitBackend implements RateLimitBackend {

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final int maxEntries;
  private final LongSupplier nowMillis;

  LocalRateLimitBackend(int maxEntries, LongSupplier nowMillis) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries debe ser > 0");
    }
    this.maxEntries = maxEntries;
    this.nowMillis = nowMillis;
  }

  @Override
  public Counter increment(String key, Duration window, Duration timeout) {
    long now = nowMillis.getAsLong();
    if (!windows.containsKey(key) && windows.size() >= maxEntries) {
      purgeExpired(now);
      if (windows.size() >= maxEntries) {
        // Sin lugar: se permite sin registrar. Mejor que crecer sin limite.
        return new Counter(1, window.toMillis());
      }
    }
    Window updated =
        windows.compute(
            key,
            (k, current) ->
                current == null || current.expiresAt() <= now
                    ? new Window(1, now + window.toMillis())
                    : new Window(current.count() + 1, current.expiresAt()));
    return new Counter(updated.count(), updated.expiresAt() - now);
  }

  @Override
  public Counter peek(String key, Duration timeout) {
    long now = nowMillis.getAsLong();
    Window current = windows.get(key);
    if (current == null || current.expiresAt() <= now) {
      return Counter.EMPTY;
    }
    return new Counter(current.count(), current.expiresAt() - now);
  }

  /** Cantidad de contadores en memoria (para tests). */
  int size() {
    return windows.size();
  }

  private void purgeExpired(long now) {
    windows.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
  }

  private record Window(long count, long expiresAt) {}
}
