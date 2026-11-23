package ec.twintailcs.electoral.shared.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import ec.twintailcs.electoral.electoral.application.usecase.ListarCargosUseCase;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import io.quarkus.cache.CacheManager;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D.1 del ODD de Phase 2: la cache distribuida contra un servidor Valkey de verdad.
 *
 * <p>Con Caffeine la cache vivia dentro del proceso: cada replica tenia la suya y una invalidacion
 * en una replica no llegaba a las otras. Valkey rompe esa Separation: todas las replicas leen y
 * escriben el mismo almacen. Este test afirma esa propiedad, no solo que "la cache anda".
 *
 * <p>Las cuatro afirmaciones:
 *
 * <ul>
 *   <li>el round-trip con tipo generico conserva los ids: {@code List<Cargo>} vuelve como {@code
 *       List<Cargo>}, no como una lista de objetos sin tipo;
 *   <li>invalidar deja de servir el valor viejo;
 *   <li>la entrada caduca cuando pasa el TTL;
 *   <li>otra replica ve lo que escribio esta, y su invalidacion se propaga — la ultima es la que
 *       mata el sintoma raiz del ODD (datos stale entre replicas).
 * </ul>
 */
@QuarkusTest
@TestProfile(CacheValkeyTestProfile.class)
class CacheDeLecturaValkeyIT {

  private static final TypeReference<List<Cargo>> TIPO_CARGOS = new TypeReference<>() {};

  @Inject CacheDeLectura cache;
  @Inject CacheManager cacheManager;
  @Inject ReactiveRedisDataSource redis;
  @Inject ListarCargosUseCase listarCargos;

  @BeforeEach
  void vaciarCaches() {
    cacheManager
        .getCacheNames()
        .forEach(
            nombre ->
                cacheManager
                    .getCache(nombre)
                    .ifPresent(cache -> cache.invalidateAll().await().indefinitely()));
  }

  @Test
  @DisplayName("Valkey: el round-trip con tipo generico conserva los ids de los cargos")
  void elRoundTripConTipoGenericoConservaLosIds() {
    List<Cargo> deLaBase = listarCargos.execute();
    assertThat(deLaBase).as("el seed trae cargos").isNotEmpty();

    Optional<List<Cargo>> deLaCache = cache.leer("cargos", "todos", TIPO_CARGOS);

    assertThat(deLaCache).as("el cargo se lee de la cache").isPresent();
    List<Cargo> cacheados = deLaCache.get();
    assertThat(cacheados).as("la cache trae todos los cargos").hasSameSizeAs(deLaBase);
    assertThat(cacheados.stream().map(Cargo::getId).toList())
        .as("los ids sobreviven a la serializacion: sin esto la cache serviria objetos sin pk")
        .containsExactlyElementsOf(deLaBase.stream().map(Cargo::getId).toList());
    assertThat(cacheados.get(0).getNombre()).isEqualTo(deLaBase.get(0).getNombre());
    assertThat(cacheados.get(0).getNivel()).isEqualTo(deLaBase.get(0).getNivel());
    assertThat(cacheados.get(0).getPuestos()).isEqualTo(deLaBase.get(0).getPuestos());
  }

  @Test
  @DisplayName("Valkey: invalidar la cache deja de servir el valor viejo")
  void invalidarDejaDeServirElValorViejo() {
    listarCargos.execute();
    assertThat(cache.leer("cargos", "todos", TIPO_CARGOS)).as("la cache esta llena").isPresent();

    cacheManager.getCache("cargos").orElseThrow().invalidateAll().await().indefinitely();

    assertThat(cache.leer("cargos", "todos", TIPO_CARGOS))
        .as("despues de invalidar, la cache no sirve el valor viejo")
        .isEmpty();
  }

  @Test
  @DisplayName("Valkey: la entrada caduca cuando pasa el TTL")
  void laEntradaCaducaCuandoPasaElTtl() throws InterruptedException {
    listarCargos.execute();
    assertThat(cache.leer("cargos", "todos", TIPO_CARGOS))
        .as("recien escrita esta en la cache")
        .isPresent();

    // El TTL de 'cargos' en este perfil es de 1 segundo.
    Thread.sleep(1500);

    assertThat(cache.leer("cargos", "todos", TIPO_CARGOS))
        .as("pasado el TTL, la entrada ya no esta en la cache")
        .isEmpty();
  }

  @Test
  @DisplayName("Valkey: otra replica ve lo que escribio esta, y su invalidacion se propaga")
  void otraReplicaComparteElMismoAlmacen() {
    listarCargos.execute();

    // Un cliente distinto contra el mismo servidor: es lo que hace la otra replica de Railway.
    ReactiveKeyCommands<String> claves = redis.key();
    List<String> clavesCargos = claves.keys("*cargos*").await().indefinitely();
    assertThat(clavesCargos)
        .as("la entrada se escribio en el servidor compartido, no en el heap de esta replica")
        .isNotEmpty();

    String clave = clavesCargos.get(0);
    ReactiveValueCommands<String, String> valores = redis.value(String.class);
    String crudo = valores.get(clave).await().indefinitely();
    assertThat(crudo).as("el valor esta serializado en Valkey").contains("\"nombre\"");
    assertThat(claves.ttl(clave).await().indefinitely())
        .as("la entrada tiene TTL: sin TTL, una entrada huerfana vive para siempre")
        .isGreaterThan(0);

    // La otra replica invalida. Esta replica tiene que verlo en la siguiente lectura: si la cache
    // tuviera una copia local, serviria el dato viejo y el sintoma del ODD seguiria existiendo.
    claves.del(clave).await().indefinitely();

    assertThat(cache.leer("cargos", "todos", TIPO_CARGOS))
        .as("la invalidacion de otra replica se ve aca: no hay copia local")
        .isEmpty();
  }
}
