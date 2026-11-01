package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del dashboard: resumen, resultados, progreso y novedades.
 *
 * <p>El dashboard es la vista de solo lectura del ADMIN. El guard de clase es
 * {@code hasAnyRole('ADMIN','MASTER','COORDINATOR')}: OBSERVER no entra, y ADMIN nunca escribe
 * (el controller solo expone {@code @GetMapping}).
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code GET /api/v1/dashboard/resumen} → 200 con el conteo por estado de acta.
 *   <li>{@code GET /api/v1/dashboard/resultados} → 200 con los resultados por cargo.
 *   <li>{@code GET /api/v1/dashboard/progreso} → 200 con el avance por cantón.
 *   <li>{@code GET /api/v1/dashboard/novedades} → 200 con las novedades recientes.
 *   <li>Sin token → 401. Con OBSERVER → 403.
 * </ul>
 */
class DashboardContractIT extends ContractTestSupport {

  /** Prefijo del módulo de dashboard. */
  private static final String DASHBOARD = "/api/v1/dashboard";

  @Test
  @DisplayName("GET /dashboard/resumen devuelve el conteo de actas por estado")
  void resumenDevuelveElConteoPorEstado() {
    Response r = as(tokenFor("ADMIN")).get(DASHBOARD + "/resumen");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getLong("enviadas")).isNotNegative();
    assertThat(r.jsonPath().getLong("enDisputa")).isNotNegative();
    assertThat(r.jsonPath().getLong("corregidas")).isNotNegative();
    assertThat(r.jsonPath().getLong("aprobadas")).isNotNegative();

    long total = r.jsonPath().getLong("totalActivas");
    long suma =
        r.jsonPath().getLong("enviadas")
            + r.jsonPath().getLong("enDisputa")
            + r.jsonPath().getLong("corregidas")
            + r.jsonPath().getLong("aprobadas");
    assertThat(total)
        .as("totalActivas tiene que ser la suma de los cuatro estados activos")
        .isEqualTo(suma);
  }

  @Test
  @DisplayName("GET /dashboard/resultados devuelve los resultados agrupados por cargo")
  void resultadosDevuelvenLosResultadosPorCargo() {
    Response r = as(tokenFor("ADMIN")).get(DASHBOARD + "/resultados");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> cargos = r.jsonPath().getList("$");
    assertThat(cargos).as("body: %s", r.asString()).isNotNull();
    assertThat(cargos)
        .allSatisfy(
            cargo -> {
              assertThat(cargo.get("cargoId")).as("cargoId: %s", cargo).isNotNull();
              assertThat((String) cargo.get("cargoNombre")).as("cargoNombre: %s", cargo).isNotBlank();
              assertThat(cargo.get("candidatos")).as("candidatos: %s", cargo).isNotNull();
            });
  }

  @Test
  @DisplayName("GET /dashboard/progreso devuelve el avance por cantón con su porcentaje")
  void progresoDevuelveElAvancePorCanton() {
    Response r = as(tokenFor("ADMIN")).get(DASHBOARD + "/progreso");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> cantones = r.jsonPath().getList("$");
    assertThat(cantones).as("body: %s", r.asString()).isNotEmpty();
    assertThat(cantones)
        .allSatisfy(
            canton -> {
              assertThat(canton.get("cantonId")).as("cantonId: %s", canton).isNotNull();
              assertThat((String) canton.get("cantonNombre")).as("cantonNombre: %s", canton).isNotBlank();
              assertThat(((Number) canton.get("totalJrvs")).longValue())
                  .as("totalJrvs: %s", canton)
                  .isNotNegative();
              assertThat(((Number) canton.get("jrvsConActa")).longValue())
                  .as("jrvsConActa: %s", canton)
                  .isNotNegative();
              double porcentaje = ((Number) canton.get("porcentaje")).doubleValue();
              assertThat(porcentaje).as("porcentaje: %s", canton).isBetween(0.0, 100.0);
              assertThat(((Number) canton.get("jrvsConActa")).longValue())
                  .as("no puede haber mas JRVs con acta que JRVs totales: %s", canton)
                  .isLessThanOrEqualTo(((Number) canton.get("totalJrvs")).longValue());
            });
  }

  @Test
  @DisplayName("GET /dashboard/novedades devuelve las novedades recientes")
  void novedadesDevuelveLasNovedadesRecientes() {
    Response r = as(tokenFor("ADMIN")).get(DASHBOARD + "/novedades");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> novedades = r.jsonPath().getList("$");
    assertThat(novedades).as("body: %s", r.asString()).isNotNull();
    assertThat(novedades)
        .allSatisfy(
            novedad -> {
              assertThat(novedad.get("observerNombre")).as("observerNombre: %s", novedad).isNotNull();
              assertThat(((Number) novedad.get("minutosTranscurridos")).longValue())
                  .as("minutosTranscurridos: %s", novedad)
                  .isNotNegative();
            });
  }

  @Test
  @DisplayName("sin token, el dashboard devuelve 401")
  void dashboardSinTokenDevuelve401() {
    assertErrorShape(anon().get(DASHBOARD + "/resumen"), 401);
    assertErrorShape(anon().get(DASHBOARD + "/resultados"), 401);
    assertErrorShape(anon().get(DASHBOARD + "/progreso"), 401);
    assertErrorShape(anon().get(DASHBOARD + "/novedades"), 401);
  }

  @Test
  @DisplayName("un OBSERVER no entra al dashboard: 403")
  void observerNoEntraAlDashboard() {
    String token = tokenFor("OBSERVER");

    assertErrorShape(as(token).get(DASHBOARD + "/resumen"), 403);
    assertErrorShape(as(token).get(DASHBOARD + "/resultados"), 403);
    assertErrorShape(as(token).get(DASHBOARD + "/progreso"), 403);
    assertErrorShape(as(token).get(DASHBOARD + "/novedades"), 403);
  }
}
