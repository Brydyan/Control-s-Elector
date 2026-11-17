package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato de la configuracion geografica jerarquica (SDD 2026-11-16-config-geografica).
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code GET /api/v1/master/geografia} → 200 con el arbol provincia → canton → parroquia →
 *       JRV.
 *   <li>La lectura la pueden hacer MASTER y ADMIN ({@code configuracion:read}).
 *   <li>Sin token → 401. Con OBSERVER → 403.
 * </ul>
 */
class GeografiaContractIT extends ContractTestSupport {

  private static final String GEOGRAFIA = "/api/v1/master/geografia";

  @Test
  @DisplayName("GET /master/geografia devuelve el arbol con los cuatro niveles")
  @SuppressWarnings("unchecked")
  void getGeografiaDevuelveElArbol() {
    Response r = as(masterToken()).get(GEOGRAFIA);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);

    List<Map<String, Object>> provincias = r.jsonPath().getList("provincias");
    assertThat(provincias).as("body: %s", r.asString()).isNotEmpty();

    Map<String, Object> provincia = provincias.get(0);
    assertThat(provincia).containsKeys("id", "nombre", "codigo", "cantones");
    assertThat((String) provincia.get("nombre")).isNotBlank();

    List<Map<String, Object>> cantones = (List<Map<String, Object>>) provincia.get("cantones");
    assertThat(cantones).as("la provincia tiene que traer cantones: %s", provincia).isNotEmpty();

    // Un canton sin parroquias es legal (se acaba de crear y todavia no tiene ninguna), asi que
    // el test NO puede asumir que el primero de la lista las tiene: busca el primero que las
    // tenga y, en todos los casos, exige que la CLAVE exista.
    Map<String, Object> canton = null;
    for (Map<String, Object> candidato : cantones) {
      assertThat(candidato).containsKeys("id", "nombre", "codigo", "parroquias");
      assertThat((String) candidato.get("nombre")).isNotBlank();
      List<Map<String, Object>> parroquiasCandidato =
          (List<Map<String, Object>>) candidato.get("parroquias");
      assertThat(parroquiasCandidato).as("parroquias de %s", candidato).isNotNull();
      if (parroquiasCandidato != null && !parroquiasCandidato.isEmpty()) {
        canton = candidato;
        break;
      }
    }
    assertThat(canton).as("algun canton de la provincia tiene parroquias: %s", provincia).isNotNull();

    List<Map<String, Object>> parroquias = (List<Map<String, Object>>) canton.get("parroquias");

    // Igual que arriba: una parroquia sin JRVs es valida; el test busca la primera que tenga.
    Map<String, Object> parroquia = null;
    for (Map<String, Object> candidata : parroquias) {
      assertThat(candidata).containsKeys("id", "nombre", "esUrbana", "jrvs");
      assertThat(candidata.get("esUrbana")).isInstanceOf(Boolean.class);
      List<Map<String, Object>> jrvsCandidata = (List<Map<String, Object>>) candidata.get("jrvs");
      assertThat(jrvsCandidata).as("jrvs de %s", candidata).isNotNull();
      if (jrvsCandidata != null && !jrvsCandidata.isEmpty()) {
        parroquia = candidata;
        break;
      }
    }
    assertThat(parroquia).as("alguna parroquia tiene JRVs: %s", parroquias).isNotNull();

    List<Map<String, Object>> jrvs = (List<Map<String, Object>>) parroquia.get("jrvs");

    Map<String, Object> jrv = jrvs.get(0);
    assertThat(jrv).containsKeys("id", "numero", "recinto", "totalElectores");
    assertThat((String) jrv.get("numero")).isNotBlank();
    assertThat(((Number) jrv.get("totalElectores")).intValue()).isPositive();
  }

  @Test
  @DisplayName("el arbol de geografia y el desglose del dashboard cuentan las mismas parroquias")
  @SuppressWarnings("unchecked")
  void elArbolDeGeografiaCoincideConElDesgloseDelDashboard() {
    // Los dos endpoints leen la misma tabla con la misma jerarquia. Si uno de los dos cuelga una
    // parroquia del canton equivocado (un JOIN mal puesto), los dos tellos dejan de coincidir.
    // ADMIN puede leer los dos: `configuracion:read` para el arbol y el guard del dashboard.
    String token = tokenFor("ADMIN");

    Response arbol = as(token).get(GEOGRAFIA);
    Response progreso = as(token).get("/api/v1/dashboard/progreso");
    assertThat(arbol.statusCode()).as("body: %s", arbol.asString()).isEqualTo(200);
    assertThat(progreso.statusCode()).as("body: %s", progreso.asString()).isEqualTo(200);

    Map<Long, List<Long>> parroquiasDelArbol = new java.util.HashMap<>();
    for (Map<String, Object> provincia :
        arbol.jsonPath().<Map<String, Object>>getList("provincias")) {
      for (Map<String, Object> canton :
          (List<Map<String, Object>>) provincia.get("cantones")) {
        List<Long> ids = new java.util.ArrayList<>();
        for (Map<String, Object> parroquia :
            (List<Map<String, Object>>) canton.get("parroquias")) {
          ids.add(((Number) parroquia.get("id")).longValue());
        }
        ids.sort(Long::compareTo);
        parroquiasDelArbol.put(((Number) canton.get("id")).longValue(), ids);
      }
    }

    Map<Long, List<Long>> parroquiasDelDashboard = new java.util.HashMap<>();
    for (Map<String, Object> canton : progreso.jsonPath().<Map<String, Object>>getList("$")) {
      List<Long> ids = new java.util.ArrayList<>();
      for (Map<String, Object> parroquia :
          (List<Map<String, Object>>) canton.get("parroquias")) {
        ids.add(((Number) parroquia.get("parroquiaId")).longValue());
      }
      ids.sort(Long::compareTo);
      parroquiasDelDashboard.put(((Number) canton.get("cantonId")).longValue(), ids);
    }

    assertThat(parroquiasDelArbol)
        .as("el arbol y el dashboard tienen que tener los mismos cantones: %s vs %s", parroquiasDelArbol, parroquiasDelDashboard)
        .isNotEmpty();
    assertThat(parroquiasDelDashboard)
        .as("el dashboard tiene que traer el desglose: %s", progreso.asString())
        .isNotEmpty();

    for (Map.Entry<Long, List<Long>> entry : parroquiasDelArbol.entrySet()) {
      assertThat(parroquiasDelDashboard.get(entry.getKey()))
          .as("el canton %s tiene las mismas parroquias en el arbol y en el dashboard", entry.getKey())
          .containsExactlyElementsOf(entry.getValue());
    }
  }

  @Test
  @DisplayName("un ADMIN puede leer la geografia (configuracion:read)")
  void adminPuedeLeerLaGeografia() {
    Response r = as(tokenFor("ADMIN")).get(GEOGRAFIA);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getList("provincias")).isNotEmpty();
  }

  @Test
  @DisplayName("un OBSERVER no entra a la geografia: 403")
  void observerNoEntraALaGeografia() {
    Response r = as(tokenFor("OBSERVER")).get(GEOGRAFIA);

    assertErrorShape(r, 403);
  }

  @Test
  @DisplayName("sin token, la geografia responde 401")
  void sinTokenLaGeografiaResponde401() {
    Response r = anon().get(GEOGRAFIA);

    assertErrorShape(r, 401);
  }
}
