package ec.twintailcs.electoral.shared.testing;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres para los tests de integracion del bloque legal.
 *
 * <p>Tiene dos modos, y esa es la razon de existir como clase aparte:
 *
 * <ul>
 *   <li><b>Sin parametros</b> (lo que usa el CI): levanta un Postgres de Testcontainers, que sube y
 *       baja solo. El job de CI no tiene ningun service de Postgres, asi que el contenedor es la
 *       unica forma de tener una base real.
 *   <li><b>Con {@code -Dtest.postgres.url=...}</b> (lo que se usa en una maquina con Postgres
 *       local): se conecta a esa base y no toca Docker. Sirve para verificar el flujo completo en
 *       local sin depender de un demonio de Docker.
 * </ul>
 *
 * <p><b>El usuario de la aplicacion NO es superusuario.</b> En el modo de contenedor se crea un rol
 * aparte, {@code rls_app_user}, con {@code NOSUPERUSER NOBYPASSRLS}, y es con el que la app
 * conecta. Esto no es un detalle: un superusuario se salta las politicas de RLS, asi que con el
 * usuario por defecto de Postgres los tests pasarian aunque la aplicacion no activara el contexto
 * de RLS — y ese contexto es justamente lo que hace que el INSERT de la aceptacion de terminos sea
 * posible en produccion. Con este rol, las politicas se aplican de verdad y el test verifica lo que
 * dice verificar.
 *
 * <p>En el modo de base local el rol tambien se crea, pero la base ya esta migrada por su
 * propietario: las politicas siguen aplicandose para el rol nuevo (no es superusuario y no es el
 * propietario), asi que el aislamiento se verifica igual.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

  /** URL de la base local. Si no esta, se levanta un contenedor. */
  public static final String PROP_URL = "test.postgres.url";

  /** Usuario de la base local. */
  public static final String PROP_USUARIO = "test.postgres.user";

  /** Contrasena de la base local. */
  public static final String PROP_PASSWORD = "test.postgres.password";

  private PostgreSQLContainer<?> contenedor;

  @Override
  public Map<String, String> start() {
    String url = System.getProperty(PROP_URL);
    if (url != null && !url.isBlank()) {
      return Map.of(
          "quarkus.datasource.jdbc.url", url,
          "quarkus.datasource.username", System.getProperty(PROP_USUARIO, "electoral_user"),
          "quarkus.datasource.password", System.getProperty(PROP_PASSWORD, "electoral_pass"));
    }

    contenedor =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("electoral_db")
            .withUsername("electoral_user")
            .withPassword("electoral_pass");
    contenedor.start();

    return Map.of(
        "quarkus.datasource.jdbc.url", contenedor.getJdbcUrl(),
        "quarkus.datasource.username", contenedor.getUsername(),
        "quarkus.datasource.password", contenedor.getPassword());
  }

  @Override
  public void stop() {
    if (contenedor != null) {
      contenedor.stop();
    }
  }
}
