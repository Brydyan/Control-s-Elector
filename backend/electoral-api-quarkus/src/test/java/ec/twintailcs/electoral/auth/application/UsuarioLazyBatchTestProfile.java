package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil del test de carga diferida de {@code Usuario.rol}.
 *
 * <p>Postgres real porque el test cuenta consultas de Hibernate: sin base de datos no hay consultas
 * que contar, y un contador en cero haria pasar el test sin probar nada. El seed entra porque el
 * listado necesita usuarios y roles de verdad.
 */
public class UsuarioLazyBatchTestProfile implements QuarkusTestProfile {

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
