package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los tests de geografia contra una base real.
 *
 * <p>El arbol geografico se arma con JOINs sobre las tablas {@code provincia}, {@code canton},
 * {@code parroquia} y {@code jrv}: un test sin base de verdad no probaria ni los JOINs ni el orden.
 * El seed de referencia entra porque las cuatro tablas tienen datos de ejemplo.
 *
 * <p>{@code database.generation=validate} deja que Hibernate valide el schema contra las entidades:
 * si alguien mapea una columna que no existe, el build falla aqui y no en produccion.
 */
public class GeografiaTestProfile implements QuarkusTestProfile {

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
