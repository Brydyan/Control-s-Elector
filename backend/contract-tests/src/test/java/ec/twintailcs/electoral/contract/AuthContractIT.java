package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato de autenticación: login, perfil, refresh con rotación, logout y registro.
 *
 * <p>Estos endpoints son la bisagra del sistema: si la fase 2 de la migración (#113) altera la
 * forma de la respuesta o la semántica de la rotación del refresh token, toda la cadena de
 * autenticación del frontend queda rota sin que ningún test unitario lo note.
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>login válido → 200 con {@code accessToken}, {@code tokenType=Bearer}, {@code
 *       expiresInSeconds}, {@code sesionId} y {@code usuario{id,email,rol}}; además cookie {@code
 *       refresh_token} HttpOnly + Secure + SameSite=Strict.
 *   <li>login con credenciales inválidas → 401. login con body inválido → 400 con {@code fields}.
 *   <li>GET /me devuelve el mismo usuario que el login, y 401 sin token.
 *   <li>refresh rota el token: el refresh viejo queda invalidado y devuelve 401 al reusarse.
 *   <li>logout revoca la sesión y borra la cookie.
 *   <li>register exige MASTER (o el permiso {@code usuario:create}) y devuelve 201.
 * </ul>
 */
class AuthContractIT extends ContractTestSupport {

  @Test
  @DisplayName("login válido devuelve el contrato completo de AuthResponse y la cookie refresh")
  void loginValidoDevuelveTokensYUsuario() {
    String email = registerUser("OBSERVER");

    Response r = anon().body(json(Map.of("email", email, "password", TEST_PASSWORD)))
        .post("/api/v1/auth/login");

    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.jsonPath().getString("accessToken")).isNotBlank();
    assertThat(r.jsonPath().getString("tokenType")).isEqualTo("Bearer");
    assertThat(r.jsonPath().getLong("expiresInSeconds")).isPositive();
    assertThat(r.jsonPath().getString("sesionId")).isNotBlank();
    assertThat(r.jsonPath().getString("usuario.email")).isEqualTo(email);
    assertThat(r.jsonPath().getString("usuario.rol")).isEqualTo("OBSERVER");
    assertThat(r.jsonPath().getString("usuario.id")).isNotBlank();

    String setCookie = r.getHeader("Set-Cookie");
    assertThat(setCookie)
        .as("la cookie refresh_token debe salir en el Set-Cookie")
        .contains("refresh_token=")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict");
  }

  @Test
  @DisplayName("login con credenciales inválidas devuelve 401 con el shape estándar de error")
  void loginConCredencialesInvalidasDevuelve401() {
    Response r =
        anon()
            .body(json(Map.of("email", "admsismas@control.ttcs", "password", "no-es-la-clave")))
            .post("/api/v1/auth/login");

    assertErrorShape(r, 401);
    assertThat(r.jsonPath().getString("message")).isEqualTo("Credenciales invalidas");
  }

  @Test
  @DisplayName("login con body inválido devuelve 400 y detalla los campos que fallaron")
  void loginConBodyInvalidoDevuelve400ConFields() {
    Response r =
        anon().body(json(Map.of("email", "no-es-un-email", "password", "corta")))
            .post("/api/v1/auth/login");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).isEqualTo("validation failed");
    assertThat(r.jsonPath().getMap("fields")).containsKeys("email", "password");
  }

  @Test
  @DisplayName("GET /me devuelve el usuario del token y 401 sin token")
  void meDevuelveElUsuarioDelToken() {
    String email = registerUser("COORDINATOR");
    String token = login(email, TEST_PASSWORD);

    Response conToken = as(token).get("/api/v1/auth/me");

    assertThat(conToken.statusCode()).isEqualTo(200);
    assertThat(conToken.jsonPath().getString("email")).isEqualTo(email);
    assertThat(conToken.jsonPath().getString("rol")).isEqualTo("COORDINATOR");
    assertThat(conToken.jsonPath().getString("id")).isNotBlank();

    Response sinToken = anon().get("/api/v1/auth/me");

    assertErrorShape(sinToken, 401);
  }

  @Test
  @DisplayName("refresh con cookie renueva los tokens y rota el refresh token")
  void refreshRotaElRefreshToken() {
    String email = registerUser("OBSERVER");
    Response login =
        anon().body(json(Map.of("email", email, "password", TEST_PASSWORD)))
            .post("/api/v1/auth/login");
    String refreshViejo = login.getCookie("refresh_token");
    assertThat(refreshViejo).isNotBlank();

    Response refresco = anon().cookie("refresh_token", refreshViejo).post("/api/v1/auth/refresh");

    assertThat(refresco.statusCode()).isEqualTo(200);
    assertThat(refresco.jsonPath().getString("accessToken")).isNotBlank();
    assertThat(refresco.jsonPath().getString("usuario.email")).isEqualTo(email);

    String refreshNuevo = refresco.getCookie("refresh_token");
    assertThat(refreshNuevo).isNotBlank().isNotEqualTo(refreshViejo);
  }

  @Test
  @DisplayName("el refresh token rotado no se puede reusar: el viejo devuelve 401")
  void refreshTokenViejoNoSePuedeReusar() {
    String email = registerUser("OBSERVER");
    Response login =
        anon().body(json(Map.of("email", email, "password", TEST_PASSWORD)))
            .post("/api/v1/auth/login");
    String refreshViejo = login.getCookie("refresh_token");

    Response refresco = anon().cookie("refresh_token", refreshViejo).post("/api/v1/auth/refresh");
    assertThat(refresco.statusCode()).isEqualTo(200);

    Response reuso = anon().cookie("refresh_token", refreshViejo).post("/api/v1/auth/refresh");

    assertErrorShape(reuso, 401);
  }

  @Test
  @DisplayName("refresh sin token devuelve 401")
  void refreshSinTokenDevuelve401() {
    Response r = anon().post("/api/v1/auth/refresh");

    assertErrorShape(r, 401);
    assertThat(r.jsonPath().getString("message")).isEqualTo("No se encontro refresh token");
  }

  @Test
  @DisplayName("logout devuelve 204, borra la cookie y revoca la sesión")
  void logoutRevocaLaSesion() {
    String email = registerUser("OBSERVER");
    Response login =
        anon().body(json(Map.of("email", email, "password", TEST_PASSWORD)))
            .post("/api/v1/auth/login");
    String refresh = login.getCookie("refresh_token");

    Response logout = anon().cookie("refresh_token", refresh).post("/api/v1/auth/logout");

    assertThat(logout.statusCode()).isEqualTo(204);
    assertThat(logout.getHeader("Set-Cookie")).contains("refresh_token=").contains("Max-Age=0");

    Response refrescoTrasLogout = anon().cookie("refresh_token", refresh).post("/api/v1/auth/refresh");
    assertErrorShape(refrescoTrasLogout, 401);
  }

  @Test
  @DisplayName("register exige MASTER: un OBSERVER recibe 403")
  void registerExigePermisosElevados() {
    String token = tokenFor("OBSERVER");

    Response r =
        as(token)
            .body(
                json(
                    Map.of(
                        "nombre", "Cta",
                        "apellido", "Rgn",
                        "password", TEST_PASSWORD,
                        "rol", "COORDINATOR")))
            .post("/api/v1/auth/register");

    assertErrorShape(r, 403);
  }

  @Test
  @DisplayName("register con rol inválido devuelve 400")
  void registerConRolInvalidoDevuelve400() {
    Response r =
        as(masterToken())
            .body(
                json(
                    Map.of(
                        "nombre", "Cta",
                        "apellido", "Rgn",
                        "password", TEST_PASSWORD,
                        "rol", "SUPERADMIN")))
            .post("/api/v1/auth/register");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).isEqualTo("validation failed");
  }

  @Test
  @DisplayName("la validación del body corre antes que la autorización: body inválido da 400, no 403")
  void validacionCorreAntesQueAutorizacion() {
    String token = tokenFor("OBSERVER");

    Response r =
        as(token)
            .body(
                json(
                    Map.of(
                        "nombre", "C",
                        "apellido", "T",
                        "password", TEST_PASSWORD,
                        "rol", "COORDINATOR")))
            .post("/api/v1/auth/register");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).isEqualTo("validation failed");
  }
}
