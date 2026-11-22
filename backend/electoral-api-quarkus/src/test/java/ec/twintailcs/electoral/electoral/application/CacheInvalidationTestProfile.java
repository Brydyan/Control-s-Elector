package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los tests de invalidacion de cache en las escrituras.
 *
 * <p>Postgres real (Testcontainers) porque el test escribe con la API (alta de cargo y de
 * provincia) y vuelve a leer: sin base de verdad no habria ni escritura ni lectura.
 */
public class CacheInvalidationTestProfile implements QuarkusTestProfile {

  @Override
  public List<TestResourceEntry> testResources() {
    return List.of(new TestResourceEntry(PostgresTestResource.class));
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.liquibase.migrate-at-start", "true",
        "quarkus.liquibase.contexts", "seed",
        "quarkus.hibernate-orm.database.generation", "validate");
  }
}
