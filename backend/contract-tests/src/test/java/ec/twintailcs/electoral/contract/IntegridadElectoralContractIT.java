package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integridad electoral: la suma de votos y la unicidad del par (JRV, cargo) bajo concurrencia real.
 *
 * <p>Los otros archivos de la suite fijan <b>la forma</b> de las respuestas y <b>quién</b> puede
 * hacer qué. Este fija las dos reglas que no se pueden cumplir confiando en el cliente:
 *
 * <ul>
 *   <li><b>El invariante de suma.</b> {@code SUM(detalle.votos) + votos_blancos + votos_nulos}
 *       tiene que cerrar con {@code total_sufragantes}. Si no cierra, el backend rechaza el
 *       registro con 400: una acta con votos que no cuadran es un acta que no se puede contar. El
 *       frontend valida lo mismo, pero el backend es el que tiene que decirlo — el contrato no
 *       puede depender de que todos los clientes hagan la cuenta.
 *   <li><b>La unicidad del par.</b> Un par (JRV, cargo) admite una sola acta activa. Dos observers
 *       que registran el mismo par a la vez tienen que ver exactamente un 201 y un 409, y en la
 *       base no puede quedar más de una acta para ese par. El locking pesimista ({@code SELECT FOR
 *       UPDATE}) y el índice único parcial {@code uk_acta_activa} son la defensa; el 409 es lo que
 *       ve el observer que perdió la carrera.
 * </ul>
 *
 * <p><b>Por qué esto no se puede probar en otro lugar.</b> El invariante de suma sí se puede
 * afirmar por HTTP, y por eso está acá. La unicidad bajo concurrencia <b>solo</b> se puede afirmar
 * con dos requests simultáneos: con dos llamadas secuenciales la segunda encuentra el acta de la
 * primera con un SELECT normal y pasaría aunque no hubiera locking. Por eso los dos requests salen
 * con un {@link CountDownLatch}: si no salen a la vez, el test no prueba la carrera.
 *
 * <p><b>Sobre los datos.</b> Los dos tests reservan un par (JRV, cargo) libre con {@link
 * #comboLibre(String)}. El de la suma no llega a crear un acta (el backend la rechaza), así que
 * devuelve el par al pool a mano; el de la concurrencia deja un acta real, que la limpieza de la
 * clase base lleva hasta ANULADA para liberar el par.
 */
class IntegridadElectoralContractIT extends ContractTestSupport {

  /** Timeout de la carrera: los tests de concurrencia son intermitentes por naturaleza. */
  private static final int TIMEOUT_CONCURRENCIA_SEGUNDOS = 30;

  @Test
  @DisplayName(
      "registrar un acta cuyos votos no cuadran con el total de sufragantes devuelve 400 y no"
          + " guarda nada")
  void registrarActaConSumaDeVotosDescuadradaDevuelve400() {
    // Importa porque la suma de votos es el invariante que hace confiable un acta: si el backend
    // aceptara un total descuadrado, el conteo de la JRV no cerraría nunca y nadie se enteraría.
    // El 400 tiene que traer el detalle exacto de la cuenta: es el mensaje que el frontend
    // muestra al observer para que entienda qué no le cierra.
    String observer = observerToken();
    Map<String, Object> combo = comboLibre(observer);
    List<Long> candidatos = candidatosDe(cargoId(combo), observer);
    // cuadrado=false: el total de sufragantes no cierra con la suma de los votos.
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, false);

    Response r = as(observer).body(json(body)).post(OBSERVER + "/actas");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("error")).isEqualTo("Bad Request");
    assertThat(r.jsonPath().getString("message"))
        .as("el mensaje tiene que detailar la cuenta que no cierra: %s", r.asString())
        .isEqualTo(mensajeDeSumaDescuadrada(body));

    // El 400 no dejó nada a medias: el par sigue libre y el observer no registró ningun acta.
    assertThat(actasDelObserver(observer))
        .as("un acta rechazada no puede quedar registrada")
        .isEmpty();
    liberarComboReservado(combo);
  }

  @Test
  @DisplayName(
      "dos registros simultáneos del mismo par JRV+cargo: uno gana con 201 y el otro recibe 409")
  @Timeout(value = TIMEOUT_CONCURRENCIA_SEGUNDOS + 10, unit = TimeUnit.SECONDS)
  void dosRegistrosSimultaneosDelMismoParSoloUnoGana() throws Exception {
    // Importa porque en campo dos observers de la misma JRV pueden registrar el mismo cargo a la
    // vez. Si los dos ganaran, la JRV tendria dos actas para el mismo cargo y el conteo oficial
    // seria ambiguo. El perdedor tiene que recibir 409 (no un 500) y la base tiene que quedar con
    // una sola acta para el par.
    String observer = observerToken();
    Map<String, Object> combo = comboLibre(observer);
    List<Long> candidatos = candidatosDe(cargoId(combo), observer);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    CountDownLatch salir = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Response> respuestas;
    try {
      Future<Response> primero = executor.submit(() -> registrarEnParalelo(observer, body, salir));
      Future<Response> segundo = executor.submit(() -> registrarEnParalelo(observer, body, salir));

      // Los dos requests salen a la vez: sin esto la carrera no existe.
      salir.countDown();

      respuestas =
          List.of(
              primero.get(TIMEOUT_CONCURRENCIA_SEGUNDOS, TimeUnit.SECONDS),
              segundo.get(TIMEOUT_CONCURRENCIA_SEGUNDOS, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }

    List<Response> creados = respuestas.stream().filter(r -> r.statusCode() == 201).toList();
    List<Response> rechazados = respuestas.stream().filter(r -> r.statusCode() == 409).toList();

    assertThat(creados)
        .as("exactamente uno de los dos registros tiene que ganar: %s", respuestas)
        .hasSize(1);
    assertThat(rechazados)
        .as("el perdedor tiene que recibir 409, no un 500: %s", respuestas)
        .hasSize(1);

    // El perdedor recibe el error de dominio del backend, con la forma estandar.
    Response perdedor = rechazados.get(0);
    assertErrorShape(perdedor, 409);
    assertThat(perdedor.jsonPath().getString("error")).isEqualTo("Conflict");
    assertThat(perdedor.jsonPath().getString("message"))
        .as("el 409 explica que el par ya tiene acta activa: %s", perdedor.asString())
        .contains("Ya existe un acta activa");

    // La base queda con UNA sola acta para ese par.
    List<Map<String, Object>> actasDelPar =
        actasDelObserver(observer).stream()
            .filter(a -> a.get("jrvId") != null && a.get("cargoId") != null)
            .filter(
                a ->
                    jrvId(combo) == ((Number) a.get("jrvId")).longValue()
                        && cargoId(combo) == ((Number) a.get("cargoId")).longValue())
            .toList();
    assertThat(actasDelPar)
        .as("no puede quedar mas de un acta para el par (jrv, cargo): %s", actasDelPar)
        .hasSize(1);

    // La acta que gano queda anotada para que la limpieza la devuelva a ANULADA y libere el par.
    registrarActaCreada(creados.get(0).jsonPath().getLong("id"), observer, body);
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------------------

  /** Registra un acta y espera el instante de salida para que los dos requests salgan a la vez. */
  private static Response registrarEnParalelo(
      String token, Map<String, Object> body, CountDownLatch salir) throws InterruptedException {
    salir.await();
    return as(token).body(json(body)).post(OBSERVER + "/actas");
  }

  /** Actas registradas por el observer, tal como las ve su propio modulo. */
  private static List<Map<String, Object>> actasDelObserver(String token) {
    return listar(as(token).get(OBSERVER + "/mis-actas"));
  }
}
