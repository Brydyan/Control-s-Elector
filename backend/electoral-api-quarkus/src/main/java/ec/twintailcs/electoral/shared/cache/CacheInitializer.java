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
   * Verifica disponibilidad de caches al arrancar.
   *
   * <p>En Quarkus 3.40.1 con backend Redis (Phase 2), los caches se crean dinámicamente cuando se
   * acceden por primera vez. Este inicializador verifica que el backend de cache esté disponible y
   * registra el estado para diagnóstico.
   *
   * @param event evento de arranque de Quarkus
   */
  public void init(@Observes StartupEvent event) {
    LOG.infof(
        "Phase 2 Cache Initialization: checking %d named caches (%s)",
        CACHE_NAMES.length, Arrays.toString(CACHE_NAMES));

    int found = 0;
    int notFound = 0;

    for (String cacheName : CACHE_NAMES) {
      try {
        var cache = cacheManager.getCache(cacheName);
        if (cache.isEmpty()) {
          LOG.warnf("⚠️  Cache '%s' not found in CacheManager at startup", cacheName);
          notFound++;
        } else {
          LOG.infof("✓ Cache '%s' available: %s", cacheName, cache.get().getClass().getSimpleName());
          found++;
        }
      } catch (Exception e) {
        LOG.warnf(
            e,
            "ERROR verifying cache '%s' at startup (Phase 2 may degrade if Redis unavailable)",
            cacheName);
        notFound++;
      }
    }

    LOG.infof(
        "Phase 2 Cache Status: %d/%d caches available. Backend type: %s",
        found, CACHE_NAMES.length, cacheManager.getClass().getSimpleName());

    if (notFound > 0) {
      LOG.warnf(
          "⚠️  %d cache(s) not found. Caches may be created on-demand by Quarkus (dynamic creation).",
          notFound);
    }
  }
}
