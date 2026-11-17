package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del CRUD de cantones (SDD 2026-11-16-config-geografica, fase B).
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code POST /api/v1/master/cantones} → 201 con el canton creado.
 *   <li>{@code PUT /api/v1/master/cantones/{id}} → 200 con el canton actualizado.
 *   <li>{@code DELETE /api/v1/master/cantones/{id}} → 204.
 *   <li>Codigo repetido en la misma provincia → 409. Provincia inexistente → 404.
 *   <li>Las tres mutaciones son de MASTER: ADMIN, COORDINATOR y OBSERVER reciben 403.
 * </ul>
 */
class GeografiaCantonContractIT extends ContractTestSupport {

  private static final String CANTONES = "/api/v1/master/cantones";

  @Test
  @DisplayName("el ciclo de alta, edicion y baja de un canton funciona: 201 / 200 / 204")
  void cicloDeVidaDelCanton() {
    // El codigo tiene que ser unico en toda la base (el indice ix_canton_codigo es global).
    String codigo = "T" + System.nanoTime() % 100000;

    Response creado =
        as(masterToken())
            .body(json(Map.of("nombre", "Canton de contrato", "codigo", codigo, "provinciaId", 1)))
            .post(CANTONES);
    assertThat(creado.statusCode()).as("body: %s", creado.asString()).isEqualTo(201);

    long id = creado.jsonPath().getLong("id");
    assertThat(creado.jsonPath().getString("nombre")).isEqualTo("Canton de contrato");
    assertThat(creado.jsonPath().getString("codigo")).isEqualTo(codigo);
    assertThat(creado.jsonPath().getLong("provinciaId")).isEqualTo(1L);

    Response editado =
        as(masterToken())
            .body(json(Map.of("nombre", "Canton editado", "codigo", codigo, "provinciaId", 1)))
            .put(CANTONES + "/" + id);
    assertThat(editado.statusCode()).as("body: %s", editado.asString()).isEqualTo(200);
    assertThat(editado.jsonPath().getString("nombre")).isEqualTo("Canton editado");

    Response borrado = as(masterToken()).delete(CANTONES + "/" + id);
    assertThat(borrado.statusCode()).as("body: %s", borrado.asString()).isEqualTo(204);
  }

  @Test
  @DisplayName("un codigo repetido en la misma provincia se rechaza con 409")
  void codigoDuplicadoEs409() {
    // "2401" es el codigo del canton Santa Elena del seed, en la provincia 1.
    Response r =
        as(masterToken())
            .body(json(Map.of("nombre", "Duplicado", "codigo", "2401", "provinciaId", 1)))
            .post(CANTONES);

    assertErrorShape(r, 409);
  }

  @Test
  @DisplayName("una provincia que no existe se rechaza con 404")
  void provinciaInexistenteEs404() {
    Response r =
        as(masterToken())
            .body(json(Map.of("nombre", "Huerfano", "codigo", "T98765", "provinciaId", 999999)))
            .post(CANTONES);

    assertErrorShape(r, 404);
  }

  @Test
  @DisplayName("un nombre vacio se rechaza con 400")
  void nombreVacioEs400() {
    Response r =
        as(masterToken())
            .body(json(Map.of("nombre", "  ", "codigo", "T98766", "provinciaId", 1)))
            .post(CANTONES);

    assertErrorShape(r, 400);
  }

  @Test
  @DisplayName("solo MASTER puede crear, editar o borrar cantones")
  void soloMasterEscribeCantones() {
    for (String rol : new String[] {"ADMIN", "COORDINATOR", "OBSERVER"}) {
      Response post =
          as(tokenFor(rol))
              .body(json(Map.of("nombre", "Intruso", "codigo", "T98767", "provinciaId", 1)))
              .post(CANTONES);
      assertErrorShape(post, 403);

      Response put =
          as(tokenFor(rol))
              .body(json(Map.of("nombre", "Intruso", "codigo", "T98768", "provinciaId", 1)))
              .put(CANTONES + "/1");
      assertErrorShape(put, 403);

      Response delete = as(tokenFor(rol)).delete(CANTONES + "/1");
      assertErrorShape(delete, 403);
    }
  }

  @Test
  @DisplayName("sin token, el CRUD de cantones responde 401")
  void sinTokenElCrudResponde401() {
    assertErrorShape(anon().post(CANTONES), 401);
    assertErrorShape(anon().put(CANTONES + "/1"), 401);
    assertErrorShape(anon().delete(CANTONES + "/1"), 401);
  }
}
