package ec.twintailcs.electoral.shared.startup;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Modo "migrate-only": el proceso migra y se termina.
 *
 * <p>Existe para separar las dos credenciales de la base de datos:
 *
 * <ul>
 *   <li>La <b>migración</b> necesita el dueño de la base: es el único que puede crear el schema.
 *   <li>La <b>aplicación</b> debe correr con un rol que NO pueda saltarse las políticas de RLS (un
 *       superusuario las ignora, aunque la tabla tenga {@code FORCE ROW LEVEL SECURITY}).
 * </ul>
 *
 * <p>Con este modo, el contenedor que migra lleva las credenciales del dueño y el contenedor de la
 * API no las tiene: la API no puede saltarse RLS ni por accidente ni por un fallo.
 *
 * <p>El orden importa: Liquibase con {@code migrate-at-start=true} corre durante el arranque, antes
 * del {@link StartupEvent}. Por eso salir en el {@code StartupEvent} es salir DESPUÉS de migrar.
 *
 * <p>Se activa con {@code APP_MIGRATE_ONLY=true} ({@code app.migrate-only}).
 */
@ApplicationScoped
public class MigrateOnlyRunner {

  private static final Logger LOG = Logger.getLogger(MigrateOnlyRunner.class);

  @ConfigProperty(name = "app.migrate-only", defaultValue = "false")
  boolean migrateOnly;

  /**
   * Sale del proceso cuando la app arrancó, que es cuando la migración ya termino.
   *
   * @param event evento de arranque de Quarkus
   */
  void onStartup(@Observes StartupEvent event) {
    if (!migrateOnly) {
      return;
    }
    LOG.info("Modo migrate-only: la migracion termino, el proceso sale.");
    Quarkus.asyncExit(0);
  }
}
