package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del CRUD de provincias (SDD 2026-11-16-config-geografica).
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code POST /api/v1/master/provincias} → 201, {@code PUT} → 200, {@code DELETE} → 204.
 *   <li>Codigo repetido → 409. Provincia inexistente → 404. Payload invalido → 400.
 *   <li>Las tres mutaciones son de MASTER: ADMIN, COORDINATOR y OBSERVER reciben 403.
 * </ul>
 */
class GeografiaProvinciaContractIT extends ContractTestSupport {

  private static final String PROVINCIAS = "/api/v1/master/provincias";

  /** Contador que evita dos codigos iguales dentro de la misma corrida. */
  private static final AtomicInteger CONTADOR = new AtomicInteger();

  /** Codigo de provincia unico por corrida. */
  private String codigoUnico() {
    return "P" + System.nanoTime() % 100000 + "-" + CONTADOR.incrementAndGet();
  }

  @Test
  @DisplayName("el ciclo de alta, edicion y baja de una provincia funciona: 201 / 200 / 204")
  void cicloDeVidaDeLaProvincia() {
    String codigo = codigoUnico();

    Response creada =
        as(masterToken()).body(json(Map.of("nombre", "Provincia de contrato", "codigo", codigo)))
            .post(PROVINCIAS);
    assertThat(creada.statusCode()).as("body: %s", creada.asString()).isEqualTo(201);

    long id = creada.jsonPath().getLong("id");
    assertThat(creada.jsonPath().getString("nombre")).isEqualTo("Provincia de contrato");
    assertThat(creada.jsonPath().getString("codigo")).isEqualTo(codigo);

    Response editada =
        as(masterToken())
            .body(json(Map.of("nombre", "Provincia editada", "codigo", codigo)))
            .put(PROVINCIAS + "/" + id);
    assertThat(editada.statusCode()).as("body: %s", editada.asString()).isEqualTo(200);
    assertThat(editada.jsonPath().getString("nombre")).isEqualTo("Provincia editada");

    Response borrada = as(masterToken()).delete(PROVINCIAS + "/" + id);
    assertThat(borrada.statusCode()).as("body: %s", borrada.asString()).isEqualTo(204);
  }

  @Test
  @DisplayName("un codigo repetido se rechaza con 409")
  void codigoDuplicadoEs409() {
    // "24" es el codigo de la provincia Santa Elena del seed.
    Response r =
        as(masterToken()).body(json(Map.of("nombre", "Duplicada", "codigo", "24"))).post(PROVINCIAS);

    assertErrorShape(r, 409);
  }

  @Test
  @DisplayName("una provincia que no existe se rechaza con 404")
  void provinciaInexistenteEs404() {
    assertErrorShape(as(masterToken()).delete(PROVINCIAS + "/999999"), 404);
  }

  @Test
  @DisplayName("un nombre vacio se rechaza con 400")
  void nombreVacioEs400() {
    Response r =
        as(masterToken()).body(json(Map.of("nombre", "  ", "codigo", codigoUnico()))).post(PROVINCIAS);

    assertErrorShape(r, 400);
  }

  @Test
  @DisplayName("solo MASTER puede crear, editar o borrar provincias")
  void soloMasterEscribeProvincias() {
    for (String rol : new String[] {"ADMIN", "COORDINATOR", "OBSERVER"}) {
      Response post =
          as(tokenFor(rol))
              .body(json(Map.of("nombre", "Intrusa", "codigo", codigoUnico())))
              .post(PROVINCIAS);
      assertErrorShape(post, 403);

      Response put =
          as(tokenFor(rol))
              .body(json(Map.of("nombre", "Intrusa", "codigo", codigoUnico())))
              .put(PROVINCIAS + "/1");
      assertErrorShape(put, 403);

      Response delete = as(tokenFor(rol)).delete(PROVINCIAS + "/1");
      assertErrorShape(delete, 403);
    }
  }

  @Test
  @DisplayName("sin token, el CRUD de provincias responde 401")
  void sinTokenElCrudResponde401() {
    assertErrorShape(anon().post(PROVINCIAS), 401);
    assertErrorShape(anon().put(PROVINCIAS + "/1"), 401);
    assertErrorShape(anon().delete(PROVINCIAS + "/1"), 401);
  }
}
