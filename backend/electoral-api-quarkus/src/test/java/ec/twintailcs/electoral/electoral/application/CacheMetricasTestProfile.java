package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los tests de metricas de cache.
 *
 * <p>Postgres real (Testcontainers) porque el cache guarda lo que devuelve la base: sin base de
 * verdad el contador de consultas no mediria nada. Ademas se enciende el contador de Hibernate, que
 * es el que permite afirmar "la segunda llamada no consulto" en vez de suponerlo.
 */
public class CacheMetricasTestProfile implements QuarkusTestProfile {

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
        "quarkus.hibernate-orm.log.statistics", "true");
  }
}
