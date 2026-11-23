package ec.twintailcs.electoral.shared.cache;

import io.quarkus.cache.CacheManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Inicializador de caches Redis en Quarkus.
 *
 * <p>Quarkus crea caches de forma perezosa (lazy): cuando se llama {@code getCache(name)} por primera
 * vez, el cache se crea. Si esa primera llamada ocurre antes de que CDI reconozca la config
 * {@code quarkus.cache.type=redis}, Quarkus lo crea con el backend equivocado (Caffeine o NoOp).
 *
 * <p>Este bean force-crea los caches al arrancar la aplicacion, cuando ya conoce que el backend es
 * Redis. Garantiza que {@code cacheManager.getCache(name)} siempre retorna Optional.of() en tests y
 * produccion.
 */
@ApplicationScoped
class CacheInitializer {

  private static final Logger LOG = Logger.getLogger(CacheInitializer.class);

  @Inject CacheManager cacheManager;

  @PostConstruct
  void initialize() {
    for (String name : new String[] {"cargos", "partidos", "candidatos", "geografia"}) {
      var cache = cacheManager.getCache(name);
      if (cache.isPresent()) {
        LOG.infof("Cache '%s' initialized: %s", name, cache.get().getClass().getSimpleName());
      } else {
        LOG.warnf("Cache '%s' not found after initialization attempt", name);
      }
    }
  }
}
