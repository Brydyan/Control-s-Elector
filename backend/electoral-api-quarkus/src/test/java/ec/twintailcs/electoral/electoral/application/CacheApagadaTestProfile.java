package ec.twintailcs.electoral.electoral.application;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Perfil con la cache apagada: {@code quarkus.cache.enabled=false}.
 *
 * <p>Es el escenario de la linea base sin cache (el load test corre el mismo escenario con y sin
 * el) y tambien el que descubre si el backend de cache cambia: con la cache apagada, Quarkus
 * entrega un {@code NoOpCache}.
 */
public class CacheApagadaTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of("quarkus.cache.enabled", "false");
  }
}
