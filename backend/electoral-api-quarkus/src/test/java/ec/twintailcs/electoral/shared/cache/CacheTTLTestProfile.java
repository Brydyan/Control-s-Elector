package ec.twintailcs.electoral.shared.cache;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil del test de TTL de la caché.
 *
 * <p>Postgres real (Testcontainers) porque la aplicación lo necesita para levantar. El TTL de
 * {@code partidos} se baja a 1 segundo: esperar dos minutos dentro de un test no es una opción, y
 * el comportamiento que se quiere comprobar —"la entrada caduca"— es el mismo con cualquier TTL.
 */
public class CacheTTLTestProfile implements QuarkusTestProfile {

  @Override
  public List<TestResourceEntry> testResources() {
    return List.of(new TestResourceEntry(PostgresTestResource.class));
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.liquibase.migrate-at-start", "true",
        "quarkus.liquibase.contexts", "seed",
        "quarkus.hibernate-orm.database.generation", "validate",
        "quarkus.hibernate-orm.log.statistics", "true",
        // TTL corto solo para esta prueba: el resto de las cachés conservan el de producción.
        "quarkus.cache.caffeine.partidos.expire-after-write", "1s");
  }
}
