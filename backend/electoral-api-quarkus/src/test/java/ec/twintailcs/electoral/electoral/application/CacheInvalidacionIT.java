package ec.twintailcs.electoral.electoral.application;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Usuario;
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
 * T-CACHE-STRAT-01 D: la cache no puede servir dato viejo.
 *
 * <p>Los datos de referencia se cachean (cargos, partidos, candidatos, geografia), asi que una
 * escritura tiene que tirar el cache: sin eso, el MASTER crea un cargo y el listado sigue mostrando
 * la lista anterior durante el TTL completo.
 *
 * <p>Este test es el que sujeta esa garantia: lee (que llena la cache), escribe por la API, y
 * vuelve a leer. Si la escritura no invalida, la segunda lectura devuelve la lista vieja y el test
 * cae. No hace falta tocar la cache a mano para verlo.
 */
@QuarkusTest
@TestProfile(CacheInvalidationTestProfile.class)
class CacheInvalidacionIT {

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
  @DisplayName("un cargo nuevo aparece de inmediato en el listado: la escritura tiro la cache")
  void unCargoNuevoApareceDeInmediato() {
    // Primera lectura: llena la cache de cargos.
    List<String> antes = nombresDeCargos();
    assertThat(antes).isNotNull();

    String nombre = "Cargo de prueba " + UUID.randomUUID();

    given()
        .header("Authorization", "Bearer " + tokenMaster)
        .contentType("application/json")
        .body("{\"nombre\":\"" + nombre + "\",\"nivel\":\"PROVINCIA\",\"puestos\":1}")
        .when()
        .post("/api/v1/configuracion/cargos")
        .then()
        .statusCode(201);

    assertThat(nombresDeCargos())
        .as("el cargo recien creado ya esta en el listado")
        .contains(nombre);
  }

  @Test
  @DisplayName("una provincia nueva aparece de inmediato en el arbol: la escritura tiro la cache")
  void unaProvinciaNuevaApareceDeInmediato() {
    // Primera lectura: llena la cache de geografia.
    assertThat(arbolDeGeografia()).isNotNull();

    String nombre = "Provincia de prueba " + UUID.randomUUID();

    given()
        .header("Authorization", "Bearer " + tokenMaster)
        .contentType("application/json")
        .body("{\"nombre\":\"" + nombre + "\"}")
        .when()
        .post("/api/v1/master/provincias")
        .then()
        .statusCode(201);

    assertThat(arbolDeGeografia())
        .as("la provincia recien creada ya esta en el arbol")
        .contains(nombre);
  }

  /** Nombres de los cargos del listado. */
  private List<String> nombresDeCargos() {
    return given()
        .header("Authorization", "Bearer " + tokenMaster)
        .when()
        .get("/api/v1/configuracion/cargos")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList("nombre");
  }

  /** Nombres de las provincias del arbol geografico. */
  private List<String> arbolDeGeografia() {
    return given()
        .header("Authorization", "Bearer " + tokenMaster)
        .when()
        .get("/api/v1/master/geografia")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList("provincias.nombre");
  }
}
