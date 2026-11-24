package ec.twintailcs.electoral.shared.cache;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import ec.twintailcs.electoral.shared.testing.ValkeyTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;

/**
 * Perfil del test de la cache contra Valkey real (D.1 del ODD de Phase 2).
 *
 * <p>Postgres real (Testcontainers) porque la cache guarda lo que devuelve la base, y Valkey real
 * (Testcontainers) porque la cache distribuida es el objeto del test: contra un servidor de verdad
 * se puede afirmar el round-trip con tipo generico, el TTL y la invalidacion, cosas que un mock no
 * probarian.
 *
 * <p>El TTL de {@code cargos} se baja a 1 segundo: esperar dos minutos dentro de un test no es una
 * opcion, y el comportamiento que se quiere comprobar —"la entrada caduca"— es el mismo con
 * cualquier TTL.
 */
public class CacheValkeyTestProfile implements QuarkusTestProfile {

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
        // Asegurar que la cache usa Redis en lugar de Caffeine.
        "quarkus.cache.type", "redis",
        // TTL corto solo para esta prueba: el resto de las caches conserva el de produccion.
        "quarkus.cache.redis.cargos.expire-after-write", "1s");
  }
}
