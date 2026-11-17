package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del CRUD de parroquias y de JRVs (SDD 2026-11-16-config-geografica, fase C).
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code POST /api/v1/master/parroquias} → 201, {@code PUT} → 200, {@code DELETE} → 204.
 *   <li>{@code POST /api/v1/master/jrvs} → 201, {@code PUT} → 200, {@code DELETE} → 204.
 *   <li>Parroquia con JRVs → 409 con {@code fields.childrenCount}. JRV con actas → 409 con {@code
 *       fields.actasCount}.
 *   <li>Canton o parroquia inexistente → 404. Payload invalido → 400.
 *   <li>Las mutaciones son de MASTER: ADMIN, COORDINATOR y OBSERVER reciben 403.
 * </ul>
 */
class GeografiaParroquiaJrvContractIT extends ContractTestSupport {

  private static final String PARROQUIAS = "/api/v1/master/parroquias";
  private static final String JRVS = "/api/v1/master/jrvs";

  /** Contador que evita dos numeros de JRV iguales dentro de la misma corrida. */
  private static final java.util.concurrent.atomic.AtomicInteger CONTADOR =
      new java.util.concurrent.atomic.AtomicInteger();

  /** Id de un canton real del seed: la parroquia cuelga de un canton. */
  private long cantonId() {
    var cantones = listar(as(masterToken()).get("/api/v1/configuracion/cantones"));
    assertThat(cantones).as("el seed tiene que traer cantones").isNotEmpty();
    return ((Number) cantones.get(0).get("id")).longValue();
  }

  /** Numero de JRV unico por corrida. */
  private String numeroJrvUnico() {
    return "JRV-CT-" + System.nanoTime() % 1000000 + "-" + CONTADOR.incrementAndGet();
  }

  @Test
  @DisplayName("el ciclo de alta, edicion y baja de una parroquia funciona: 201 / 200 / 204")
  void cicloDeVidaDeLaParroquia() {
    long canton = cantonId();
    String nombre = unique("Parroquia de contrato");

    Response creada =
        as(masterToken())
            .body(json(Map.of("nombre", nombre, "esUrbana", true, "cantonId", canton)))
            .post(PARROQUIAS);
    assertThat(creada.statusCode()).as("body: %s", creada.asString()).isEqualTo(201);

    long id = creada.jsonPath().getLong("id");
    assertThat(creada.jsonPath().getString("nombre")).isEqualTo(nombre);
    assertThat(creada.jsonPath().getBoolean("esUrbana")).isTrue();
    assertThat(creada.jsonPath().getLong("cantonId")).isEqualTo(canton);

    Response editada =
        as(masterToken())
            .body(json(Map.of("nombre", nombre + " editada", "esUrbana", false, "cantonId", canton)))
            .put(PARROQUIAS + "/" + id);
    assertThat(editada.statusCode()).as("body: %s", editada.asString()).isEqualTo(200);
    assertThat(editada.jsonPath().getString("nombre")).isEqualTo(nombre + " editada");
    assertThat(editada.jsonPath().getBoolean("esUrbana")).isFalse();

    Response borrada = as(masterToken()).delete(PARROQUIAS + "/" + id);
    assertThat(borrada.statusCode()).as("body: %s", borrada.asString()).isEqualTo(204);
  }

  @Test
  @DisplayName("el ciclo de alta, edicion y baja de una JRV funciona: 201 / 200 / 204")
  void cicloDeVidaDeLaJrv() {
    long parroquia = crearParroquia();
    String numero = numeroJrvUnico();

    Response creada =
        as(masterToken())
            .body(
                json(
                    Map.of(
                        "numero", numero,
                        "recinto", "Recinto de contrato",
                        "totalElectores", 250,
                        "parroquiaId", parroquia)))
            .post(JRVS);
    assertThat(creada.statusCode()).as("body: %s", creada.asString()).isEqualTo(201);

    long id = creada.jsonPath().getLong("id");
    JRVS_CREADOS.add(id);
    assertThat(creada.jsonPath().getString("numero")).isEqualTo(numero);
    assertThat(creada.jsonPath().getString("recinto")).isEqualTo("Recinto de contrato");
    assertThat(creada.jsonPath().getInt("totalElectores")).isEqualTo(250);
    assertThat(creada.jsonPath().getLong("parroquiaId")).isEqualTo(parroquia);

    Response editada =
        as(masterToken())
            .body(
                json(
                    Map.of(
                        "numero", numero,
                        "recinto", "Recinto editado",
                        "totalElectores", 300,
                        "parroquiaId", parroquia)))
            .put(JRVS + "/" + id);
    assertThat(editada.statusCode()).as("body: %s", editada.asString()).isEqualTo(200);
    assertThat(editada.jsonPath().getString("recinto")).isEqualTo("Recinto editado");
    assertThat(editada.jsonPath().getInt("totalElectores")).isEqualTo(300);

    Response borrada = as(masterToken()).delete(JRVS + "/" + id);
    assertThat(borrada.statusCode()).as("body: %s", borrada.asString()).isEqualTo(204);
  }

  @Test
  @DisplayName("una parroquia con JRVs no se puede borrar: 409 con el numero de JRVs")
  void parroquiaConJrvsNoSeBorra() {
    long parroquia = crearParroquia();
    Response jrv =
        as(masterToken())
            .body(
                json(
                    Map.of(
                        "numero", numeroJrvUnico(),
                        "recinto", "Recinto con acta",
                        "totalElectores", 100,
                        "parroquiaId", parroquia)))
            .post(JRVS);
    assertThat(jrv.statusCode()).as("body: %s", jrv.asString()).isEqualTo(201);
    JRVS_CREADOS.add(jrv.jsonPath().getLong("id"));

    Response r = as(masterToken()).delete(PARROQUIAS + "/" + parroquia);

    assertErrorShape(r, 409);
    assertThat(r.jsonPath().getString("fields.childrenCount"))
        .as("body: %s", r.asString())
        .isEqualTo("1");
  }

  @Test
  @DisplayName("una JRV con actas registradas no se puede borrar: 409 con el numero de actas")
  void jrvConActasNoSeBorra() {
    long jrvConActa = idDeJrvDelSeed("JRV-SE-001");

    Response r = as(masterToken()).delete(JRVS + "/" + jrvConActa);

    assertErrorShape(r, 409);
    assertThat(r.jsonPath().getString("fields.actasCount"))
        .as("body: %s", r.asString())
        .isNotBlank();
  }

  @Test
  @DisplayName("una parroquia o una JRV que no existen se rechazan con 404")
  void entidadInexistenteEs404() {
    assertErrorShape(as(masterToken()).delete(PARROQUIAS + "/999999"), 404);
    assertErrorShape(as(masterToken()).delete(JRVS + "/999999"), 404);

    Response parroquia =
        as(masterToken())
            .body(json(Map.of("nombre", "Huerfana", "esUrbana", true, "cantonId", 999999)))
            .post(PARROQUIAS);
    assertErrorShape(parroquia, 404);

    Response jrv =
        as(masterToken())
            .body(
                json(
                    Map.of(
                        "numero", numeroJrvUnico(),
                        "recinto", "Huerfana",
                        "totalElectores", 10,
                        "parroquiaId", 999999)))
            .post(JRVS);
    assertErrorShape(jrv, 404);
  }

  @Test
  @DisplayName("un payload invalido se rechaza con 400")
  void payloadInvalidoEs400() {
    long canton = cantonId();

    assertErrorShape(
        as(masterToken())
            .body(json(Map.of("nombre", "  ", "esUrbana", true, "cantonId", canton)))
            .post(PARROQUIAS),
        400);

    assertErrorShape(
        as(masterToken())
            .body(json(Map.of("nombre", unique("Sin urbana"), "cantonId", canton)))
            .post(PARROQUIAS),
        400);

    assertErrorShape(
        as(masterToken())
            .body(
                json(
                    Map.of(
                        "numero", numeroJrvUnico(),
                        "recinto", "Sin electores",
                        "totalElectores", 0,
                        "parroquiaId", crearParroquia())))
            .post(JRVS),
        400);
  }

  @Test
  @DisplayName("solo MASTER puede crear, editar o borrar parroquias y JRVs")
  void soloMasterEscribe() {
    long parroquia = crearParroquia();

    for (String rol : new String[] {"ADMIN", "COORDINATOR", "OBSERVER"}) {
      Response postParroquia =
          as(tokenFor(rol))
              .body(json(Map.of("nombre", unique("Intrusa"), "esUrbana", true, "cantonId", cantonId())))
              .post(PARROQUIAS);
      assertErrorShape(postParroquia, 403);

      Response putParroquia =
          as(tokenFor(rol))
              .body(json(Map.of("nombre", unique("Intrusa"), "esUrbana", true, "cantonId", cantonId())))
              .put(PARROQUIAS + "/" + parroquia);
      assertErrorShape(putParroquia, 403);

      Response deleteParroquia = as(tokenFor(rol)).delete(PARROQUIAS + "/" + parroquia);
      assertErrorShape(deleteParroquia, 403);

      Response postJrv =
          as(tokenFor(rol))
              .body(
                  json(
                      Map.of(
                          "numero", numeroJrvUnico(),
                          "recinto", "Intrusa",
                          "totalElectores", 10,
                          "parroquiaId", parroquia)))
              .post(JRVS);
      assertErrorShape(postJrv, 403);

      Response deleteJrv = as(tokenFor(rol)).delete(JRVS + "/1");
      assertErrorShape(deleteJrv, 403);
    }
  }

  @Test
  @DisplayName("sin token, el CRUD de parroquias y JRVs responde 401")
  void sinTokenElCrudResponde401() {
    assertErrorShape(anon().post(PARROQUIAS), 401);
    assertErrorShape(anon().put(PARROQUIAS + "/1"), 401);
    assertErrorShape(anon().delete(PARROQUIAS + "/1"), 401);
    assertErrorShape(anon().post(JRVS), 401);
    assertErrorShape(anon().put(JRVS + "/1"), 401);
    assertErrorShape(anon().delete(JRVS + "/1"), 401);
  }

  /**
   * Parroquias y JRVs que creo los tests de esta clase.
   *
   * <p>Se borran al terminar: la geografía es data de produccion en la base de desarrollo y
   * dejar parroquias sueltas ensucia el arbol que ven el frontend y los demas tests.
   */
  private static final List<Long> PARROQUIAS_CREADAS = new ArrayList<>();

  /** JRVs creados por esta clase, para poder borrar la parroquia que los contiene. */
  private static final List<Long> JRVS_CREADOS = new ArrayList<>();

  /** Borra lo que los tests crearon. */
  @AfterEach
  void limpiarGeografiaCreada() {
    for (Long jrv : JRVS_CREADOS) {
      as(masterToken()).delete(JRVS + "/" + jrv);
    }
    JRVS_CREADOS.clear();
    for (Long parroquia : PARROQUIAS_CREADAS) {
      as(masterToken()).delete(PARROQUIAS + "/" + parroquia);
    }
    PARROQUIAS_CREADAS.clear();
  }

  /** Crea una parroquia propia del test y devuelve su id. */
  private long crearParroquia() {
    Response r =
        as(masterToken())
            .body(
                json(Map.of("nombre", unique("Parroquia"), "esUrbana", true, "cantonId", cantonId())))
            .post(PARROQUIAS);
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    long id = r.jsonPath().getLong("id");
    PARROQUIAS_CREADAS.add(id);
    return id;
  }

  /** Id interno de una JRV del seed, buscada por su numero en el arbol geografico. */
  @SuppressWarnings("unchecked")
  private long idDeJrvDelSeed(String numero) {
    Response arbol = as(masterToken()).get("/api/v1/master/geografia");
    assertThat(arbol.statusCode()).as("body: %s", arbol.asString()).isEqualTo(200);

    for (Map<String, Object> provincia : arbol.jsonPath().getList("provincias", Map.class)) {
      for (Map<String, Object> canton :
          (java.util.List<Map<String, Object>>) provincia.get("cantones")) {
        for (Map<String, Object> parroquia :
            (java.util.List<Map<String, Object>>) canton.get("parroquias")) {
          for (Map<String, Object> jrv :
              (java.util.List<Map<String, Object>>) parroquia.get("jrvs")) {
            if (numero.equals(jrv.get("numero"))) {
              return ((Number) jrv.get("id")).longValue();
            }
          }
        }
      }
    }
    throw new AssertionError("La JRV " + numero + " no existe en el seed del backend");
  }
}
