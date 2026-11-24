package ec.twintailcs.electoral.legal.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.shared.testing.ClientIpHeaders;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flujo legal completo contra una base real (T-LEGAL-01.3, .4 y .10).
 *
 * <p>Estos son los tests que la suite de regresion por contrato no puede sustituir: hablan HTTP,
 * pero escriben en tablas con RLS forzada y dependen de la migracion de Liquibase. Si el changelog
 * y las entidades no coinciden, Hibernate no arranca; si las politicas RLS estan mal, el INSERT de
 * la aceptacion revienta y el limite de exportacion no ve la exportacion anterior.
 *
 * <p>Cada test crea su propio usuario OBSERVER. No es superflúo: el limite de exportacion vive en
 * la base y dura 24 horas, asi que un usuario compartido haría que el primer export pasara solo en
 * la primera corrida.
 *
 * <p>Las IPs del formulario del DPO se fijan por test con {@link ClientIpHeaders}: el limitador
 * cuenta por IP, y sin separarlos los tests se pisan entre si.
 */
@QuarkusTest
@TestProfile(LegalFlowTestProfile.class)
class LegalFlowIT {

  private static final String PASSWORD = "clave-de-prueba-123";

  @Inject PasswordHasher hasher;
  @Inject JwtService jwt;
  @Inject EntityManager em;

  private String email;
  private String token;
  private long usuarioId;

  @BeforeEach
  void crearUsuarioDePrueba() {
    email = "legal-" + UUID.randomUUID() + "@test.local";

    Usuario creado =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Rol rol = Rol.find("nombre", "OBSERVER").firstResult();
                  Usuario u = new Usuario();
                  u.setEmail(email);
                  u.setNombre("Legal");
                  u.setApellido("Prueba");
                  u.setPasswordHash(hasher.hash(PASSWORD));
                  u.setRol(rol);
                  u.setHabilitado(true);
                  u.persist();
                  return u;
                });

    usuarioId = creado.getId();
    token =
        jwt.issue(creado.getPublicId(), email, "OBSERVER", List.of(), UUID.randomUUID().toString())
            .access();
  }

  // -------------------------------------------------------------------------
  // Aceptacion de terminos
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("aceptar terminos registra la version en el usuario y en la auditoria")
  void aceptarTerminosRegistraLaVersionEnElUsuarioYEnLaAuditoria() {
    Response res =
        given()
            .header("Authorization", "Bearer " + token)
            .headers(ClientIpHeaders.from("198.51.100.10"))
            .when()
            .post("/api/v1/user/accept-terms")
            .then()
            .statusCode(200)
            .extract()
            .response();

    assertThat(res.jsonPath().getString("status")).isEqualTo("accepted");
    assertThat(res.jsonPath().getString("version")).isEqualTo("1.0");

    String versionEnBase =
        enContextoDe(
            usuarioId,
            em ->
                (String)
                    em.createNativeQuery("select accepted_terms_version from usuario where id = ?1")
                        .setParameter(1, usuarioId)
                        .getSingleResult());
    assertThat(versionEnBase).isEqualTo("1.0");

    int aceptaciones =
        enContextoDe(
            usuarioId,
            em ->
                ((Number)
                        em.createNativeQuery(
                                "select count(*) from terms_acceptance_audit where usuario_id = ?1")
                            .setParameter(1, usuarioId)
                            .getSingleResult())
                    .intValue());
    assertThat(aceptaciones).as("una fila de auditoria por aceptacion").isEqualTo(1);
  }

  @Test
  @DisplayName("la segunda aceptacion se registra como re-aceptacion, no como inicial")
  void laSegundaAceptacionSeRegistraComoReaceptacion() {
    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .post("/api/v1/user/accept-terms")
        .then()
        .statusCode(200);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .post("/api/v1/user/accept-terms")
        .then()
        .statusCode(200);

    List<String> tipos =
        enContextoDe(
            usuarioId,
            em ->
                em.createNativeQuery(
                        "select consent_type from terms_acceptance_audit where usuario_id = ?1 order by id")
                    .setParameter(1, usuarioId)
                    .getResultList());

    assertThat(tipos).containsExactly("initial", "update");
  }

  @Test
  @DisplayName("aceptar terminos sin token responde 401")
  void aceptarTerminosSinTokenResponde401() {
    given().when().post("/api/v1/user/accept-terms").then().statusCode(401);
  }

  // -------------------------------------------------------------------------
  // Exportacion de datos (GDPR Art. 15)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("la exportacion devuelve los datos propios y queda registrada")
  void laExportacionDevuelveLosDatosPropiosYQuedaRegistrada() {
    Response res =
        given()
            .header("Authorization", "Bearer " + token)
            .headers(ClientIpHeaders.from("198.51.100.11"))
            .when()
            .get("/api/v1/user/export-data")
            .then()
            .statusCode(200)
            .extract()
            .response();

    assertThat(res.jsonPath().getString("export_request_id")).isNotBlank();
    assertThat(res.jsonPath().getString("user.email")).isEqualTo(email);
    assertThat(res.jsonPath().getList("actas_registradas")).isNotNull();
    assertThat(res.jsonPath().getList("dispute_submissions")).isNotNull();

    int exportaciones =
        enContextoDe(
            usuarioId,
            em ->
                ((Number)
                        em.createNativeQuery(
                                "select count(*) from user_export_log where usuario_id = ?1")
                            .setParameter(1, usuarioId)
                            .getSingleResult())
                    .intValue());
    assertThat(exportaciones).as("la exportacion queda auditada").isEqualTo(1);
  }

  @Test
  @DisplayName("la segunda exportacion dentro de 24h responde 429 con Retry-After")
  void laSegundaExportacionDentroDe24hResponde429() {
    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/api/v1/user/export-data")
        .then()
        .statusCode(200);

    Response segunda =
        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/v1/user/export-data")
            .then()
            .statusCode(429)
            .extract()
            .response();

    assertThat(segunda.getHeader("Retry-After"))
        .as("el cliente necesita saber cuando puede reintentar")
        .isNotNull();
    assertThat(Integer.parseInt(segunda.getHeader("Retry-After"))).isBetween(1, 24 * 3600);
  }

  @Test
  @DisplayName("el limite de exportacion es por usuario y no por IP")
  void elLimiteDeExportacionEsPorUsuario() {
    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/api/v1/user/export-data")
        .then()
        .statusCode(200);

    given()
        .header("Authorization", "Bearer " + token)
        .headers(ClientIpHeaders.from("198.51.100.99"))
        .when()
        .get("/api/v1/user/export-data")
        .then()
        .statusCode(429);
  }

  @Test
  @DisplayName("exportar datos sin token responde 401")
  void exportarDatosSinTokenResponde401() {
    given().when().get("/api/v1/user/export-data").then().statusCode(401);
  }

  // -------------------------------------------------------------------------
  // Contacto con el DPO
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("la solicitud al DPO se guarda con la IP del cliente")
  void laSolicitudAlDpoSeGuardaConLaIpDelCliente() {
    given()
        .body(
            """
            {"name":"Juan García","email":"juan@example.com","subject":"Ejercer derecho al olvido","message":"Quiero borrar mi cuenta y todos mis datos."}
            """)
        .contentType("application/json")
        .headers(ClientIpHeaders.from("203.0.113.1"))
        .when()
        .post("/api/v1/legal/contact-dpo")
        .then()
        .statusCode(200)
        .body("status", org.hamcrest.Matchers.equalTo("received"))
        .body("message", org.hamcrest.Matchers.containsString("DPO"));

    List<Object[]> filas =
        leerComoMaster(
            "select name, email, subject, status, ip_address from dpo_contact_requests order by id desc limit 1");

    assertThat(filas).hasSize(1);
    assertThat(filas.get(0)[0]).isEqualTo("Juan García");
    assertThat(filas.get(0)[1]).isEqualTo("juan@example.com");
    assertThat(filas.get(0)[2]).isEqualTo("Ejercer derecho al olvido");
    assertThat(filas.get(0)[3]).isEqualTo("NEW");
    assertThat(filas.get(0)[4]).isEqualTo("203.0.113.1");
  }

  @Test
  @DisplayName("la segunda solicitud de la misma IP dentro de 30s responde 429")
  void laSegundaSolicitudDeLaMismaIpResponde429() {
    given()
        .body(
            """
            {"name":"Ana","email":"ana@example.com","subject":"Consulta","message":"Mensaje de prueba."}
            """)
        .contentType("application/json")
        .headers(ClientIpHeaders.from("203.0.113.2"))
        .when()
        .post("/api/v1/legal/contact-dpo")
        .then()
        .statusCode(200);

    Response segunda =
        given()
            .body(
                """
                {"name":"Ana","email":"ana@example.com","subject":"Consulta","message":"Mensaje de prueba."}
                """)
            .contentType("application/json")
            .headers(ClientIpHeaders.from("203.0.113.2"))
            .when()
            .post("/api/v1/legal/contact-dpo")
            .then()
            .statusCode(429)
            .extract()
            .response();

    assertThat(segunda.getHeader("Retry-After")).isNotNull();
  }

  @Test
  @DisplayName("otra IP puede enviar su solicitud aunque otra este limitada")
  void otraIpPuedeEnviarSuSolicitud() {
    given()
        .body(
            """
            {"name":"Luis","email":"luis@example.com","subject":"Consulta","message":"Mensaje de prueba."}
            """)
        .contentType("application/json")
        .headers(ClientIpHeaders.from("203.0.113.3"))
        .when()
        .post("/api/v1/legal/contact-dpo")
        .then()
        .statusCode(200);

    given()
        .body(
            """
            {"name":"Luis","email":"luis@example.com","subject":"Consulta","message":"Mensaje de prueba."}
            """)
        .contentType("application/json")
        .headers(ClientIpHeaders.from("203.0.113.4"))
        .when()
        .post("/api/v1/legal/contact-dpo")
        .then()
        .statusCode(200);
  }

  @Test
  @DisplayName("un email invalido se rechaza con 400 y no se guarda nada")
  void unEmailInvalidoSeRechazaCon400() {
    given()
        .body(
            """
            {"name":"","email":"no-es-un-email","subject":"","message":""}
            """)
        .contentType("application/json")
        .headers(ClientIpHeaders.from("203.0.113.5"))
        .when()
        .post("/api/v1/legal/contact-dpo")
        .then()
        .statusCode(400);

    long guardadas =
        contarComoMaster(
            "select count(*) from dpo_contact_requests where ip_address = '203.0.113.5'");
    assertThat(guardadas).as("una solicitud invalida no se guarda").isZero();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Consulta con el contexto de RLS del usuario.
   *
   * <p>Las tablas del bloque legal tienen RLS forzada: sin {@code app.current_user_id} la consulta
   * no ve nada. El contexto se fija con {@code set_config(..., true)}, que es local a la
   * transaccion.
   */
  private <T> T enContextoDe(long usuarioContexto, Function<EntityManager, T> accion) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              em.createNativeQuery("select set_config('app.current_user_id', :id, true)")
                  .setParameter("id", String.valueOf(usuarioContexto))
                  .getSingleResult();
              return accion.apply(em);
            });
  }

  /**
   * Consulta como MASTER.
   *
   * <p>Las solicitudes al DPO solo las lee el equipo legal: la politica de RLS limita el SELECT a
   * {@code MASTER}. El test usa esa misma via para comprobar que la fila se guardo.
   */
  private List<Object[]> leerComoMaster(String sql) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              em.createNativeQuery("select set_config('app.current_user_id', :id, true)")
                  .setParameter("id", String.valueOf(idMaster()))
                  .getSingleResult();
              return em.createNativeQuery(sql).getResultList();
            });
  }

  /** Cuenta filas de una tabla del bloque legal con permisos de MASTER. */
  private long contarComoMaster(String sql) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              em.createNativeQuery("select set_config('app.current_user_id', :id, true)")
                  .setParameter("id", String.valueOf(idMaster()))
                  .getSingleResult();
              return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
            });
  }

  /** Id del usuario MASTER sembrado: es quien puede leer las solicitudes del DPO. */
  private Long idMaster() {
    return (Long)
        em.createNativeQuery(
                "select u.id from usuario u join rol r on u.rol_id = r.id where r.nombre = 'MASTER' limit 1")
            .getSingleResult();
  }
}
