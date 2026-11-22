package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los tests del endpoint de vaciado de cache.
 *
 * <p>Postgres real (Testcontainers) porque el endpoint exige identidad: el vaciado se audita con el
 * usuario que lo pidio y esa fila va a {@code audit_log}, que tiene RLS. Un test sin base de verdad
 * no probaria ni la autorizacion ni el rastro.
 */
public class AdminCacheTestProfile implements QuarkusTestProfile {

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
