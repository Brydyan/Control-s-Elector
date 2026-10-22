package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato transversal: la forma del error y la del spec OpenAPI.
 *
 * <p>El frontend consume el backend a través del interceptor de errores y del cliente HTTP, así que
 * la <b>forma</b> del error es parte del contrato, no un detalle de implementación. Si la fase
 * Quarkus cambia el shape, el frontend entero se rompe sin que ningún test de negocio lo note.
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>Todo 4xx responde {@code {timestamp, status, error, message}}, con {@code status} igual al
 *       status HTTP. Los errores de validación de bean agregan {@code fields}.
 *   <li>401 = no autenticado. 403 = permiso insuficiente. 400 = entrada inválida. 404 = recurso
 *       inexistente. 409 = conflicto de estado.
 *   <li>El spec OpenAPI se sirve en {@code /v3/api-docs} y declara las rutas de todos los módulos.
 * </ul>
 */
class ErroresContractIT extends ContractTestSupport {

  /** UUID de evidencia que no existe: el id de evidencia es un UUID publico, no un Long. */
  private static final String EVIDENCIA_INEXISTENTE = "00000000-0000-0000-0000-00000000dead";

  /** Spec OpenAPI del backend. */
  private static final String OPENAPI = "/v3/api-docs";

  @Test
  @DisplayName("el 401 tiene la forma estandar y su status coincide con el HTTP")
  void el401TieneLaFormaEstandar() {
    Response r = anon().get(OBSERVER + "/jrvs");

    assertErrorShape(r, 401);
    assertThat(r.jsonPath().getString("error")).isEqualTo("Unauthorized");
  }

  @Test
  @DisplayName("el 403 tiene la forma estandar")
  void el403TieneLaFormaEstandar() {
    Response r = as(tokenFor("OBSERVER")).get("/api/v1/dashboard/resumen");

    assertErrorShape(r, 403);
    assertThat(r.jsonPath().getString("error")).isEqualTo("Forbidden");
  }

  @Test
  @DisplayName("el 400 de validacion trae el detalle por campo")
  void el400DeValidacionTraeElDetallePorCampo() {
    Response r = anon().body(json(Map.of("email", "no-es-email", "password", "x")))
        .post("/api/v1/auth/login");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("error")).isEqualTo("Bad Request");
    assertThat(r.jsonPath().getString("message")).isEqualTo("validation failed");
    assertThat(r.jsonPath().getMap("fields")).isNotEmpty();
  }

  @Test
  @DisplayName("el 404 de un recurso inexistente tiene la forma estandar")
  void el404DeUnRecursoInexistenteTieneLaFormaEstandar() {
    Response r = as(tokenFor("MASTER")).get("/api/v1/evidencias/" + EVIDENCIA_INEXISTENTE);

    assertErrorShape(r, 404);
    assertThat(r.jsonPath().getString("error")).isEqualTo("Not Found");
  }

  @Test
  @DisplayName("el 409 de un par JRV+cargo ya registrado tiene la forma estandar")
  void el409DeUnParYaRegistradoTieneLaFormaEstandar() {
    String token = observerToken();
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response primero = as(token).body(json(body)).post(OBSERVER + "/actas");
    assertThat(primero.statusCode()).as("body: %s", primero.asString()).isEqualTo(201);
    registrarActaCreada(primero.jsonPath().getLong("id"), token, body);

    Response segundo = as(token).body(json(body)).post(OBSERVER + "/actas");

    assertErrorShape(segundo, 409);
    assertThat(segundo.jsonPath().getString("error")).isEqualTo("Conflict");
  }

  @Test
  @DisplayName("todos los 4xx comparten la misma forma: timestamp, status, error y message")
  void todosLos4xxCompartenLaMismaForma() {
    List<Response> errores =
        List.of(
            anon().get(OBSERVER + "/jrvs"),
            as(tokenFor("OBSERVER")).get("/api/v1/dashboard/resumen"),
            anon().body(json(Map.of())).post("/api/v1/auth/login"),
            as(tokenFor("MASTER")).get("/api/v1/evidencias/" + EVIDENCIA_INEXISTENTE));

    for (Response r : errores) {
      assertThat(r.jsonPath().getMap("$"))
          .as("el body de error tiene que traer las 4 claves base: %s", r.asString())
          .containsKeys("timestamp", "status", "error", "message");
      assertThat(r.jsonPath().getInt("status"))
          .as("el status del body tiene que coincidir con el status HTTP: %s", r.asString())
          .isEqualTo(r.statusCode());
    }
  }

  @Test
  @DisplayName("GET /v3/api-docs sirve el spec OpenAPI con las rutas de todos los modulos")
  void elSpecOpenApiDeclaraLasRutasDeTodosLosModulos() {
    // Se pide JSON explicitamente: el backend Spring siempre sirve JSON en esta ruta, y el
    // backend Quarkus negocia por el header Accept (sin el, sirve YAML). El contrato que
    // importa es que el spec declare las rutas, no el formato por defecto de la negociacion.
    Response r = anon().accept("application/json").get(OPENAPI);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getString("openapi")).startsWith("3.1");
    assertThat(r.jsonPath().getString("info.title")).isNotBlank();

    Map<String, Object> paths = r.jsonPath().getMap("paths");
    assertThat(paths).as("el spec tiene que declarar las rutas: %s", r.asString()).isNotEmpty();

    for (String ruta :
        List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/me",
            "/api/v1/menus/me",
            "/api/v1/observer/actas",
            "/api/v1/observer/mis-actas",
            "/api/v1/coordinator/actas",
            "/api/v1/coordinator/actas/{actaId}/disputar",
            "/api/v1/coordinator/actas/{actaId}/aprobar",
            "/api/v1/evidencias",
            "/api/v1/configuracion/partidos",
            "/api/v1/configuracion/cargos",
            "/api/v1/configuracion/candidatos",
            "/api/v1/configuracion/cantones",
            "/api/v1/dashboard/resumen",
            "/api/v1/admin/usuarios/{publicId}",
            "/api/v1/admin/sesiones/{sesionId}")) {
      assertThat(paths).as("el spec tiene que declarar la ruta %s", ruta).containsKey(ruta);
    }
  }

  @Test
  @DisplayName("el spec OpenAPI es publico: se sirve sin token")
  void elSpecOpenApiEsPublico() {
    Response r = anon().accept("application/json").get(OPENAPI);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
  }
}
