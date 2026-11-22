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
import org.junit.jupiter.api.AfterEach;
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
 * <p><b>El test arma su propio dataset.</b> No depende del seed de {@code jrv}/{@code jrv_cargo}:
 * otros IT de la suite limpian esas tablas ({@code delete from jrv}) y con eso dejaban esta prueba
 * sin datos, fallando o —peor— pasando por el {@code jrv} que otro test acababa de insertar. Aqui
 * se crean la JRV, el cargo, el partido y el candidato propios, y se borran al terminar. La
 * geometria (parroquia) si viene del seed: nadie la borra y hace falta para colgar la JRV.
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
  private Long cargoId;
  private Long partidoId;
  private Long candidatoId;

  @BeforeEach
  void preparar() {
    tokenObservador = tokenDe(crearUsuario("OBSERVER"));
    armarDataset();
  }

  @AfterEach
  void limpiar() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              em.createNativeQuery("delete from candidato where id = :id")
                  .setParameter("id", candidatoId)
                  .executeUpdate();
              em.createNativeQuery("delete from jrv_cargo where jrv_id = :id")
                  .setParameter("id", jrvId)
                  .executeUpdate();
              em.createNativeQuery("delete from jrv where id = :id")
                  .setParameter("id", jrvId)
                  .executeUpdate();
              em.createNativeQuery("delete from cargo where id = :id")
                  .setParameter("id", cargoId)
                  .executeUpdate();
              em.createNativeQuery("delete from partido where id = :id")
                  .setParameter("id", partidoId)
                  .executeUpdate();
            });
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
            .body("cargos[0].candidatos[0].nombre", notNullValue())
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

  /**
   * Arma el dataset minimo que el endpoint lee: una JRV colgada de una parroquia del seed, un
   * cargo, un partido, un candidato y la habilitacion del cargo en esa JRV.
   *
   * <p>Se hace con SQL nativo porque {@code jrv_cargo} no tiene entidad mapeada: la consulta que
   * hace el endpoint tambien es nativa.
   */
  private void armarDataset() {
    String sufijo = UUID.randomUUID().toString().substring(0, 8);
    // `partido.numero_lista` es UNIQUE: se deriva del mismo sufijo para no chocar con el seed
    // (1..4) ni con los partidos que crean otros IT.
    int numeroLista = 100_000 + Math.abs(UUID.randomUUID().hashCode() % 899_999);

    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              partidoId =
                  ((Number)
                          em.createNativeQuery(
                                  "insert into partido (nombre, siglas, numero_lista)"
                                      + " values (:nombre, :siglas, :numero) returning id")
                              .setParameter("nombre", "Lista de prueba " + sufijo)
                              .setParameter("siglas", "LPR-" + sufijo)
                              .setParameter("numero", numeroLista)
                              .getSingleResult())
                      .longValue();

              cargoId =
                  ((Number)
                          em.createNativeQuery(
                                  "insert into cargo (nombre, nivel, puestos)"
                                      + " values (:nombre, 'CANTON', 1) returning id")
                              .setParameter("nombre", "Cargo de prueba " + sufijo)
                              .getSingleResult())
                      .longValue();

              candidatoId =
                  ((Number)
                          em.createNativeQuery(
                                  "insert into candidato (nombre, orden, cargo_id, partido_id)"
                                      + " values (:nombre, 1, :cargo, :partido) returning id")
                              .setParameter("nombre", "Candidato de prueba " + sufijo)
                              .setParameter("cargo", cargoId)
                              .setParameter("partido", partidoId)
                              .getSingleResult())
                      .longValue();

              jrvId =
                  ((Number)
                          em.createNativeQuery(
                                  "insert into jrv (numero, recinto, total_electores, parroquia_id)"
                                      + " select :numero, :recinto, 100, p.id"
                                      + " from parroquia p order by p.id limit 1 returning id")
                              .setParameter("numero", "JRV-TEST-" + sufijo)
                              .setParameter("recinto", "Recinto de prueba " + sufijo)
                              .getSingleResult())
                      .longValue();

              em.createNativeQuery(
                      "insert into jrv_cargo (jrv_id, cargo_id, activo) values (:jrv, :cargo, true)")
                  .setParameter("jrv", jrvId)
                  .setParameter("cargo", cargoId)
                  .executeUpdate();
            });
  }
}
