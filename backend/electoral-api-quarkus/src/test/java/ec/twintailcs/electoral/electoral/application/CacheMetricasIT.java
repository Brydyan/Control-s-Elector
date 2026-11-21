package ec.twintailcs.electoral.electoral.application;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-CACHE-STRAT-01 D.5: el hit ratio de la cache tiene que ser visible.
 *
 * <p>Sin metricas, "la cache esta funcionando" es una opinion. Con micrometer, la cache publica sus
 * aciertos y sus fallos: este test comprueba que las metricas existen y que cuentan, para que un
 * tablero pueda decir cuanto se esta sirviendo desde la cache y cuanto esta yendo a la base.
 */
@QuarkusTest
@TestProfile(CacheMetricasTestProfile.class)
class CacheMetricasIT {

  @Inject ConfiguracionService configuracionService;

  @BeforeEach
  void primedCache() {
    // La cache se llena: sin una lectura previa no hay acierto que contar.
    configuracionService.listCargos();
    configuracionService.listCargos();
  }

  @Test
  @DisplayName("las metricas de la cache se publican en /q/metrics")
  void lasMetricasDeLaCacheSePublican() {
    String metricas = metricas();

    assertThat(metricas).as("las metricas de la cache estan publicadas").contains("cache_hits");
    assertThat(metricas).as("las metricas de la cache estan publicadas").contains("cache_misses");
    assertThat(metricas)
        .as("la cache de cargos aparece con su nombre")
        .contains("cache=\"cargos\"");
  }

  @Test
  @DisplayName("la metrica cuenta los aciertos de la cache")
  void laMetricaCuentaLosAciertos() {
    String metricas = metricas();

    assertThat(metricas)
        .as("hay aciertos registrados para la cache de cargos")
        .containsPattern("cache_hits_total\\{[^}]*cache=\"cargos\"[^}]*\\}\\s+[\\d.]+");
  }

  /** Cuerpo de /q/metrics en formato Prometheus. */
  private String metricas() {
    var res = given().when().get("/q/metrics");
    assertThat(res.getStatusCode()).as("el endpoint de metricas responde").isEqualTo(200);
    return res.asString();
  }
}
