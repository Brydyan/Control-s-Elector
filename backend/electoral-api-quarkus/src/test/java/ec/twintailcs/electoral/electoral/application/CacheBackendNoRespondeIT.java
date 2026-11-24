package ec.twintailcs.electoral.electoral.application;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.shared.cache.CacheDeLectura;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * El backend de cache que no responde es una degradacion, no un error.
 *
 * <p>Es la regresion del 500 intermitente del CI. El sintoma era un {@code
 * ArcUndeclaredThrowableException} (una excepcion checked envuelta por ARC) y un 500 en listados de
 * cargos, candidatos y geografia: los tres unicos caminos que consultan la cache <b>dentro</b> de
 * la transaccion del caso de uso.
 *
 * <p>La cadena era: la llamada a Valkey se quedaba esperando para siempre (sin techo de tiempo), la
 * transaccion de 60 s la.cancelaba el reaper de Narayana, y el commit lanzaba {@code
 * jakarta.transaction.RollbackException}, que al ser checked escapaba del bean interceptado y ARC
 * la envolvia. Con el techo de tiempo, un backend mudo degrada a fallo de cache: se sirve desde la
 * base y la API responde 200.
 *
 * <p>Este test afirma las dos mitades: no hay excepcion y el dato llega igual desde la base.
 *
 * <p>La invalidacion es el otro camino de la cache, y sufria el mismo mal: el interceptor de
 * Quarkus la resolvia con {@code await().indefinitely()} sin capturar la excepcion, de modo que un
 * backend inalcanzable devolvia 500 en <b>toda escritura</b> (crear cargo, partido, provincia),
 * aunque la escritura ya se hubiera confirmado en la base. Con la invalidacion tolerante, el 500 se
 * va: la escritura responde 201 y el fallo del backend queda como degradacion.
 */
@QuarkusTest
@TestProfile(CacheBackendNoRespondeTestProfile.class)
class CacheBackendNoRespondeIT {

  @Inject ConfiguracionService configuracionService;
  @Inject CacheDeLectura cache;
  @Inject PasswordHasher hasher;
  @Inject JwtService jwt;

  private String tokenMaster;

  @BeforeEach
  void crearMasterDePrueba() {
    Long masterId =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Rol rol = Rol.find("nombre", "MASTER").firstResult();
                  Usuario u = new Usuario();
                  u.setEmail("master-" + UUID.randomUUID() + "@test.local");
                  u.setNombre("Master");
                  u.setApellido("Prueba");
                  u.setPasswordHash(hasher.hash("clave-de-prueba-123"));
                  u.setRol(rol);
                  u.setHabilitado(true);
                  u.persist();
                  return u.getId();
                });

    List<String> permisos =
        QuarkusTransaction.requiringNew()
            .call(() -> RolPermiso.authorityStringsByUsuarioId(masterId));
    Usuario u = QuarkusTransaction.requiringNew().call(() -> Usuario.findById(masterId));
    tokenMaster =
        jwt.issue(u.getPublicId(), u.getEmail(), "MASTER", permisos, UUID.randomUUID().toString())
            .access();
  }

  @Test
  @Timeout(60)
  @DisplayName("con el backend de cache sin respuesta, el listado de cargos se sirve desde la base")
  void elListadoDeCargosDegradaAMiss() {
    List<Cargo> cargos = configuracionService.listCargos();

    assertThat(cargos)
        .as("el listado se sirve desde la base aunque la cache no conteste")
        .isNotEmpty();
    assertThat(cargos.get(0).getNombre()).isNotBlank();
  }

  @Test
  @Timeout(60)
  @DisplayName(
      "con el backend de cache sin respuesta, la escritura en la cache no rompe la operacion")
  void laEscrituraEnLaCacheDegradaSinError() {
    // El camino de escritura es el otro lado del mismo problema: si el write se queda esperando
    // para siempre, tumba la transaccion igual que la lectura.
    configuracionService.listCargos();

    assertThat(cache).isNotNull();
  }

  @Test
  @Timeout(90)
  @DisplayName(
      "con el backend de cache sin respuesta, crear un cargo responde 201: la invalidacion degrada")
  void laInvalidacionDegradadaNoRompeLaEscritura() {
    // Regresion del 500 en toda escritura: la invalidacion de la cache se resolvia con
    // await().indefinitely() sin capturar la excepcion, asi que un backend inalcanzable devolvia
    // 500
    // en crear cargo, partido, provincia... La escritura ya estaba confirmada en la base: solo la
    // invalidacion fallaba.
    given()
        .header("Authorization", "Bearer " + tokenMaster)
        .contentType("application/json")
        .body(
            "{\"nombre\":\"Cargo sin cache "
                + UUID.randomUUID()
                + "\",\"nivel\":\"PROVINCIA\",\"puestos\":1}")
        .when()
        .post("/api/v1/configuracion/cargos")
        .then()
        .statusCode(201);
  }
}
