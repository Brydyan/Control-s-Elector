package ec.twintailcs.electoral.electoral.application;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.geografia.application.GeografiaService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-CACHE-STRAT-01: con la cache apagada, la app sigue funcionando.
 *
 * <p><b>El defecto que este test sujeta.</b> {@code Cache.as(Class)} de Quarkus lanza {@link
 * IllegalStateException} cuando el cache no es del tipo pedido — no {@code ClassCastException}. Con
 * {@code quarkus.cache.enabled=false} Quarkus entrega un {@code NoOpCache}, que no sobrescribe
 * {@code as()}: la excepcion escapaba del helper de lectura y <b>cada lectura de configuracion
 * devolvia 500</b>. El "knob" para medir la linea base sin cache producia una app caida.
 *
 * <p>Perder la cache es una degradacion (se sirve desde la base), no un error.
 */
@QuarkusTest
@TestProfile(CacheApagadaTestProfile.class)
class CacheApagadaIT {

  @Inject ConfiguracionService configuracionService;
  @Inject GeografiaService geografiaService;
  @Inject JwtService jwt;

  private String tokenMaster;

  @BeforeEach
  void crearToken() {
    // Las lecturas de /api/v1/configuracion exigen token (401 sin el), asi que
    // el test necesita uno con `configuracion:read`.
    Long masterId =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Rol rol = Rol.find("nombre", "MASTER").firstResult();
                  Usuario u = new Usuario();
                  u.setEmail("cache-off-" + UUID.randomUUID() + "@test.local");
                  u.setNombre("Cache");
                  u.setApellido("Apagada");
                  u.setPasswordHash("x");
                  u.setRol(rol);
                  u.setHabilitado(true);
                  u.persist();
                  return u.getId();
                });

    Usuario u = QuarkusTransaction.requiringNew().call(() -> Usuario.findById(masterId));
    List<String> permisos =
        QuarkusTransaction.requiringNew()
            .call(() -> RolPermiso.authorityStringsByUsuarioId(masterId));
    tokenMaster =
        jwt.issue(u.getPublicId(), u.getEmail(), "MASTER", permisos, UUID.randomUUID().toString())
            .access();
  }

  @Test
  @DisplayName("con la cache apagada, listar cargos no revienta")
  void listarCargosSinCache() {
    assertThatCode(() -> configuracionService.listCargos())
        .as("leer cargos con la cache apagada no debe lanzar")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("con la cache apagada, listar partidos no revienta")
  void listarPartidosSinCache() {
    assertThatCode(() -> configuracionService.listPartidos())
        .as("leer partidos con la cache apagada no debe lanzar")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("con la cache apagada, listar candidatos no revienta")
  void listarCandidatosSinCache() {
    assertThatCode(() -> configuracionService.listCandidatos(null, null))
        .as("leer candidatos con la cache apagada no debe lanzar")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("con la cache apagada, listar la geografia no revienta")
  void listarGeografiaSinCache() {
    assertThatCode(() -> geografiaService.listarArbol())
        .as("leer la geografia con la cache apagada no debe lanzar")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("con la cache apagada, los endpoints de configuracion responden 200")
  void endpointsDeConfiguracionResponden200() {
    // El sintoma del defecto era un 500 en estos endpoints: se afirma el
    // codigo de respuesta, no solo que no se lanze la excepcion.
    int cargos =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .get("/api/v1/configuracion/cargos")
            .statusCode();
    int partidos =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .get("/api/v1/configuracion/partidos")
            .statusCode();
    int candidatos =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .get("/api/v1/configuracion/candidatos")
            .statusCode();

    assertThat(cargos).as("GET /configuracion/cargos").isEqualTo(200);
    assertThat(partidos).as("GET /configuracion/partidos").isEqualTo(200);
    assertThat(candidatos).as("GET /configuracion/candidatos").isEqualTo(200);
  }
}
