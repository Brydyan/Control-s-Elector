package ec.twintailcs.electoral.legal.web;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil de los tests del flujo legal contra una base real.
 *
 * <p>A diferencia de {@link LegalPublicTestProfile}, aca la base es parte del contrato: la
 * aceptacion de terminos escribe en {@code terms_acceptance_audit}, la exportacion escribe en
 * {@code user_export_log} y las dos tablas tienen RLS forzada. Un test con base de mentira no
 * probaria nada de eso.
 *
 * <p>El perfil enciende lo que el default apaga:
 *
 * <ul>
 *   <li>{@code migrate-at-start=true}: Liquibase aplica el changelog, incluido el bloque legal.
 *   <li>{@code contexts=seed}: el seed de referencia (el MASTER y los cinco usuarios de ejemplo) es
 *       parte del esquema de pruebas.
 *   <li>{@code database.generation=validate}: Hibernate valida el schema contra las entidades. Es
 *       el control de drift: si alguien cambia una entidad sin cambiar el changelog, este perfil lo
 *       detecta en el build y no en produccion.
 * </ul>
 */
public class LegalFlowTestProfile implements QuarkusTestProfile {

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
