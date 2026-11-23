package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Perfil con un backend de cache que <b>acepta la conexion y no responde nunca</b>.
 *
 * <p>Es el caso que tumbaba el CI. No es "el backend esta caido" (conexion rechazada, que degrada
 * al instante) sino "el backend esta despierto pero no contesta": el contenedor del CI que todavia
 * no esta listo para atender comandos, un backend saturado, una red lenta. Contra ese backend, la
 * llamada a la cache se quedaba esperando para siempre <b>dentro de la transaccion</b> del caso de
 * uso; a los 60 s Narayana cancelaba la transaccion y el commit fallaba con
 * {@code jakarta.transaction.RollbackException}, que ARC envuelve en
 * {@code ArcUndeclaredThrowableException}. El sintoma era un 500 en listados que solo debian
 * degradarse a "se sirven desde la base".
 *
 * <p>El socket falso se abre antes de que arranque la aplicacion (el perfil se construye antes del
 * arranque), para que la primera operacion de cache caiga en el, y no en un rechazo de conexion.
 */
public class CacheBackendNoRespondeTestProfile implements QuarkusTestProfile {

  /** Socket que acepta y nunca contesta. Vive mientras dure la JVM de los tests. */
  private static final ServerSocket BLACKHOLE = arranca();

  private static final List<Socket> CONEXIONES = new ArrayList<>();

  private static ServerSocket arranca() {
    try {
      ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
      Thread aceptador =
          new Thread(
              () -> {
                while (!socket.isClosed()) {
                  try {
                    // Se guarda la conexion y no se responde nada: el comando se queda esperando.
                    CONEXIONES.add(socket.accept());
                  } catch (IOException e) {
                    return;
                  }
                }
              },
              "cache-blackhole");
      aceptador.setDaemon(true);
      aceptador.start();
      return socket;
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo abrir el backend de cache que no responde", e);
    }
  }

  @Override
  public List<TestResourceEntry> testResources() {
    return List.of(new TestResourceEntry(PostgresTestResource.class));
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.redis.hosts", "redis://127.0.0.1:" + BLACKHOLE.getLocalPort(),
        // Techo corto para que el test no tarde: lo que se prueba es la degradacion, no la espera.
        "app.cache.timeout", "2s",
        "quarkus.liquibase.migrate-at-start", "true",
        "quarkus.liquibase.contexts", "seed",
        "quarkus.hibernate-orm.database.generation", "validate",
        "quarkus.hibernate-orm.log.statistics", "true");
  }
}
