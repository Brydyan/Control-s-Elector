package ec.twintailcs.electoral.common.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.shared.testing.ClientIpHeaders;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rate limiting de punta a punta: anotaciones, filtro global, login y resolucion de IP.
 *
 * <p>Cada test usa usuarios e IPs propios para no compartir contadores con los demas.
 */
@QuarkusTest
@TestProfile(RateLimitingTestProfile.class)
@DisplayName("Rate limiting en endpoints")
class RateLimitingIT {

  private static final String PASSWORD = "clave-de-prueba-123";
  private static final String WRONG = "clave-incorrecta-999";
  private static final AtomicInteger IP_SEQ = new AtomicInteger();

  @Inject PasswordHasher hasher;
  @Inject JwtService jwt;

  // ---------------------------------------------------------------------
  // Login
  // ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "login: tras 5 fallos desde una IP, el 6.º intento es 429 aunque la clave sea correcta")
  void loginBloqueaTrasCincoFallos() {
    String email = crearUsuario("OBSERVER");
    String ip = ip();
    for (int i = 0; i < 5; i++) {
      login(email, WRONG, ip).then().statusCode(401);
    }

    Response sexto = login(email, PASSWORD, ip);

    assertThat(sexto.statusCode()).isEqualTo(429);
    assertThat(sexto.getHeader("Retry-After")).isNotNull();
    assertThat(sexto.getHeader("RateLimit-Limit")).isEqualTo("5");
  }

  @Test
  @DisplayName(
      "login: un atacante que agota los intentos desde su IP no bloquea al observador real")
  void atacanteNoBloqueaAlObservador() {
    String email = crearUsuario("OBSERVER");
    String ipAtacante = ip();
    for (int i = 0; i < 6; i++) {
      login(email, WRONG, ipAtacante);
    }

    login(email, PASSWORD, ip()).then().statusCode(200);
  }

  @Test
  @DisplayName("login: 10 observadores detras del mismo NAT no se bloquean entre si")
  void recintoConNatNoSeBloquea() {
    String ipRecinto = ip();
    for (int i = 0; i < 10; i++) {
      String email = crearUsuario("OBSERVER");
      login(email, WRONG, ipRecinto).then().statusCode(401);
      login(email, PASSWORD, ipRecinto).then().statusCode(200);
    }
  }

  @Test
  @DisplayName("login exitoso no consume intentos fallidos")
  void loginExitosoNoConsume() {
    String email = crearUsuario("OBSERVER");
    String ip = ip();
    for (int i = 0; i < 8; i++) {
      login(email, PASSWORD, ip).then().statusCode(200);
    }
  }

  // ---------------------------------------------------------------------
  // IP del cliente
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("la sesion guarda la IP de CF-Connecting-IP cuando trae el secreto de origen")
  void sesionConIpConfiable() {
    String email = crearUsuario("OBSERVER");
    String ip = ip();

    String sesionId = login(email, PASSWORD, ip).then().statusCode(200).extract().path("sesionId");

    assertThat(ipDeSesion(sesionId)).isEqualTo(ip);
  }

  @Test
  @DisplayName("un X-Forwarded-For inventado por el cliente se ignora")
  void xForwardedForFalsoSeIgnora() {
    String email = crearUsuario("OBSERVER");

    String sesionId =
        given()
            .contentType("application/json")
            .header("X-Forwarded-For", "6.6.6.6")
            .body(loginBody(email, PASSWORD))
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("sesionId");

    assertThat(ipDeSesion(sesionId)).isNotEqualTo("6.6.6.6").isNotEqualTo("sistema");
  }

  @Test
  @DisplayName("CF-Connecting-IP sin el secreto correcto no se cree")
  void cfConnectingIpSinSecretoSeIgnora() {
    String email = crearUsuario("OBSERVER");

    String sesionId =
        given()
            .contentType("application/json")
            .header("X-Origin-Auth", "secreto-equivocado")
            .header("CF-Connecting-IP", "7.7.7.7")
            .body(loginBody(email, PASSWORD))
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("sesionId");

    assertThat(ipDeSesion(sesionId)).isNotEqualTo("7.7.7.7");
  }

  // ---------------------------------------------------------------------
  // Reautenticacion
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("update-personal-email: el 6.º intento en 15 min es 429 (frena adivinar la clave)")
  void reauthLimitado() {
    String email = crearUsuario("OBSERVER");
    String token = token(email, "OBSERVER", List.of());
    for (int i = 0; i < 5; i++) {
      int status = updateEmail(token, WRONG).statusCode();
      assertThat(status).isNotEqualTo(429);
    }

    Response sexto = updateEmail(token, PASSWORD);

    assertThat(sexto.statusCode()).isEqualTo(429);
    assertThat(sexto.getHeader("Retry-After")).isNotNull();
  }

  // ---------------------------------------------------------------------
  // Endpoints de la parte D
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("GET /observer/jrvs: exige acta:read")
  void jrvsExigePermiso() {
    String email = crearUsuario("OBSERVER");

    given()
        .header("Authorization", "Bearer " + token(email, "OBSERVER", List.of()))
        .get("/api/v1/observer/jrvs")
        .then()
        .statusCode(403);
    given()
        .header("Authorization", "Bearer " + token(email, "OBSERVER", List.of("acta:read")))
        .get("/api/v1/observer/jrvs")
        .then()
        .statusCode(200);
  }

  @Test
  @DisplayName("GET /observer/jrvs: el coordinador sigue pudiendo consultarlo")
  void coordinadorConsultaJrvs() {
    String email = crearUsuario("COORDINATOR");

    given()
        .header("Authorization", "Bearer " + token(email, "COORDINATOR", List.of("acta:read")))
        .get("/api/v1/observer/jrvs")
        .then()
        .statusCode(200);
  }

  @Test
  @DisplayName("GET /observer/jrvs: la consulta 61 en un minuto es 429")
  void jrvsLimitado() {
    String email = crearUsuario("OBSERVER");
    String token = token(email, "OBSERVER", List.of("acta:read"));
    for (int i = 0; i < 60; i++) {
      given()
          .header("Authorization", "Bearer " + token)
          .get("/api/v1/observer/jrvs")
          .then()
          .statusCode(200);
    }

    given()
        .header("Authorization", "Bearer " + token)
        .get("/api/v1/observer/jrvs")
        .then()
        .statusCode(429)
        .header("Retry-After", org.hamcrest.Matchers.notNullValue());
  }

  @Test
  @DisplayName("GET /configuracion/cargos: la consulta 121 en un minuto es 429")
  void configReadLimitado() {
    String email = crearUsuario("OBSERVER");
    String token = token(email, "OBSERVER", List.of());
    for (int i = 0; i < 120; i++) {
      given()
          .header("Authorization", "Bearer " + token)
          .get("/api/v1/configuracion/cargos")
          .then()
          .statusCode(200);
    }

    given()
        .header("Authorization", "Bearer " + token)
        .get("/api/v1/configuracion/cargos")
        .then()
        .statusCode(429);
  }

  @Test
  @DisplayName("user-global: corta a un cliente en bucle en cualquier endpoint autenticado")
  void userGlobalCortaBucle() {
    String email = crearUsuario("OBSERVER");
    String token = token(email, "OBSERVER", List.of());
    for (int i = 0; i < RateLimitingTestProfile.USER_GLOBAL_LIMIT; i++) {
      given()
          .header("Authorization", "Bearer " + token)
          .get("/api/v1/user/profile")
          .then()
          .statusCode(200);
    }

    given()
        .header("Authorization", "Bearer " + token)
        .get("/api/v1/user/profile")
        .then()
        .statusCode(429);
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  /** IP unica por llamada: los tests no comparten contadores por IP. */
  private static String ip() {
    // 2001:db8::/32 es el rango de documentacion de IPv6: nunca es una IP real.
    return "2001:db8::" + Integer.toHexString(IP_SEQ.incrementAndGet());
  }

  private static String loginBody(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }

  private static Response login(String email, String password, String ip) {
    return given()
        .contentType("application/json")
        .headers(ClientIpHeaders.from(ip))
        .body(loginBody(email, password))
        .post("/api/v1/auth/login");
  }

  private static Response updateEmail(String token, String password) {
    return given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body("{\"emailPersonal\":\"nuevo@test.local\",\"password\":\"" + password + "\"}")
        .put("/api/v1/user/update-personal-email");
  }

  private String crearUsuario(String rolNombre) {
    String email = "rl-" + UUID.randomUUID() + "@test.local";
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              Usuario u = new Usuario();
              u.setEmail(email);
              u.setNombre("Rate");
              u.setApellido("Limit");
              u.setPasswordHash(hasher.hash(PASSWORD));
              u.setRol(Rol.find("nombre", rolNombre).firstResult());
              u.setHabilitado(true);
              u.persist();
            });
    return email;
  }

  private String token(String email, String rol, List<String> perms) {
    Usuario u =
        QuarkusTransaction.requiringNew().call(() -> Usuario.find("email", email).firstResult());
    return jwt.issue(u.getPublicId(), email, rol, perms, UUID.randomUUID().toString()).access();
  }

  private static String ipDeSesion(String sesionId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Sesion s = Sesion.find("sesionId", sesionId).firstResult();
              return s == null ? null : s.getIpCliente();
            });
  }
}
