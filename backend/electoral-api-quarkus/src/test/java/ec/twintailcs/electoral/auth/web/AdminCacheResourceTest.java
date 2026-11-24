package ec.twintailcs.electoral.auth.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Usuario;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
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
 * T-CACHE-STRAT-01 E.2: el endpoint de vaciado de cache.
 *
 * <p>Verifica lo que el SDD pide, con la matriz de roles escrita:
 *
 * <ul>
 *   <li>el MASTER vacia la cache y la respuesta dice que caches se limpiaron;
 *   <li>el vaciado deja el cache realmente vacio —si el endpoint contestara {@code OK} sin tocar la
 *       cache, este test caeria—;
 *   <li>un rol sin permiso recibe 403 y no toca nada;
 *   <li>sin token recibe 401;
 *   <li>un cache que no existe recibe 404;
 *   <li>el vaciado queda auditado en {@code audit_log}.
 * </ul>
 */
@QuarkusTest
@TestProfile(AdminCacheTestProfile.class)
class AdminCacheResourceTest {

  /** Clave de prueba dentro del cache: no es un dato del dominio, solo una entrada vaciable. */
  private static final String CLAVE = "sentinel-de-prueba";

  @Inject PasswordHasher hasher;
  @Inject JwtService jwt;
  @Inject EntityManager em;
  @Inject CacheManager cacheManager;
  @Inject ReactiveRedisDataSource redis;

  private String tokenMaster;
  private String tokenObservador;
  private Long masterId;
  private Long idObservador;

  @BeforeEach
  void crearUsuariosDePrueba() {
    masterId = crearUsuario("MASTER");
    idObservador = crearUsuario("OBSERVER");

    tokenMaster = tokenDe(masterId, "MASTER");
    tokenObservador = tokenDe(idObservador, "OBSERVER");
  }

  // -------------------------------------------------------------------------
  // Escenario 1: el MASTER puede vaciar
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("el MASTER vacia todos los caches y la respuesta dice cuales se limpiaron")
  void elMasterLimpiaTodosLosCaches() {
    // Los caches nacen cuando se usa el dato: primero se leen, despues existen.
    leer("/api/v1/configuracion/cargos");
    leer("/api/v1/configuracion/partidos");
    leer("/api/v1/configuracion/candidatos");
    leer("/api/v1/master/geografia");

    var res =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .when()
            .get("/api/v1/admin/cache/clear")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", containsString("All caches cleared"))
            .body("clearedCaches", notNullValue())
            .extract()
            .response();

    List<String> limpiados = res.jsonPath().getList("clearedCaches", String.class);
    assertThat(limpiados)
        .as("los caches de datos estaticos se vacian")
        .contains("cargos", "partidos", "candidatos", "geografia");
  }

  @Test
  @DisplayName("el vaciado deja el cache realmente vacio, no solo dice OK")
  void elVaciadoDejaElCacheVacio() {
    leer("/api/v1/configuracion/partidos");

    // En Phase 2 (Valkey), accedemos Redis directamente para verificar el cache.
    // La clave para "partidos" con clave "sentinel-de-prueba" es "partidos:sentinel-de-prueba"
    String claveRedis = "partidos:" + CLAVE;
    ReactiveValueCommands<String, String> valores = redis.value(String.class);

    // Se siembra una entrada y se comprueba que sobrevive: si el valor se perdiera solo, el test
    // pasaria sin comprobar nada.
    valores.set(claveRedis, "valor-de-prueba").await().indefinitely();
    assertThat(valores.get(claveRedis).await().indefinitely())
        .as("la entrada esta en la cache")
        .isEqualTo("valor-de-prueba");

    given()
        .header("Authorization", "Bearer " + tokenMaster)
        .queryParam("cacheName", "partidos")
        .when()
        .get("/api/v1/admin/cache/clear")
        .then()
        .statusCode(200);

    assertThat(valores.get(claveRedis).await().indefinitely())
        .as("tras el vaciado la entrada ya no esta")
        .isNull();
  }

  @Test
  @DisplayName("el vaciado de un cache especifico responde con el nombre de ese cache")
  void elVaciadoDeUnCacheEspecifico() {
    leer("/api/v1/configuracion/partidos");

    given()
        .header("Authorization", "Bearer " + tokenMaster)
        .queryParam("cacheName", "partidos")
        .when()
        .get("/api/v1/admin/cache/clear")
        .then()
        .statusCode(200)
        .body("success", is(true))
        .body("message", containsString("partidos"))
        .body("clearedCaches", hasItem("partidos"));
  }

  // -------------------------------------------------------------------------
  // Escenario 2: la matriz de roles
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("un rol sin permiso recibe 403 y no vacia nada")
  void unRolSinPermisoRecibe403() {
    leer("/api/v1/configuracion/partidos");

    // En Phase 2 (Valkey), accedemos Redis directamente.
    String claveRedis = "partidos:" + CLAVE;
    ReactiveValueCommands<String, String> valores = redis.value(String.class);
    valores.set(claveRedis, "valor").await().indefinitely();

    given()
        .header("Authorization", "Bearer " + tokenObservador)
        .when()
        .get("/api/v1/admin/cache/clear")
        .then()
        .statusCode(403);

    assertThat(valores.get(claveRedis).await().indefinitely())
        .as("un 403 no toca la cache")
        .isEqualTo("valor");
  }

  @Test
  @DisplayName("sin token recibe 401")
  void sinTokenRecibe401() {
    given().when().get("/api/v1/admin/cache/clear").then().statusCode(401);
  }

  @Test
  @DisplayName("un cache que no existe recibe 404")
  void unCacheQueNoExisteRecibe404() {
    given()
        .header("Authorization", "Bearer " + tokenMaster)
        .queryParam("cacheName", "cache-que-no-existe")
        .when()
        .get("/api/v1/admin/cache/clear")
        .then()
        .statusCode(404)
        .body("success", is(false))
        .body("message", containsString("not found"));
  }

  // -------------------------------------------------------------------------
  // Escenario 3: el rastro
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("el vaciado queda registrado en la auditoria")
  void elVaciadoQuedaRegistradoEnLaAuditoria() {
    given()
        .header("Authorization", "Bearer " + tokenMaster)
        .when()
        .get("/api/v1/admin/cache/clear")
        .then()
        .statusCode(200);

    List<Object[]> filas =
        leerConContextoDeMaster(
            "select accion, entidad, registrado_por from audit_log where accion = 'LIMPIAR_CACHE'");

    assertThat(filas).as("el vaciado deja rastro").isNotEmpty();
    Object[] ultima = filas.get(filas.size() - 1);
    assertThat(ultima[0]).isEqualTo("LIMPIAR_CACHE");
    assertThat(ultima[1]).isEqualTo("CACHE");
    assertThat(((Number) ultima[2]).longValue()).isEqualTo(masterId);
  }

  // -------------------------------------------------------------------------
  // Ayudas
  // -------------------------------------------------------------------------

  /**
   * Lee (o carga) una entrada del cache.
   *
   * <p>Usa la API de la cache con su cargador: si la clave esta, devuelve lo que hay; si no, carga
   * y guarda. Asi el test puede distinguir "la entrada sigue en la cache" de "la cache se vacio".
   *
   * <p>Phase 2 (Valkey): el cache "partidos" tiene value-type configurado como List, asi que el
   * sentinel debe ser compatible con ese tipo.
   */
  private Object valorEnCache(Cache cache, String valorSiNoEsta) {
    return cache.get(CLAVE, (Object k) -> java.util.List.of(valorSiNoEsta)).await().indefinitely();
  }

  /** Lee un endpoint protegido con el token del MASTER. */
  private void leer(String path) {
    given()
        .header("Authorization", "Bearer " + tokenMaster)
        .when()
        .get(path)
        .then()
        .statusCode(200);
  }

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

  /**
   * Token de acceso de un usuario, como lo emite el login.
   *
   * <p>Los permisos salen de la base ({@code rol_permiso}), igual que en el login real: un token
   * sin permisos no pasa ni por los endpoints de lectura, que exigen {@code configuracion:read}.
   */
  private String tokenDe(Long usuarioId, String rol) {
    Usuario u = QuarkusTransaction.requiringNew().call(() -> Usuario.findById(usuarioId));
    List<String> permisos =
        QuarkusTransaction.requiringNew()
            .call(() -> RolPermiso.authorityStringsByUsuarioId(usuarioId));
    return jwt.issue(u.getPublicId(), u.getEmail(), rol, permisos, UUID.randomUUID().toString())
        .access();
  }

  /**
   * Lee con el contexto de RLS del MASTER.
   *
   * <p>La tabla de auditoria tiene RLS y su politica de lectura solo deja ver las filas a quien
   * tiene rol COORDINATOR/MASTER/ADMIN: sin el contexto, la consulta no ve nada y el test pasaria
   * sin comprobar nada.
   */
  private List<Object[]> leerConContextoDeMaster(String sql) {
    List<?> crudo =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  em.createNativeQuery("select set_config('app.current_user_id', :id, true)")
                      .setParameter("id", masterId.toString())
                      .getSingleResult();
                  return em.createNativeQuery(sql).getResultList();
                });

    List<Object[]> filas = new java.util.ArrayList<>();
    for (Object fila : crudo) {
      filas.add(fila instanceof Object[] array ? array : new Object[] {fila});
    }
    return filas;
  }
}
