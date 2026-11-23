package ec.twintailcs.electoral.shared.testing;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Valkey para los tests de rate limiting y de la cache distribuida.
 *
 * <p>Levanta un contenedor Valkey de Testcontainers que sube y baja solo. El servicio de
 * RateLimitingService requiere un servidor Redis-protocol para funcionar, y la cache de Phase 2
 * comparte ese mismo servidor; esta clase lo proporciona en los tests sin que el CI necesite
 * configurar un servicio aparte.
 *
 * <p>La imagen es Valkey y no Redis por licencia: Valkey es BSD-3-Clause (Linux Foundation) y Redis
 * 7.4+ es RSALv2/SSPL, que no es una licencia OSI. Valkey habla el mismo protocolo (RESP), asi que
 * el cliente de Quarkus no cambia. La URL conserva el esquema {@code redis://} porque ese es el que
 * espera la extension {@code quarkus-redis-client}.
 *
 * <p>Testcontainers en modo alpine es rápido pero a veces el puerto está accesible antes de que
 * Valkey esté listo internamente. Se agrega wait strategy que verifica la conexión TCP + el log
 * "Ready to accept connections" para garantizar que Valkey responda a comandos Redis.
 */
public class ValkeyTestResource implements QuarkusTestResourceLifecycleManager {

  private static final Logger LOG = Logger.getLogger(ValkeyTestResource.class);

  private GenericContainer<?> container;

  @Override
  public Map<String, String> start() {
    container =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine"))
            .withExposedPorts(6379)
            .waitingFor(
                new WaitAllStrategy()
                    .withStrategy(Wait.forListeningPort())
                    .withStrategy(Wait.forLogMessage("(?i).*ready to accept connections.*", 1)));
    container.start();

    String host = container.getHost();
    int port = container.getFirstMappedPort();

    // Verificacion adicional: conectar al puerto para garantizar que Valkey responde
    // Testcontainers a veces considera el puerto "listo" antes de que el servicio esté
    // verdaderamente disponible. Este wait adicional descarta ese caso.
    verifyValkeyConnectivity(host, port);

    LOG.infof("Valkey started at redis://%s:%d", host, port);
    return Map.of("quarkus.redis.hosts", "redis://" + host + ":" + port);
  }

  /**
   * Verifica que Valkey responda a conexiones TCP.
   *
   * <p>Intenta conectar hasta 30 veces (3 segundos) para garantizar que el servicio está realmente
   * listo, no solo que el puerto esté abierto.
   */
  private void verifyValkeyConnectivity(String host, int port) {
    int maxAttempts = 30;
    int attempt = 0;
    while (attempt < maxAttempts) {
      try (Socket socket = new Socket(host, port)) {
        LOG.infof("Valkey connectivity verified after %d attempts", attempt);
        return;
      } catch (IOException e) {
        attempt++;
        if (attempt < maxAttempts) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for Valkey", ie);
          }
        }
      }
    }
    throw new RuntimeException(
        "Valkey did not respond after " + maxAttempts + " attempts (3 seconds). Host: "
            + host + ", Port: " + port);
  }

  @Override
  public void stop() {
    if (container != null) {
      container.stop();
    }
  }
}
