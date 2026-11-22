package ec.twintailcs.electoral.common.application.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * Regla de rate limiting: cuantas operaciones admite una identidad dentro de una ventana fija.
 *
 * <p>Las politicas se declaran por nombre en {@code app.ratelimit.policies.*} y se resuelven con
 * {@link RateLimitPolicies}. Los valores son configurables por entorno sin recompilar.
 *
 * @param name nombre de la politica; forma parte de la clave de almacenamiento
 * @param limit maximo de operaciones permitidas en la ventana
 * @param window duracion de la ventana fija
 * @param key de donde sale la identidad que se cuenta
 * @param onFailure que hacer si el almacen compartido no responde
 */
public record RateLimitPolicy(
    String name, long limit, Duration window, KeyType key, FailureMode onFailure) {

  /** Ventana minima: por debajo de un segundo el {@code Retry-After} pierde sentido. */
  static final Duration MIN_WINDOW = Duration.ofSeconds(1);

  public RateLimitPolicy {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("La politica de rate limit necesita nombre");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException(
          "La politica '" + name + "' necesita limit > 0 (recibido " + limit + ")");
    }
    Objects.requireNonNull(window, "window");
    if (window.compareTo(MIN_WINDOW) < 0) {
      throw new IllegalArgumentException(
          "La politica '"
              + name
              + "' necesita una ventana de al menos 1s (recibido "
              + window
              + ")");
    }
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(onFailure, "onFailure");
  }

  /** Origen de la identidad contada por la politica. */
  public enum KeyType {
    /** IP real del cliente. Solo se aplica si la IP es confiable. */
    IP,
    /** Usuario autenticado (subject del JWT). */
    USER,
    /** Identidad de negocio que arma el caso de uso (por ejemplo email + IP en el login). */
    CUSTOM
  }

  /** Comportamiento cuando el almacen compartido (Redis) falla o excede el timeout. */
  public enum FailureMode {
    /**
     * Cuenta en memoria de la replica. La proteccion queda aproximada (por replica) pero existe.
     */
    LOCAL_FALLBACK,
    /** Deja pasar la operacion. Solo para politicas de bajo riesgo. */
    ALLOW
  }
}
