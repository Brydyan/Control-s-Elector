package ec.twintailcs.electoral.shared.testing;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
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
 */
public class ValkeyTestResource implements QuarkusTestResourceLifecycleManager {

  private GenericContainer<?> container;

  @Override
  public Map<String, String> start() {
    container =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine"))
            .withExposedPorts(6379);
    container.start();

    String host = container.getHost();
    int port = container.getFirstMappedPort();

    return Map.of("quarkus.redis.hosts", "redis://" + host + ":" + port);
  }

  @Override
  public void stop() {
    if (container != null) {
      container.stop();
    }
  }
}
