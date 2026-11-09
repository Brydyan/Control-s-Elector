package ec.twintailcs.electoral.shared.testing;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis para los tests de rate limiting.
 *
 * <p>Levanta un contenedor Redis de Testcontainers que sube y baja solo. El servicio de
 * RateLimitingService requiere Redis para funcionar; esta clase lo proporciona en los tests sin que
 * el CI necesite configurar un servicio Redis aparte.
 */
public class RedisTestResource implements QuarkusTestResourceLifecycleManager {

  private GenericContainer<?> container;

  @Override
  public Map<String, String> start() {
    container =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
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
