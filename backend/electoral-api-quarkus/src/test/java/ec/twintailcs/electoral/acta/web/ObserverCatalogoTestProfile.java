package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil del catalogo batch del observer.
 *
 * <p>Corre contra un Postgres real con el seed de referencia: el endpoint lee {@code jrv_cargo},
 * {@code cargo}, {@code candidato} y {@code partido}, y sin esos datos el test no probaria nada. El
 * seed es el mismo que usa geografia y trae los cargos habilitados por JRV.
 */
public class ObserverCatalogoTestProfile implements QuarkusTestProfile {

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
