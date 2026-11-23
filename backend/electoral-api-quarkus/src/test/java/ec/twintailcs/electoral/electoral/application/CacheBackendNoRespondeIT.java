package ec.twintailcs.electoral.electoral.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.shared.cache.CacheDeLectura;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * El backend de cache que no responde es una degradacion, no un error.
 *
 * <p>Es la regresion del 500 intermitente del CI. El sintoma era un {@code
 * ArcUndeclaredThrowableException} (una excepcion checked envuelta por ARC) y un 500 en listados de
 * cargos, candidatos y geografia: los tres unicos caminos que consultan la cache <b>dentro</b> de
 * la transaccion del caso de uso.
 *
 * <p>La cadena era: la llamada a Valkey se quedaba esperando para siempre (sin techo de tiempo), la
 * transaccion de 60 s la.cancelaba el reaper de Narayana, y el commit lanzaba {@code
 * jakarta.transaction.RollbackException}, que al ser checked escapaba del bean interceptado y ARC
 * la envolvia. Con el techo de tiempo, un backend mudo degrada a fallo de cache: se sirve desde la
 * base y la API responde 200.
 *
 * <p>Este test afirma las dos mitades: no hay excepcion y el dato llega igual desde la base.
 */
@QuarkusTest
@TestProfile(CacheBackendNoRespondeTestProfile.class)
class CacheBackendNoRespondeIT {

  @Inject ConfiguracionService configuracionService;
  @Inject CacheDeLectura cache;

  @Test
  @Timeout(60)
  @DisplayName("con el backend de cache sin respuesta, el listado de cargos se sirve desde la base")
  void elListadoDeCargosDegradaAMiss() {
    List<Cargo> cargos = configuracionService.listCargos();

    assertThat(cargos)
        .as("el listado se sirve desde la base aunque la cache no conteste")
        .isNotEmpty();
    assertThat(cargos.get(0).getNombre()).isNotBlank();
  }

  @Test
  @Timeout(60)
  @DisplayName(
      "con el backend de cache sin respuesta, la escritura en la cache no rompe la operacion")
  void laEscrituraEnLaCacheDegradaSinError() {
    // El camino de escritura es el otro lado del mismo problema: si el write se queda esperando
    // para siempre, tumba la transaccion igual que la lectura.
    configuracionService.listCargos();

    assertThat(cache).isNotNull();
  }
}
