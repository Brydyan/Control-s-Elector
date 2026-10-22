package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del flujo OBSERVER: catálogo de JRVs y cargos, registro de actas, borradores, envío,
 * corrección, consultas del veedor, fotografía del acta e incidencias.
 *
 * <p>Es la mitad "del que está en la mesa" del control electoral: el OBSERVER es el único rol que
 * escribe actas y el único que puede subirlas. La máquina de estados y el aislamiento por
 * {@code registrada_por_id} son invariantes que este archivo fija desde afuera, por HTTP.
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code GET /api/v1/observer/jrvs} → 200 con la lista de JRVs y su estado de registro.
 *   <li>{@code GET /api/v1/observer/jrv/{jrvId}/cargos} → 200 con los cargos de la JRV y el estado
 *       de su acta ({@code registrado}, {@code estadoActa}, {@code actaId}).
 *   <li>{@code POST /api/v1/observer/actas} → 201 con el acta ya en {@code ENVIADA}.
 *   <li>{@code POST /api/v1/observer/actas/borrador} → 200 con el acta en {@code BORRADOR} y sin
 *       validar la integridad electoral (los votos pueden no cuadrar).
 *   <li>{@code POST /api/v1/observer/actas/{actaId}/enviar} → 200 con la transición {@code BORRADOR}
 *       → {@code ENVIADA}.
 *   <li>{@code PUT /api/v1/observer/actas/{actaId}/corregir} → 200 con el acta en {@code CORREGIDA}.
 *   <li>{@code GET /api/v1/observer/mis-actas} → 200 con las actas del observer autenticado.
 *   <li>{@code GET /api/v1/observer/correcciones-pendientes} → 200 con las actas en disputa del
 *       observer autenticado.
 *   <li>{@code GET /api/v1/observer/actas/{actaId}} → 200 con el detalle completo, y 403 si el
 *       observer no es el autor.
 *   <li>{@code POST /api/v1/observer/actas/{actaId}/imagen} → 201 al subir la fotografía.
 *   <li>{@code POST /api/v1/observer/incidencias} → 201 con la incidencia registrada.
 *   <li>Registrar dos veces la misma JRV + cargo → 409.
 * </ul>
 *
 * <p><b>Desvío conocido entre el OpenAPI y el backend.</b> El controller documenta 404 para un
 * acta inexistente, pero {@code ActaInvalidaException} se mapea a 400 en el
 * {@code GlobalExceptionHandler}: este archivo fija el 400 observado, que es el comportamiento real
 * que la fase Quarkus tiene que reproducir.
 *
 * <p><b>Sobre los datos.</b> El seed determinista de Liquibase deja 5 JRVs con 2 cargos cada una.
 * Como la unicidad de un acta es (JRV, cargo), cada test que registra un acta reserva un par
 * distinto: {@link #comboLibre(String)} busca el primero sin acta activa que además no haya sido
 * tomado por otro test de esta corrida. Al terminar, cada test devuelve sus actas a {@code
 * ANULADA} (el único estado que no bloquea el par JRV + cargo), de modo que la suite se puede
 * re-ejecutar sobre la misma base sin limpiar nada a mano.
 */
class ObserverActasContractIT extends ContractTestSupport {

  /** Fotografía mínima: al backend le importa el Content-Type {@code image/*}, no los bytes. */
  private static final byte[] FOTO_PNG = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'
  };

  // -----------------------------------------------------------------------------------------------
  // Catálogo
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /observer/jrvs devuelve todas las JRVs con sus cargos pendientes y registrados")
  void jrvsDevuelvenLaListaDeJRVsConSuEstadoDeRegistro() {
    String token = observerToken();

    Response r = as(token).get(OBSERVER + "/jrvs");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> jrvs = r.jsonPath().getList("$");
    assertThat(jrvs).isNotEmpty();
    assertThat(jrvs)
        .allSatisfy(
            jrv -> {
              assertThat(jrv.get("id")).isNotNull();
              assertThat(jrv.get("numero")).asString().startsWith("JRV-");
              assertThat(jrv.get("parroquia")).isNotNull();
              assertThat(((Number) jrv.get("cargosPendientes")).intValue())
                  .isGreaterThanOrEqualTo(0);
              assertThat(((Number) jrv.get("cargosRegistrados")).intValue())
                  .isGreaterThanOrEqualTo(0);
            });
    // El seed determinista de Liquibase es la precondición de toda la suite.
    assertThat(jrvs)
        .extracting(jrv -> jrv.get("numero"))
        .contains("JRV-SE-001", "JRV-SE-002", "JRV-BAL-001", "JRV-LL-001");
  }

  @Test
  @DisplayName("GET /observer/jrv/{jrvId}/cargos devuelve los cargos de la JRV con el estado de su acta")
  void cargosDeUnaJrvDevuelvenLosCargosConSuEstadoDeActa() {
    String token = observerToken();
    long jrvId = jrvIdDe("JRV-SE-001", token);

    Response r = as(token).get(OBSERVER + "/jrv/" + jrvId + "/cargos");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> cargos = r.jsonPath().getList("$");
    assertThat(cargos).isNotEmpty();
    assertThat(cargos).extracting(cargo -> cargo.get("nombre")).contains("Alcalde/sa");
    assertThat(cargos)
        .allSatisfy(
            cargo -> {
              assertThat(cargo.get("id")).isNotNull();
              assertThat(cargo.get("nivel")).isNotNull();
              boolean registrado = Boolean.TRUE.equals(cargo.get("registrado"));
              if (registrado) {
                assertThat(cargo.get("estadoActa")).as("estado del acta: %s", cargo).isNotNull();
                assertThat(cargo.get("actaId")).as("id del acta: %s", cargo).isNotNull();
              } else {
                assertThat(cargo.get("estadoActa")).as("cargo sin acta: %s", cargo).isNull();
                assertThat(cargo.get("actaId")).as("cargo sin acta: %s", cargo).isNull();
              }
            });
  }

  // -----------------------------------------------------------------------------------------------
  // Registro
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("POST /observer/actas registra el acta en 201 y deja el acta lista para subir su foto")
  void registrarActaDevuelve201YAdmiteLaSubidaDeSuFotografia() {
    String token = observerToken();
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response r = as(token).body(json(body)).post(OBSERVER + "/actas");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    assertThat(r.jsonPath().getLong("id")).isPositive();
    assertThat(r.jsonPath().getLong("jrvId")).isEqualTo(jrvId(combo));
    assertThat(r.jsonPath().getLong("cargoId")).isEqualTo(cargoId(combo));
    assertThat(r.jsonPath().getString("estado")).isEqualTo("ENVIADA");
    assertThat(r.jsonPath().getInt("totalSufragantes"))
        .isEqualTo(((Number) body.get("totalSufragantes")).intValue());
    assertThat(r.jsonPath().getInt("votosBlancos"))
        .isEqualTo(((Number) body.get("votosBlancos")).intValue());
    assertThat(r.jsonPath().getInt("votosNulos"))
        .isEqualTo(((Number) body.get("votosNulos")).intValue());
    assertThat(r.jsonPath().getString("nombreCargo")).isEqualTo(combo.get("cargoNombre"));
    assertThat(r.jsonPath().getString("numeroJrv")).isEqualTo(combo.get("jrvNumero"));
    assertThat(r.jsonPath().getString("fechaRegistro")).isNotBlank();
    assertThat((Object) r.jsonPath().get("registradaPorId")).isNotNull();
    assertThat(r.jsonPath().getString("motivoAnulacion")).isNull();
    long actaId = r.jsonPath().getLong("id");
    registrarActaCreada(actaId, token, body);

    // La fotografía del acta se sube como multipart en la parte "imagen".
    Response foto =
        as(token)
            .contentType("multipart/form-data")
            .multiPart("imagen", "acta.png", FOTO_PNG, "image/png")
            .post(OBSERVER + "/actas/" + actaId + "/imagen");

    assertThat(foto.statusCode()).as("body: %s", foto.asString()).isEqualTo(201);
  }

  @Test
  @DisplayName("POST /observer/actas sobre la misma JRV y cargo devuelve 409")
  void registrarLaMismaJrvYCargoPorSegundaVezDevuelve409() {
    String token = observerToken();
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response primero = as(token).body(json(body)).post(OBSERVER + "/actas");
    assertThat(primero.statusCode()).as("body: %s", primero.asString()).isEqualTo(201);
    registrarActaCreada(primero.jsonPath().getLong("id"), token, body);

    Response segundo = as(token).body(json(body)).post(OBSERVER + "/actas");

    assertErrorShape(segundo, 409);
    assertThat(segundo.jsonPath().getString("message")).contains("Ya existe un acta activa");
  }

  // -----------------------------------------------------------------------------------------------
  // Borrador, envío y corrección
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("POST /observer/actas/borrador guarda el borrador en 200 sin validar la integridad electoral")
  void guardarBorradorDevuelve200SinValidarLaIntegridadElectoral() {
    String token = observerToken();
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    // Los votos NO cuadran con el total de sufragantes: el borrador tiene que aceptarlo igual.
    // Se registra un solo candidato para dejarle lugar a la limpieza del final del test.
    Map<String, Object> body =
        bodyDeActa(jrvId(combo), cargoId(combo), candidatos.subList(0, 1), false);

    Response r = as(token).body(json(body)).post(OBSERVER + "/actas/borrador");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getLong("id")).isPositive();
    assertThat(r.jsonPath().getString("estado")).isEqualTo("BORRADOR");
    assertThat(r.jsonPath().getLong("jrvId")).isEqualTo(jrvId(combo));
    assertThat(r.jsonPath().getLong("cargoId")).isEqualTo(cargoId(combo));
    assertThat(r.jsonPath().getString("numeroJrv")).isEqualTo(combo.get("jrvNumero"));
    assertThat(r.jsonPath().getString("nombreCargo")).isEqualTo(combo.get("cargoNombre"));
    registrarActaCreada(r.jsonPath().getLong("id"), token, body);
  }

  @Test
  @DisplayName("POST /observer/actas/borrador de nuevo reanuda el borrador ya guardado")
  void guardarBorradorDeNuevoReanudaElMismoBorrador() {
    String token = observerToken();
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response primero = as(token).body(json(body)).post(OBSERVER + "/actas/borrador");
    assertThat(primero.statusCode()).as("body: %s", primero.asString()).isEqualTo(200);
    long actaId = primero.jsonPath().getLong("id");
    registrarActaCreada(actaId, token, body);

    Response segundo = as(token).body(json(body)).post(OBSERVER + "/actas/borrador");

    assertThat(segundo.statusCode()).as("body: %s", segundo.asString()).isEqualTo(200);
    assertThat(segundo.jsonPath().getLong("id")).isEqualTo(actaId);
    assertThat(segundo.jsonPath().getString("estado")).isEqualTo("BORRADOR");
  }

  @Test
  @DisplayName("POST /observer/actas/{actaId}/enviar lleva el acta de BORRADOR a ENVIADA")
  void enviarBorradorTransicionaElActaDeBorradorAEnviada() {
    String token = observerToken();
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response borrador = as(token).body(json(body)).post(OBSERVER + "/actas/borrador");
    assertThat(borrador.statusCode()).as("body: %s", borrador.asString()).isEqualTo(200);
    long actaId = borrador.jsonPath().getLong("id");
    registrarActaCreada(actaId, token, body);

    Response r = as(token).post(OBSERVER + "/actas/" + actaId + "/enviar");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getLong("id")).isEqualTo(actaId);
    assertThat(r.jsonPath().getString("estado")).isEqualTo("ENVIADA");
  }

  @Test
  @DisplayName("PUT /observer/actas/{actaId}/corregir corrige un acta en disputa y la deja CORREGIDA")
  void corregirUnActaEnDisputaLaPasaACorregida() {
    String token = observerToken();
    String coordinator = tokenFor("COORDINATOR");
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response registro = as(token).body(json(body)).post(OBSERVER + "/actas");
    assertThat(registro.statusCode()).as("body: %s", registro.asString()).isEqualTo(201);
    long actaId = registro.jsonPath().getLong("id");
    registrarActaCreada(actaId, token, body);

    // La disputa la abre el COORDINATOR (endpoint del módulo coordinator, fuera del alcance de este
    // archivo): sólo sirve como precondición del acta en disputa que el observer va a corregir.
    as(coordinator)
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + actaId + "/disputar");
    assertThat(estadoEnMisActas(token, actaId))
        .as("el acta tiene que estar en disputa antes de corregirla")
        .isEqualTo("EN_DISPUTA");

    Response r = as(token).body(json(body)).put(OBSERVER + "/actas/" + actaId + "/corregir");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getLong("id")).isEqualTo(actaId);
    assertThat(r.jsonPath().getString("estado")).isEqualTo("CORREGIDA");
  }

  // -----------------------------------------------------------------------------------------------
  // Consultas del observer
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /observer/mis-actas devuelve las actas del observer autenticado y solo de él")
  void misActasDevuelveLasActasDelObserverAutenticado() {
    String token = observerToken();
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response registro = as(token).body(json(body)).post(OBSERVER + "/actas");
    assertThat(registro.statusCode()).as("body: %s", registro.asString()).isEqualTo(201);
    long actaId = registro.jsonPath().getLong("id");
    long registradaPor = registro.jsonPath().getLong("registradaPorId");
    registrarActaCreada(actaId, token, body);

    Response r = as(token).get(OBSERVER + "/mis-actas");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> actas = r.jsonPath().getList("$");
    assertThat(actas).extracting(acta -> ((Number) acta.get("id")).longValue()).contains(actaId);
    assertThat(actas)
        .allSatisfy(
            acta ->
                assertThat(((Number) acta.get("registradaPorId")).longValue())
                    .as("mis-actas sólo puede traer actas del observer autenticado: %s", acta)
                    .isEqualTo(registradaPor));
  }

  @Test
  @DisplayName("GET /observer/correcciones-pendientes devuelve las actas en disputa del observer")
  void correccionesPendientesDevuelveLasActasEnDisputaDelObserver() {
    String token = observerToken();
    String coordinator = tokenFor("COORDINATOR");
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response registro = as(token).body(json(body)).post(OBSERVER + "/actas");
    assertThat(registro.statusCode()).as("body: %s", registro.asString()).isEqualTo(201);
    long actaId = registro.jsonPath().getLong("id");
    registrarActaCreada(actaId, token, body);

    // Precondición: el COORDINATOR marca el acta en disputa.
    as(coordinator)
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + actaId + "/disputar");
    assertThat(estadoEnMisActas(token, actaId))
        .as("el acta tiene que estar en disputa para aparecer en correcciones-pendientes")
        .isEqualTo("EN_DISPUTA");

    Response r = as(token).get(OBSERVER + "/correcciones-pendientes");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> pendientes = r.jsonPath().getList("$");
    assertThat(pendientes).extracting(acta -> ((Number) acta.get("id")).longValue()).contains(actaId);
    assertThat(pendientes)
        .allSatisfy(
            acta ->
                assertThat(acta.get("estado"))
                    .as("correcciones-pendientes sólo trae actas en disputa: %s", acta)
                    .isEqualTo("EN_DISPUTA"));
  }

  @Test
  @DisplayName("GET /observer/actas/{actaId} muestra el detalle de mi acta y se lo niega a otro observer")
  void elDetalleDeMiActaEsVisibleParaMiYNoParaOtroObserver() {
    String token = observerToken();
    String otroToken = observerToken();
    Map<String, Object> combo = comboLibre(token);
    List<Long> candidatos = candidatosDe(cargoId(combo), token);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response registro = as(token).body(json(body)).post(OBSERVER + "/actas");
    assertThat(registro.statusCode()).as("body: %s", registro.asString()).isEqualTo(201);
    long actaId = registro.jsonPath().getLong("id");
    registrarActaCreada(actaId, token, body);

    Response detalle = as(token).get(OBSERVER + "/actas/" + actaId);

    assertThat(detalle.statusCode()).as("body: %s", detalle.asString()).isEqualTo(200);
    assertThat(detalle.jsonPath().getLong("id")).isEqualTo(actaId);
    assertThat(detalle.jsonPath().getLong("jrvId")).isEqualTo(jrvId(combo));
    assertThat(detalle.jsonPath().getLong("cargoId")).isEqualTo(cargoId(combo));
    assertThat(detalle.jsonPath().getString("estado")).isEqualTo("ENVIADA");
    assertThat(detalle.jsonPath().getString("numeroJrv")).isEqualTo(combo.get("jrvNumero"));
    assertThat(detalle.jsonPath().getString("nombreCargo")).isEqualTo(combo.get("cargoNombre"));
    assertThat(detalle.jsonPath().getList("detalles", Map.class))
        .hasSize(candidatos.size())
        .allSatisfy(
            detalleCandidato -> {
              assertThat(detalleCandidato.get("candidatoId")).isNotNull();
              assertThat(((Number) detalleCandidato.get("votos")).intValue()).isGreaterThanOrEqualTo(0);
            });
    assertThat(detalle.jsonPath().getLong("totalVotosCandidatos"))
        .isEqualTo(sumaDeVotos(candidatos.size()));

    // Aislamiento: un observer que no registró el acta no puede verla.
    Response ajeno = as(otroToken).get(OBSERVER + "/actas/" + actaId);

    assertErrorShape(ajeno, 403);
    assertThat(ajeno.jsonPath().getString("message"))
        .isEqualTo("Solo puedes consultar tus propias actas");
  }

  // -----------------------------------------------------------------------------------------------
  // Incidencias y errores
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("POST /observer/incidencias registra la incidencia operativa de la JRV con 201")
  void registrarIncidenciaDevuelve201() {
    String token = observerToken();
    Map<String, Object> body =
        Map.of(
            "jrvId", jrvIdDe("JRV-SE-001", token),
            "tipo", "JRV_NO_INSTALADA",
            "descripcion", unique("incidencia de ensayo"));

    Response r = as(token).body(json(body)).post(OBSERVER + "/incidencias");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    assertThat(r.jsonPath().getLong("id")).isPositive();
    assertThat(r.jsonPath().getLong("jrvId")).isEqualTo(((Number) body.get("jrvId")).longValue());
    assertThat(r.jsonPath().getString("tipo")).isEqualTo("JRV_NO_INSTALADA");
    assertThat(r.jsonPath().getString("descripcion")).isEqualTo(body.get("descripcion"));
    assertThat((Object) r.jsonPath().get("reportadaPorId")).isNotNull();
    assertThat(r.jsonPath().getString("fechaRegistro")).isNotBlank();
  }

  @Test
  @DisplayName("sin token, los endpoints del observer devuelven 401")
  void sinTokenLosEndpointsDelObserverDevuelven401() {
    Response jrvs = anon().get(OBSERVER + "/jrvs");
    assertErrorShape(jrvs, 401);

    Response misActas = anon().get(OBSERVER + "/mis-actas");
    assertErrorShape(misActas, 401);

    Response actas = anon().body(json(Map.of("jrvId", 1))).post(OBSERVER + "/actas");
    assertErrorShape(actas, 401);
  }

  @Test
  @DisplayName("POST /observer/actas con body inválido devuelve 400 con los campos que fallaron")
  void registrarActaConBodyInvalidoDevuelve400ConLosCamposQueFallan() {
    String token = observerToken();

    Response r = as(token).body(json(Map.of("jrvId", 1))).post(OBSERVER + "/actas");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).isEqualTo("validation failed");
    assertThat(r.jsonPath().getMap("fields"))
        .containsKeys("cargoId", "totalSufragantes", "votosBlancos", "votosNulos", "detalles");
  }

  @Test
  @DisplayName("GET /observer/actas/{actaId} sobre un acta inexistente devuelve 400 con el mensaje de dominio")
  void consultarUnActaInexistenteDevuelve400() {
    String token = observerToken();

    Response r = as(token).get(OBSERVER + "/actas/999999999");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).contains("Acta no encontrada");
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers del módulo
  // -----------------------------------------------------------------------------------------------

  /** Estado que el observer ve en su propia acta. */
  private static String estadoEnMisActas(String token, long actaId) {
    for (Map<String, Object> acta : listar(as(token).get(OBSERVER + "/mis-actas"))) {
      if (((Number) acta.get("id")).longValue() == actaId) {
        return (String) acta.get("estado");
      }
    }
    return null;
  }

}
