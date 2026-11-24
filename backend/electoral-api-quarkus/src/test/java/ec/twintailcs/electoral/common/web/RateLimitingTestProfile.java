package ec.twintailcs.electoral.common.web;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import ec.twintailcs.electoral.shared.testing.ValkeyTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los IT de rate limiting: PostgreSQL y Redis reales.
 *
 * <p>{@code user-global} baja a 150 para poder probarlo sin miles de requests. El resto de las
 * politicas usa los valores reales de {@code application.properties}.
 */
public class RateLimitingTestProfile implements QuarkusTestProfile {

  static final int USER_GLOBAL_LIMIT = 150;

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
        "app.ratelimit.policies.user-global.limit", String.valueOf(USER_GLOBAL_LIMIT),
        "quarkus.redis.client-type", "standalone",
        "quarkus.cache.type", "redis",
        "quarkus.cache.redis.client-name", "default",
        "quarkus.cache.redis.default-client-type", "standalone");
  }
}
