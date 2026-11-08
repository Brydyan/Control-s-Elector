package ec.twintailcs.electoral.shared.config;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comportamiento de CORS del backend.
 *
 * <p>Verifica el contrato que la configuracion de {@code application.properties} promete, no la
 * forma del archivo: el test arranca Quarkus de verdad y le pregunta al filtro HTTP de Vert.x.
 *
 * <p>Lo que se verifica:
 *
 * <ul>
 *   <li>un preflight desde un origen permitido responde 200 y devuelve {@code
 *       Access-Control-Allow-Origin} con el origen exacto (no comodin);
 *   <li>un preflight desde un origen NO permitido NO devuelve {@code Access-Control-Allow-Origin}:
 *       es la propiedad de seguridad que importa, porque el navegador bloquea la respuesta cuando
 *       esa cabecera falta;
 *   <li>la peticion real (no preflight) tambien lleva la cabecera de origen;
 *   <li>los metodos declarados cubren PATCH, que usa {@code EvidenciaResource}.
 * </ul>
 *
 * <p>Que el filtro de Vert.x ataje el preflight ANTES de JAX-RS es lo que hace que esto funcione:
 * {@code JwtAuthFilter} es {@code @PreMatching} y no exime {@code OPTIONS}, asi que si el preflight
 * llegara al contenedor JAX-RS responderia 401 y el navegador nunca veria las cabeceras CORS.
 */
@QuarkusTest
@TestProfile(CorsTestProfile.class)
class CorsPreflightIT {

  private static final String ORIGEN_PERMITIDO = "http://localhost:4200";
  private static final String ORIGEN_PERMITIDO_STAGING = "http://localhost:4300";
  private static final String ORIGEN_AUSENTE = "https://origen-no-autorizado.example";

  @Test
  @DisplayName("preflight de origen permitido responde 200 con el origen exacto y credenciales")
  void preflightDeOrigenPermitido() {
    Response res =
        given()
            .header("Origin", ORIGEN_PERMITIDO)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "authorization,content-type")
            .when()
            .options("/api/v1/auth/login");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGEN_PERMITIDO);
    assertThat(res.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    assertThat(res.getHeader("Access-Control-Allow-Methods")).contains("POST", "PATCH");
    assertThat(res.getHeader("Access-Control-Max-Age")).isEqualTo("3600");
  }

  @Test
  @DisplayName("los headers permitidos no incluyen X-Forwarded-For")
  void losHeadersPermitidosNoAbrenElSpoofingDeIp() {
    // X-Forwarded-For es un header de hop-by-hop que el navegador prohibe setear
    // desde JS. Listarlo como "allowed" no aporta nada al cliente legitimo y abre
    // la puerta a que un cliente origine ese valor. El backend lo recibe por
    // separado (nginx y el ingress lo ponen).
    Response res =
        given()
            .header("Origin", ORIGEN_PERMITIDO)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "authorization,content-type")
            .when()
            .options("/api/v1/auth/login");

    assertThat(res.getHeader("Access-Control-Allow-Headers"))
        .contains("Authorization", "Content-Type")
        .doesNotContain("X-Forwarded-For");
  }

  @Test
  @DisplayName("preflight de origen no permitido NO devuelve Access-Control-Allow-Origin")
  void preflightDeOrigenNoPermitido() {
    Response res =
        given()
            .header("Origin", ORIGEN_AUSENTE)
            .header("Access-Control-Request-Method", "POST")
            .when()
            .options("/api/v1/auth/login");

    assertThat(res.getHeader("Access-Control-Allow-Origin")).isNull();
  }

  @Test
  @DisplayName("la peticion real tambien lleva la cabecera de origen permitido")
  void peticionRealDeOrigenPermitido() {
    // /v3/api-docs es una ruta publica que no toca la base: el test de CORS no debe
    // depender de que haya un Postgres.
    Response res = given().header("Origin", ORIGEN_PERMITIDO).when().get("/v3/api-docs");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGEN_PERMITIDO);
  }

  @Test
  @DisplayName("una peticion real protegida y sin token conserva la cabecera de origen")
  void peticionRealProtegidaSinToken() {
    // El filtro CORS de Vert.x corre ANTES de JwtAuthFilter. Si el 401 llegara sin la
    // cabecera de origen, el navegador lo reporta como error de CORS y oculta el 401 real:
    // el usuario veria "fallo de red" en vez de "sesion expirada".
    Response res = given().header("Origin", ORIGEN_PERMITIDO).when().get("/api/v1/admin/users");

    assertThat(res.getStatusCode()).isEqualTo(401);
    assertThat(res.getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGEN_PERMITIDO);
  }

  @Test
  @DisplayName("la peticion real de un origen no permitido no lleva la cabecera de origen")
  void peticionRealDeOrigenNoPermitido() {
    Response res = given().header("Origin", ORIGEN_AUSENTE).when().get("/v3/api-docs");

    assertThat(res.getHeader("Access-Control-Allow-Origin")).isNull();
  }

  @Test
  @DisplayName("la lista de origenes acepta mas de un origen y distingue entre ellos")
  void listaDeOrigenes() {
    Response permitido =
        given()
            .header("Origin", ORIGEN_PERMITIDO_STAGING)
            .header("Access-Control-Request-Method", "GET")
            .when()
            .options("/api/v1/auth/login");
    Response ausente =
        given()
            .header("Origin", ORIGEN_AUSENTE)
            .header("Access-Control-Request-Method", "GET")
            .when()
            .options("/api/v1/auth/login");

    assertThat(permitido.getHeader("Access-Control-Allow-Origin"))
        .isEqualTo(ORIGEN_PERMITIDO_STAGING);
    assertThat(ausente.getHeader("Access-Control-Allow-Origin")).isNull();
  }
}
