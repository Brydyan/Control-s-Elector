package ec.twintailcs.electoral.shared.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de integración para {@link MetricsAliasResource}.
 *
 * <p>Verifica que el puerto principal publica las métricas en {@code /q/metrics}, que es la ruta
 * que el SDD de caché declara pública y scrapeable (D.5) y la que documentan los manuales de caché.
 * El management interface sigue siendo el dueño real: este endpoint solo lo expone donde el scraper
 * lo alcanza.
 */
@QuarkusTest
class MetricsAliasResourceTest {

  @Test
  @DisplayName("/q/metrics responde 200 en el puerto principal")
  void metricasEnElPuertoPrincipal() {
    given().when().get("/q/metrics").then().statusCode(200).body(not(emptyString()));
  }

  @Test
  @DisplayName("/q/metrics devuelve el export de Prometheus del management interface")
  void metricasConElContenidoDelManagementInterface() {
    given()
        .when()
        .get("/q/metrics")
        .then()
        .statusCode(200)
        .body(containsString("jvm_memory_used_bytes"));
  }

  @Test
  @DisplayName("/q/metrics es publica: no hace falta token")
  void metricasSinToken() {
    given().when().get("/q/metrics").then().statusCode(200);
  }
}
