package ec.twintailcs.electoral.shared.config;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Perfil de test para arrancar Quarkus sin base de datos.
 *
 * <p>El test de CORS solo necesita el filtro HTTP de Vert.x: no toca la base ni una regla de
 * negocio. Pero {@code @QuarkusTest} levanta la aplicacion completa, y la aplicacion completa por
 * defecto valida el schema con Hibernate y aplica Liquibase al arrancar — que exige un Postgres
 * vivo.
 *
 * <p>Este perfil apaga las dos cosas ({@code database.generation=none} y {@code
 * liquibase.migrate-at-start=false}) para que el test corra en cualquier entorno, con o sin Docker.
 * No se toca la configuracion de CORS: eso es justamente lo que el test verifica.
 *
 * <p>El datasource sigue declarado (la URL viene de {@code application.properties}), asi que
 * Quarkus no intenta levantar los dev services de Postgres: el pool de conexiones es lazy y
 * mientras ninguna pida consulte la base no hay ninguna conexion abierta.
 */
public class CorsTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    // OJO: este perfil NO toca ninguna propiedad de CORS a proposito. Sobrescribir
    // `quarkus.http.cors.access-control-allow-credentials` desde aca hacia que la
    // mutacion de esa propiedad en application.properties NO cayera en ningun test:
    // el perfil la ponia en "true" y el test nunca veia el valor real. Las
    // propiedades de CORS que se verifican son las de application.properties, sin
    // excepciones.
    return Map.of(
        "quarkus.hibernate-orm.database.generation", "none",
        "quarkus.liquibase.migrate-at-start", "false",
        "quarkus.datasource.devservices.enabled", "false",
        // El health check de datasource responde 503 sin base de datos. El test usa
        // /actuator/health como endpoint publico para comprobar las cabeceras CORS de una
        // peticion real, asi que el check de la base se apaga: lo que se prueba aca es el
        // filtro CORS, no la salud de la base.
        "quarkus.datasource.health.enabled", "false");
  }
}
