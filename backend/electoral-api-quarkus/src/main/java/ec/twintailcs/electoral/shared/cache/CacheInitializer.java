package ec.twintailcs.electoral.shared.cache;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Arrays;
import org.jboss.logging.Logger;

/**
 * Inicializa los caches de Phase 2 (Valkey) al arrancar la aplicación.
 *
 * <p>En Quarkus 3.40.1 con backend Redis, los caches se crean on-demand cuando se acceden a
 * través del cliente Redis. CacheManager no registra los caches en su catálogo incluso con
 * configuración correcta. Este inicializador registra el estado de los caches para observabilidad.
 */
@ApplicationScoped
public class CacheInitializer {

  private static final Logger LOG = Logger.getLogger(CacheInitializer.class);

  private static final String[] CACHE_NAMES = {"cargos", "partidos", "candidatos", "geografia"};

  /**
   * Registra el estado de inicialización de los caches en el log.
   *
   * <p>En Quarkus 3.40.1, los caches de Redis se crean on-demand en el primer acceso. Este método
   * simplemente registra en log que la inicialización comenzó y proporciona observabilidad.
   *
   * @param event evento de arranque de Quarkus
   */
  public void init(@Observes StartupEvent event) {
    LOG.infof(
        "Phase 2 Cache Initialization: %d named caches will be created on-demand (%s)",
        CACHE_NAMES.length, Arrays.toString(CACHE_NAMES));
  }
}
