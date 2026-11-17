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

    Map<String, Object> canton = cantones.get(0);
    assertThat(canton).containsKeys("id", "nombre", "codigo", "parroquias");
    assertThat((String) canton.get("nombre")).isNotBlank();

    List<Map<String, Object>> parroquias = (List<Map<String, Object>>) canton.get("parroquias");
    assertThat(parroquias).as("el canton tiene que traer parroquias: %s", canton).isNotEmpty();

    Map<String, Object> parroquia = parroquias.get(0);
    assertThat(parroquia).containsKeys("id", "nombre", "esUrbana", "jrvs");
    assertThat(parroquia.get("esUrbana")).isInstanceOf(Boolean.class);

    List<Map<String, Object>> jrvs = (List<Map<String, Object>>) parroquia.get("jrvs");
    assertThat(jrvs).as("la parroquia tiene que traer JRVs: %s", parroquia).isNotEmpty();

    Map<String, Object> jrv = jrvs.get(0);
    assertThat(jrv).containsKeys("id", "numero", "recinto", "totalElectores");
    assertThat((String) jrv.get("numero")).isNotBlank();
    assertThat(((Number) jrv.get("totalElectores")).intValue()).isPositive();
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
