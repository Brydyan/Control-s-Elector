package ec.twintailcs.electoral.common.application.ratelimit;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Map;

/**
 * Configuracion de rate limiting ({@code app.ratelimit.*}).
 *
 * <p>Cada politica se declara como {@code app.ratelimit.policies.<nombre>.*}. Los valores de
 * produccion se sobreescriben por variables de entorno en Railway, sin recompilar.
 */
@ConfigMapping(prefix = "app.ratelimit")
public interface RateLimitConfig {

  /**
   * Tiempo maximo de espera al almacen compartido. Si se excede, la politica degrada segun su
   * {@code on-failure}: el rate limiting nunca suma mas que esto a la latencia del request.
   */
  @WithDefault("50ms")
  Duration backendTimeout();

  /** Maximo de contadores en memoria del fallback local, para que no crezca sin limite. */
  @WithDefault("10000")
  int localMaxEntries();

  /** Politicas por nombre. */
  Map<String, Policy> policies();

  /** Definicion de una politica. */
  interface Policy {

    /** Maximo de operaciones en la ventana. */
    long limit();

    /** Duracion de la ventana fija. */
    Duration window();

    /** Origen de la identidad: {@code ip}, {@code user} o {@code custom}. */
    RateLimitPolicy.KeyType key();

    /** Que hacer si el almacen falla: {@code local-fallback} o {@code allow}. */
    @WithDefault("local-fallback")
    RateLimitPolicy.FailureMode onFailure();
  }
}
