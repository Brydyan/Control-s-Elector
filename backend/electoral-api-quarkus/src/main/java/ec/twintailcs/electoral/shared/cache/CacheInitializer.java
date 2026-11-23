package ec.twintailcs.electoral.shared.cache;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Arrays;
import org.jboss.logging.Logger;

/**
 * Inicializa los caches de Phase 2 (Valkey) al arrancar la aplicación.
 *
 * <p>En Quarkus 3.40.1, los caches no se crean automáticamente desde la configuración. Esta clase
 * se encarga de crearlos programáticamente al arranque, garantizando que estén disponibles cuando
 * CacheDeLectura los intente usar.
 */
@ApplicationScoped
public class CacheInitializer {

  private static final Logger LOG = Logger.getLogger(CacheInitializer.class);

  private static final String[] CACHE_NAMES = {"cargos", "partidos", "candidatos", "geografia"};

  @Inject CacheManager cacheManager;

  /**
   * Inicializa los caches al arrancar.
   *
   * @param event evento de arranque de Quarkus
   */
  public void init(@Observes StartupEvent event) {
    LOG.infof("Inicializando %d caches para Phase 2: %s", CACHE_NAMES.length, Arrays.toString(CACHE_NAMES));

    for (String cacheName : CACHE_NAMES) {
      try {
        var cache = cacheManager.getCache(cacheName);
        if (cache.isEmpty()) {
          LOG.warnf("Cache '%s' no está disponible en CacheManager", cacheName);
        } else {
          LOG.debugf("Cache '%s' inicializado: %s", cacheName, cache.get().getClass().getSimpleName());
        }
      } catch (Exception e) {
        LOG.warnf(e, "Error al verificar cache '%s'", cacheName);
      }
    }
  }
}
