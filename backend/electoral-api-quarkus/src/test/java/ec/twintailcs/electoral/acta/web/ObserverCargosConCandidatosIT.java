package ec.twintailcs.electoral.acta.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Usuario;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-PERF-OPT-01 B.2: catalogo batch de una JRV.
 *
 * <p>Verifica lo que el SDD promete del endpoint:
 *
 * <ul>
 *   <li>devuelve el mismo conjunto de cargos que el endpoint suelto, en una sola peticion;
 *   <li>cada cargo trae sus candidatos y la lista de partidos;
 *   <li>sin token responde 401 y sin {@code jrvId} responde 400;
 *   <li>una JRV sin cargos devuelve la lista vacia, no un error.
 * </ul>
 *
 * <p>Necesita un Postgres real (el perfil levanta uno con Testcontainers o se conecta al que diga
 * {@code -Dtest.postgres.url=...}), porque el endpoint lee {@code jrv_cargo} y las tablas de
 * configuracion preelectoral.
 */
@QuarkusTest
@TestProfile(ObserverCatalogoTestProfile.class)
@DisplayName("Catalogo batch de una JRV")
class ObserverCargosConCandidatosIT {

  private static final String RUTA = "/api/v1/observer/cargos-with-candidatos";

  @Inject PasswordHasher hasher;
  @Inject JwtService jwt;
  @Inject EntityManager em;

  private String tokenObservador;
  private Long jrvId;

  @BeforeEach
  void preparar() {
    tokenObservador = tokenDe(crearUsuario("OBSERVER"));
    jrvId = primerJrvConCargos();
  }

  @Test
  @DisplayName("devuelve los mismos cargos que el endpoint suelto, con candidatos y partidos")
  void devuelveElMismoCatalogoQueElEndpointSuelto() {
    List<Integer> idsBatch =
        given()
            .header("Authorization", "Bearer " + tokenObservador)
            .queryParam("jrvId", jrvId)
            .when()
            .get(RUTA)
            .then()
            .statusCode(200)
            .body("cargos", notNullValue())
            .body("cargos", hasSize(greaterThan(0)))
            .body("cargos[0].partidos", notNullValue())
            .body("cargos[0].candidatos", notNullValue())
            .extract()
            .jsonPath()
            .getList("cargos.id", Integer.class);

    List<Integer> idsSueltos =
        given()
            .header("Authorization", "Bearer " + tokenObservador)
            .when()
            .get("/api/v1/observer/jrv/" + jrvId + "/cargos")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("id", Integer.class);

    assertThat(idsBatch)
        .as("el batch devuelve exactamente los cargos activos de la JRV, en el mismo orden")
        .containsExactlyElementsOf(idsSueltos);
  }

  @Test
  @DisplayName("sin token responde 401")
  void sinTokenRecibe401() {
    given().queryParam("jrvId", jrvId).when().get(RUTA).then().statusCode(401);
  }

  @Test
  @DisplayName("sin jrvId responde 400")
  void sinJrvIdRecibe400() {
    given()
        .header("Authorization", "Bearer " + tokenObservador)
        .when()
        .get(RUTA)
        .then()
        .statusCode(400);
  }

  @Test
  @DisplayName("una JRV inexistente devuelve la lista vacia")
  void jrvInexistenteDevuelveListaVacia() {
    given()
        .header("Authorization", "Bearer " + tokenObservador)
        .queryParam("jrvId", 999_999L)
        .when()
        .get(RUTA)
        .then()
        .statusCode(200)
        .body("cargos", hasSize(0));
  }

  // -------------------------------------------------------------------------
  // Ayudas
  // -------------------------------------------------------------------------

  /** Crea un usuario con el rol pedido y devuelve su id interno. */
  private Long crearUsuario(String nombreRol) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Rol rol = Rol.find("nombre", nombreRol).firstResult();
              Usuario u = new Usuario();
              u.setEmail(nombreRol.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
              u.setNombre(nombreRol);
              u.setApellido("Prueba");
              u.setPasswordHash(hasher.hash("clave-de-prueba-123"));
              u.setRol(rol);
              u.setHabilitado(true);
              u.persist();
              return u.getId();
            });
  }

  /** Token de acceso, con los permisos que el rol tiene en la base. */
  private String tokenDe(Long usuarioId) {
    Usuario u = QuarkusTransaction.requiringNew().call(() -> Usuario.findById(usuarioId));
    List<String> permisos =
        QuarkusTransaction.requiringNew()
            .call(() -> RolPermiso.authorityStringsByUsuarioId(usuarioId));
    return jwt.issue(
            u.getPublicId(), u.getEmail(), "OBSERVER", permisos, UUID.randomUUID().toString())
        .access();
  }

  /** Primera JRV del seed que tenga al menos un cargo habilitado. */
  private Long primerJrvConCargos() {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                ((Number)
                        em.createNativeQuery(
                                "select jc.jrv_id from jrv_cargo jc where jc.activo order by jc.jrv_id limit 1")
                            .getSingleResult())
                    .longValue());
  }
}
