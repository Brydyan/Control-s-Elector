package ec.twintailcs.electoral.auth.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter para cambios de contraseña.
 *
 * <p>Limita a 1 cambio por hora por usuario+IP. La ventana es fija: si un usuario intenta cambiar
 * de nuevo dentro de la hora, se rechaza.
 *
 * <p>Es memoria del proceso, no compartida. Alcanza para frenar ataques desde un único atacante y
 * se limpia automáticamente cuando la ventana vence.
 *
 * <p>Diseño (D3): 1 cambio/hora previene credential stuffing y cambios masivos accidentales.
 */
@ApplicationScoped
public class PasswordChangeRateLimiter {

  /** Máximo de cambios por ventana (1 cambio por hora). */
  public static final int MAX_ATTEMPTS = 1;

  /** Duración de la ventana, en segundos (1 hora = 3600). */
  public static final long WINDOW_SECONDS = 3600L;

  private record Window(int attempts, long openedAt) {}

  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  /**
   * Registra un intento de cambio de contraseña.
   *
   * @param userId ID del usuario
   * @param ip dirección IP del cliente
   * @throws ec.twintailcs.electoral.auth.application.PasswordChangeException 429 si se excede el
   *     límite
   */
  public void registerAttempt(Long userId, String ip) {
    String key = buildKey(userId, ip);
    long now = Instant.now().getEpochSecond();

    Window window =
        windows.compute(
            key,
            (k, current) -> {
              if (current == null || now - current.openedAt() >= WINDOW_SECONDS) {
                return new Window(1, now);
              }
              return new Window(current.attempts() + 1, current.openedAt());
            });

    if (window.attempts() > MAX_ATTEMPTS) {
      throw ec.twintailcs.electoral.auth.application.PasswordChangeException
          .rateLimitExceeded();
    }
  }

  private String buildKey(Long userId, String ip) {
    return (userId == null ? "unknown" : userId.toString()) + ":" + (ip == null ? "unknown" : ip);
  }
}
