package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato de la configuración preelectoral: partidos, cargos, candidatos y cantones.
 *
 * <p>Es la parte del sistema que el veedor consulta para saber a quién puede votar, y la que el
 * MASTER usa para cargar la papeleta. Los listados son de lectura abierta ({@code
 * isAuthenticated()}); las escrituras exigen {@code configuracion:write}, que solo tiene MASTER.
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>Los cuatro listados devuelven 200 con la forma de su DTO.
 *   <li>Alta, edición y baja devuelven 201 / 200 / 204 con el permiso {@code
 *       configuracion:write}.
 *   <li>Un body inválido devuelve 400 con el detalle por campo.
 *   <li>Sin token → 401; con token sin permiso → 403.
 * </ul>
 */
class ConfiguracionContractIT extends ContractTestSupport {

  /** Partidos políticos. */
  private static final String PARTIDOS = "/api/v1/configuracion/partidos";

  /** Cargos electorales. */
  private static final String CARGOS = "/api/v1/configuracion/cargos";

  /** Candidatos inscritos. */
  private static final String CANDIDATOS = "/api/v1/configuracion/candidatos";

  /** Cantones de la provincia. */
  private static final String CANTONES = "/api/v1/configuracion/cantones";

  @Test
  @DisplayName("GET /configuracion/partidos devuelve la lista con la forma de PartidoResponse")
  void listarPartidosDevuelveLaLista() {
    Response r = as(tokenFor("OBSERVER")).get(PARTIDOS);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> partidos = r.jsonPath().getList("$");
    assertThat(partidos).as("el seed tiene partidos: %s", r.asString()).isNotEmpty();
    assertThat(partidos)
        .allSatisfy(
            partido -> {
              assertThat(partido.get("id")).as("id del partido: %s", partido).isNotNull();
              assertThat((String) partido.get("nombre")).as("nombre del partido: %s", partido).isNotBlank();
              assertThat((String) partido.get("siglas")).as("siglas del partido: %s", partido).isNotBlank();
              assertThat(partido.get("numeroLista")).as("numero de lista: %s", partido).isNotNull();
            });
  }

  @Test
  @DisplayName("GET /configuracion/cargos devuelve la lista con la forma de CargoResponse")
  void listarCargosDevuelveLaLista() {
    Response r = as(tokenFor("OBSERVER")).get(CARGOS);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> cargos = r.jsonPath().getList("$");
    assertThat(cargos).as("el seed tiene cargos: %s", r.asString()).isNotEmpty();
    assertThat(cargos).extracting(cargo -> cargo.get("nombre")).contains("Alcalde/sa");
    assertThat(cargos)
        .allSatisfy(
            cargo -> {
              assertThat(cargo.get("id")).as("id del cargo: %s", cargo).isNotNull();
              assertThat((String) cargo.get("nivel")).as("nivel del cargo: %s", cargo).isNotBlank();
              assertThat(((Number) cargo.get("puestos")).intValue())
                  .as("puestos del cargo: %s", cargo)
                  .isPositive();
            });
  }

  @Test
  @DisplayName("GET /configuracion/candidatos devuelve la lista con la forma de CandidatoResponse")
  void listarCandidatosDevuelveLaLista() {
    Response r = as(tokenFor("OBSERVER")).get(CANDIDATOS);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> candidatos = r.jsonPath().getList("$");
    assertThat(candidatos).as("el seed tiene candidatos: %s", r.asString()).isNotEmpty();
    assertThat(candidatos)
        .allSatisfy(
            candidato -> {
              assertThat(candidato.get("id")).as("id del candidato: %s", candidato).isNotNull();
              assertThat((String) candidato.get("nombre")).as("nombre: %s", candidato).isNotBlank();
              assertThat(candidato.get("orden")).as("orden: %s", candidato).isNotNull();
              assertThat(candidato.get("cargoId")).as("cargoId: %s", candidato).isNotNull();
              assertThat(candidato.get("partidoId")).as("partidoId: %s", candidato).isNotNull();
            });
  }

  @Test
  @DisplayName("GET /configuracion/candidatos filtra por cargoId")
  void listarCandidatosFiltraPorCargo() {
    String token = tokenFor("OBSERVER");

    // El cargo de la prueba sale de un candidato que YA existe, no del primer cargo de la
    // lista: el seed tiene cargos sin candidatos (Prefecto, de nivel PROVINCIA) y pedir el
    // primero hacia que el test fallara por como esta ordenado el seed, no por el filtro.
    List<Map<String, Object>> todos = listar(as(token).get(CANDIDATOS));
    assertThat(todos).as("el seed tiene candidatos: %s", todos).isNotEmpty();
    long cargoId = ((Number) todos.get(0).get("cargoId")).longValue();

    Response r = as(token).get(CANDIDATOS + "?cargoId=" + cargoId);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> candidatos = r.jsonPath().getList("$");
    assertThat(candidatos)
        .as("todos los candidatos devueltos tienen que ser del cargo pedido: %s", candidatos)
        .isNotEmpty()
        .allSatisfy(
            candidato ->
                assertThat(((Number) candidato.get("cargoId")).longValue()).isEqualTo(cargoId));
  }

  @Test
  @DisplayName("GET /configuracion/cantones devuelve la lista con la forma de CantonResponse")
  void listarCantonesDevuelveLaLista() {
    Response r = as(tokenFor("OBSERVER")).get(CANTONES);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> cantones = r.jsonPath().getList("$");
    assertThat(cantones).as("el seed tiene cantones: %s", r.asString()).isNotEmpty();
    assertThat(cantones)
        .allSatisfy(
            canton -> {
              assertThat(canton.get("id")).as("id del canton: %s", canton).isNotNull();
              assertThat((String) canton.get("nombre")).as("nombre del canton: %s", canton).isNotBlank();
              assertThat((String) canton.get("codigo")).as("codigo del canton: %s", canton).isNotBlank();
            });
  }

  @Test
  @DisplayName("el ciclo de alta, edicion y baja de un partido funciona y devuelve 201 / 200 / 204")
  void cicloDeVidaDeUnPartido() {
    String token = masterToken();
    String nombre = unique("Partido de prueba");
    String siglas = "P" + (int) (Math.random() * 900 + 100);

    Response alta =
        as(token)
            .body(json(Map.of("nombre", nombre, "siglas", siglas, "numeroLista", 99)))
            .post(PARTIDOS);
    assertThat(alta.statusCode()).as("body: %s", alta.asString()).isEqualTo(201);
    assertThat(alta.jsonPath().getLong("id")).isPositive();
    assertThat(alta.jsonPath().getString("nombre")).isEqualTo(nombre);
    assertThat(alta.jsonPath().getString("siglas")).isEqualTo(siglas);
    assertThat(alta.jsonPath().getInt("numeroLista")).isEqualTo(99);
    long id = alta.jsonPath().getLong("id");

    Response edicion =
        as(token).body(json(Map.of("nombre", nombre + " editado", "siglas", siglas, "numeroLista", 98)))
            .put(PARTIDOS + "/" + id);
    assertThat(edicion.statusCode()).as("body: %s", edicion.asString()).isEqualTo(200);
    assertThat(edicion.jsonPath().getString("nombre")).isEqualTo(nombre + " editado");

    Response baja = as(token).delete(PARTIDOS + "/" + id);
    assertThat(baja.statusCode()).as("body: %s", baja.asString()).isEqualTo(204);

    Response listado = as(token).get(PARTIDOS);
    assertThat(listar(listado))
        .as("el partido dado de baja no puede seguir en el listado")
        .noneMatch(partido -> ((Number) partido.get("id")).longValue() == id);
  }

  @Test
  @DisplayName("el alta de un partido con body inválido devuelve 400 con los campos que fallaron")
  void altaDePartidoConBodyInvalidoDevuelve400() {
    Response r = as(masterToken()).body(json(Map.of())).post(PARTIDOS);

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).isEqualTo("validation failed");
    assertThat(r.jsonPath().getMap("fields"))
        .containsKeys("nombre", "siglas", "numeroLista");
  }

  @Test
  @DisplayName("sin token, los listados de configuración devuelven 401")
  void listadosSinTokenDevuelven401() {
    assertErrorShape(anon().get(PARTIDOS), 401);
    assertErrorShape(anon().get(CARGOS), 401);
    assertErrorShape(anon().get(CANDIDATOS), 401);
    assertErrorShape(anon().get(CANTONES), 401);
  }

  @Test
  @DisplayName("sin configuracion:write, las escrituras de configuración devuelven 403")
  void escriturasSinPermisoDevuelven403() {
    String token = tokenFor("OBSERVER");

    Response alta =
        as(token)
            .body(json(Map.of("nombre", unique("No debe entrar"), "siglas", "XXX", "numeroLista", 1)))
            .post(PARTIDOS);
    assertErrorShape(alta, 403);

    Response edicion =
        as(token).body(json(Map.of("nombre", "x", "siglas", "X", "numeroLista", 1))).put(PARTIDOS + "/1");
    assertErrorShape(edicion, 403);

    Response baja = as(token).delete(PARTIDOS + "/1");
    assertErrorShape(baja, 403);
  }
}
