package ec.twintailcs.electoral.shared.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Duration;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-CACHE-STRAT-01 D.2: el TTL de la caché es el que dice, y no un número decorativo.
 *
 * <p>La decisión D2 (Caffeine en memoria) deja la invalidación entre réplicas en manos del TTL: si
 * mañana el backend corre en más de una réplica, una copia puede quedar desfasada hasta que su TTL
 * venza. Ese TTL es la única red de seguridad, así que hay que comprobar dos cosas:
 *
 * <ul>
 *   <li>que el valor por defecto es el que se decidió (2 minutos) y no el que dejó el diseño
 *       original (10 minutos), porque un TTL de 10 minutos significa 10 minutos de dato viejo;
 *   <li>que la entrada <b>realmente</b> caduca cuando pasa el TTL: un TTL configurado pero ignorado
 *       es peor que no tener TTL, porque aparenta acotar el problema sin acotarlo.
 * </ul>
 */
@QuarkusTest
@TestProfile(CacheTTLTestProfile.class)
class CacheTTLIT {

  @Inject CacheDeLectura cache;

  @Test
  @DisplayName("el TTL por defecto de las caches de configuracion es de 2 minutos")
  void elTtlPorDefectoEsDeDosMinutos() {
    Duration ttl =
        ConfigProvider.getConfig()
            .getValue("quarkus.cache.redis.cargos.expire-after-write", Duration.class);

    assertThat(ttl)
        .as("el TTL de produccion de la cache de cargos (acota el desfase entre replicas)")
        .isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  @DisplayName("la entrada de la cache caduca cuando pasa el TTL")
  void laEntradaCaducaCuandoPasaElTtl() throws InterruptedException {
    cache.guardar("partidos", "clave-de-prueba", java.util.List.of("valor"));

    assertThat(
            cache.leer(
                "partidos", "clave-de-prueba", new TypeReference<java.util.List<String>>() {}))
        .as("recien guardada, la entrada esta en la cache")
        .contains(java.util.List.of("valor"));

    // El TTL de 'partidos' en este perfil es de 1 segundo.
    Thread.sleep(1500);

    assertThat(
            cache.leer(
                "partidos", "clave-de-prueba", new TypeReference<java.util.List<String>>() {}))
        .as("pasado el TTL, la entrada ya no esta en la cache")
        .isEmpty();
  }
}
