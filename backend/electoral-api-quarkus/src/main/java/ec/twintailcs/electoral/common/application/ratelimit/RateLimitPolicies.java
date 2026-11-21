package ec.twintailcs.electoral.common.application.ratelimit;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;

/**
 * Registro de politicas de rate limiting, construido desde {@link RateLimitConfig}.
 *
 * <p>Se inicializa al arrancar ({@link Startup}): una politica mal configurada (limite 0, ventana
 * menor a 1s) impide que la aplicacion arranque, en vez de fallar en el primer request.
 */
@Startup
@ApplicationScoped
public class RateLimitPolicies {

  @Inject RateLimitConfig config;

  private Map<String, RateLimitPolicy> policies = Map.of();

  @PostConstruct
  void init() {
    Map<String, RateLimitPolicy> built = new TreeMap<>();
    config
        .policies()
        .forEach(
            (name, p) ->
                built.put(
                    name,
                    new RateLimitPolicy(name, p.limit(), p.window(), p.key(), p.onFailure())));
    this.policies = Collections.unmodifiableMap(built);
  }

  /**
   * Devuelve la politica por nombre.
   *
   * @throws NoSuchElementException si no esta configurada
   */
  public RateLimitPolicy get(String name) {
    RateLimitPolicy policy = policies.get(name);
    if (policy == null) {
      throw new NoSuchElementException(
          "Politica de rate limit no configurada: '"
              + name
              + "'. Definir app.ratelimit.policies."
              + name);
    }
    return policy;
  }

  /** Indica si existe una politica con ese nombre. */
  public boolean exists(String name) {
    return policies.containsKey(name);
  }

  /** Nombres de las politicas configuradas. */
  public Set<String> names() {
    return policies.keySet();
  }
}
