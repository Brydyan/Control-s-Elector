package ec.twintailcs.electoral.legal.web;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Perfil de test para los documentos legales publicos, sin base de datos.
 *
 * <p>Los documentos legales y la version vigente no tocan la base: son HTML estatico y un valor de
 * configuracion. Este perfil apaga la base (validacion de Hibernate y migracion de Liquibase) para
 * que el test corra en cualquier entorno, con o sin Docker, igual que hace {@code CorsTestProfile}
 * para el filtro de CORS.
 *
 * <p>No se toca ninguna propiedad legal: la version que se verifica es la de {@code
 * application.properties}.
 */
public class LegalPublicTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.hibernate-orm.database.generation", "none",
        "quarkus.liquibase.migrate-at-start", "false",
        "quarkus.datasource.devservices.enabled", "false",
        "quarkus.datasource.health.enabled", "false");
  }
}
