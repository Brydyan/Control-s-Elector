package ec.twintailcs.electoral.shared.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.core.type.TypeReference;
import ec.twintailcs.electoral.electoral.application.usecase.ListarCargosUseCase;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La cache caida no es un error: es una degradacion.
 *
 * <p>Con Caffeine el backend de cache estaba dentro del proceso, asi que no podia caerse. Con
 * Valkey es un servicio externo y puede caerse: se cae el despliegue, una red se parte, Valkey se
 * reinicia. Si en ese momento la API devolviera 500, la cache habria dejaron de ser una aceleracion
 * para convertirse en un punto unico de falla.
 *
 * <p>Este test afirma lo contrario: con el backend caido, la app sigue arrancando y sigue sirviendo
 * los datos desde la base. Solo se pierde el hit ratio.
 */
@QuarkusTest
@TestProfile(CacheValkeyCaidaTestProfile.class)
class CacheValkeyCaidaIT {

  private static final TypeReference<List<Cargo>> TIPO_CARGOS = new TypeReference<>() {};

  @Inject CacheDeLectura cache;
  @Inject ListarCargosUseCase listarCargos;

  @Test
  @DisplayName("Valkey caido: leer devuelve vacio y no lanza excepcion")
  void leerConElBackendCaidoDegrada() {
    assertThat(cache.leer("cargos", "todos", TIPO_CARGOS))
        .as("sin backend, la lectura no encuentra nada (y eso es todo)")
        .isEmpty();
  }

  @Test
  @DisplayName("Valkey caido: guardar no lanza excepcion")
  void guardarConElBackendCaidoNoLanza() {
    assertThatNoException()
        .as("escribir en la cache es opcional: si falla, se sigue")
        .isThrownBy(() -> cache.guardar("cargos", "todos", List.of()));
  }

  @Test
  @DisplayName("Valkey caido: el caso de uso sigue sirviendo los datos desde la base")
  void elCasoDeUsoSigueSirviendoDesdeLaBase() {
    assertThat(listarCargos.execute()).as("la cache caida se pierde; los datos no").isNotEmpty();
  }
}
