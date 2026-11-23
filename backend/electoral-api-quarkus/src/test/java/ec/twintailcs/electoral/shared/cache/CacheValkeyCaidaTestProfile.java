package ec.twintailcs.electoral.shared.cache;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil del test de cache con el backend caido.
 *
 * <p>No hay servidor Valkey: {@code quarkus.redis.hosts} apunta a un puerto donde nadie escucha.
 * Postgres si esta, porque lo que se quiere comprobar es que la aplicacion sigue sirviendo datos
 * cuando la cache no esta — la cache es una aceleracion, no un requisito.
 */
public class CacheValkeyCaidaTestProfile implements QuarkusTestProfile {

  /** Puerto donde no escucha nadie: el backend esta caido a proposito. */
  static final String HOST_SIN_SERVIDOR = "redis://127.0.0.1:1";

  @Override
  public List<TestResourceEntry> testResources() {
    return List.of(new TestResourceEntry(PostgresTestResource.class));
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.liquibase.migrate-at-start", "true",
        "quarkus.liquibase.contexts", "seed",
        "quarkus.hibernate-orm.database.generation", "validate",
        // El backend caido, y con un timeout corto para que el test no espere.
        "quarkus.redis.hosts", HOST_SIN_SERVIDOR,
        "quarkus.redis.timeout", "1s",
        "app.ratelimit.backend-timeout", "1s");
  }
}
