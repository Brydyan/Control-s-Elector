package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import ec.twintailcs.electoral.shared.testing.ValkeyTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los tests de invalidacion de cache en las escrituras.
 *
 * <p>Postgres real (Testcontainers) porque el test escribe con la API y vuelve a leer. Redis/Valkey
 * real porque Phase 2 invalida el cache distribuido via @CacheInvalidateAll.
 */
public class CacheInvalidationTestProfile implements QuarkusTestProfile {

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
        "quarkus.redis.client-type", "standalone",
        "quarkus.cache.type", "redis",
        "quarkus.cache.redis.client-name", "default");
  }
}
