package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import ec.twintailcs.electoral.shared.testing.ValkeyTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los tests de cache por servicio.
 *
 * <p>Postgres real (Testcontainers) porque el cache guarda lo que devuelve la base. Redis/Valkey
 * real porque Phase 2 usa cache distribuida. Se enciende el contador de Hibernate, que permite
 * afirmar "la segunda llamada no consulto" en vez de suponerlo.
 */
public class CacheServicioTestProfile implements QuarkusTestProfile {

  @Override
  public List<TestResourceEntry> testResources() {
    return List.of(
        new TestResourceEntry(PostgresTestResource.class),
        new TestResourceEntry(ValkeyTestResource.class));
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.liquibase.migrate-at-start", "true",
        "quarkus.liquibase.contexts", "seed",
        "quarkus.hibernate-orm.database.generation", "validate",
        "quarkus.hibernate-orm.log.statistics", "true",
        "quarkus.redis.client-type", "standalone",
        "quarkus.cache.type", "redis",
        "quarkus.cache.redis.client-name", "default");
  }
}
