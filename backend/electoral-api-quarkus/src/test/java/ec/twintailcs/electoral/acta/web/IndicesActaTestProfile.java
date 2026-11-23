package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil del test de indices de actas.
 *
 * <p>Postgres real porque lo que se comprueba es el catalogo de la base: los indices los crea
 * Liquibase al migrar, asi que sin una base migrada no hay nada que afirmar.
 */
public class IndicesActaTestProfile implements QuarkusTestProfile {

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
