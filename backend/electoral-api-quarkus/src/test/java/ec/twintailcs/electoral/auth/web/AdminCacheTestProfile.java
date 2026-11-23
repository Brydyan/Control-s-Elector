package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import ec.twintailcs.electoral.shared.testing.ValkeyTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los tests del endpoint de vaciado de cache.
 *
 * <p>Postgres real (Testcontainers) porque el endpoint exige identidad y audita. Redis/Valkey real
 * porque Phase 2 usa cache distribuida.
 */
public class AdminCacheTestProfile implements QuarkusTestProfile {

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
        "quarkus.cache.type", "redis",
        "quarkus.cache.redis.client-type", "standalone",
        "quarkus.cache.names", "cargos,partidos,candidatos,geografia");
  }
}
