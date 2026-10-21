package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
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

  /** Prefijo del módulo de observador. */
  private static final String OBSERVER = "/api/v1/observer";

  /** Prefijo del módulo de coordinación, usado sólo como precondición y para liberar actas. */
  private static final String COORDINATOR = "/api/v1/coordinator";

  /** Pares (JRV, cargo) ya reservados por esta corrida. */
  private static final Set<String> COMBOS_RESERVADOS = ConcurrentHashMap.newKeySet();

  /** Actas que el test en curso creó, para devolverlas a ANULADA al terminar. */
  private static final List<ActaCreada> ACTAS_CREADAS = new ArrayList<>();

  /** Acta creada por el test en curso, con lo necesario para poder liberarla. */
  private record ActaCreada(long id, String token, Map<String, Object> body) {}

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
    ACTAS_CREADAS.add(new ActaCreada(actaId, token, body));

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
    ACTAS_CREADAS.add(new ActaCreada(primero.jsonPath().getLong("id"), token, body));

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
    ACTAS_CREADAS.add(new ActaCreada(r.jsonPath().getLong("id"), token, body));
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
    ACTAS_CREADAS.add(new ActaCreada(actaId, token, body));

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
    ACTAS_CREADAS.add(new ActaCreada(actaId, token, body));

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
    ACTAS_CREADAS.add(new ActaCreada(actaId, token, body));

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
    ACTAS_CREADAS.add(new ActaCreada(actaId, token, body));

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
    ACTAS_CREADAS.add(new ActaCreada(actaId, token, body));

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
    ACTAS_CREADAS.add(new ActaCreada(actaId, token, body));

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
  // Limpieza: el par (JRV, cargo) se bloquea mientras el acta no está ANULADA
  // -----------------------------------------------------------------------------------------------

  /**
   * Devuelve a ANULADA las actas que el test creó.
   *
   * <p>Un acta activa bloquea su par (JRV, cargo) para siempre: sin esto la suite se puede correr
   * una sola vez sobre una base dada. La única forma de liberar el par es dejar el acta en {@code
   * ANULADA}, y para llegar ahí hay que recorrer la máquina de estados.
   *
   * <p>No es parte del contrato de este archivo: usa endpoints de otros módulos (coordinator y
   * master) sólo como infraestructura, y no afirma nada sobre sus respuestas.
   */
  @AfterEach
  void liberarActasCreadasPorElTest() {
    for (ActaCreada acta : ACTAS_CREADAS) {
      liberar(acta);
    }
    ACTAS_CREADAS.clear();
  }

  private static void liberar(ActaCreada acta) {
    String estado = estadoDeActa(acta.token(), acta.id());
    if ("BORRADOR".equals(estado)) {
      as(acta.token()).post(OBSERVER + "/actas/" + acta.id() + "/enviar");
      estado = estadoDeActa(acta.token(), acta.id());
      if ("BORRADOR".equals(estado)) {
        // Los votos del borrador no cuadran y no hay forma de enviarlos: se reemplazan por un
        // juego de candidatos disjunto (el backend no admite re-guardar los mismos) y se reintenta.
        reemplazarDetallesDelBorrador(acta);
        as(acta.token()).post(OBSERVER + "/actas/" + acta.id() + "/enviar");
        estado = estadoDeActa(acta.token(), acta.id());
      }
    }
    if ("EN_DISPUTA".equals(estado)) {
      as(acta.token()).body(json(acta.body())).put(OBSERVER + "/actas/" + acta.id() + "/corregir");
      estado = estadoDeActa(acta.token(), acta.id());
    }
    if ("ENVIADA".equals(estado) || "CORREGIDA".equals(estado)) {
      as(tokenFor("COORDINATOR")).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");
      estado = estadoDeActa(acta.token(), acta.id());
    }
    if ("APROBADA".equals(estado)) {
      as(masterToken())
          .body(json(Map.of("motivo", unique("limpieza de la suite de contrato"))))
          .delete(COORDINATOR + "/actas/" + acta.id());
    }
  }

  /**
   * Re-guarda el borrador con los candidatos que todavía no tiene, para que sus votos puedan
   * cuadrar y el acta pueda salir de BORRADOR.
   */
  private static void reemplazarDetallesDelBorrador(ActaCreada acta) {
    Response detalle = as(acta.token()).get(OBSERVER + "/actas/" + acta.id());
    if (detalle.statusCode() != 200) {
      return;
    }
    long cargoId = detalle.jsonPath().getLong("cargoId");
    List<Long> actuales = new ArrayList<>();
    for (Map<String, Object> detalleCandidato : detalle.jsonPath().getList("detalles", Map.class)) {
      actuales.add(((Number) detalleCandidato.get("candidatoId")).longValue());
    }
    List<Long> disponibles = new ArrayList<>();
    for (Long candidato : candidatosDe(cargoId, acta.token())) {
      if (!actuales.contains(candidato)) {
        disponibles.add(candidato);
      }
    }
    if (disponibles.isEmpty()) {
      return;
    }
    List<Map<String, Object>> detalles = new ArrayList<>();
    int suma = 0;
    for (int i = 0; i < disponibles.size(); i++) {
      int votos = 10 * (i + 1);
      suma += votos;
      Map<String, Object> detalleCandidato = new LinkedHashMap<>();
      detalleCandidato.put("candidatoId", disponibles.get(i));
      detalleCandidato.put("votos", votos);
      detalles.add(detalleCandidato);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("jrvId", detalle.jsonPath().getLong("jrvId"));
    body.put("cargoId", cargoId);
    body.put("votosBlancos", 0);
    body.put("votosNulos", 0);
    body.put("totalSufragantes", suma);
    body.put("detalles", detalles);
    as(acta.token()).body(json(body)).post(OBSERVER + "/actas/borrador");
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers de datos
  // -----------------------------------------------------------------------------------------------

  /** Token de un OBSERVER recién creado, exclusivo de un test. */
  private static String observerToken() {
    return login(registerUser("OBSERVER"), TEST_PASSWORD);
  }

  /** ID interno de la JRV de un combo reservado. */
  private static long jrvId(Map<String, Object> combo) {
    return ((Number) combo.get("jrvId")).longValue();
  }

  /** ID interno del cargo de un combo reservado. */
  private static long cargoId(Map<String, Object> combo) {
    return ((Number) combo.get("cargoId")).longValue();
  }

  /** ID interno de la JRV con ese número. */
  private static long jrvIdDe(String numero, String token) {
    for (Map<String, Object> jrv : listar(as(token).get(OBSERVER + "/jrvs"))) {
      if (numero.equals(jrv.get("numero"))) {
        return ((Number) jrv.get("id")).longValue();
      }
    }
    throw new AssertionError("La JRV " + numero + " no existe en el seed del backend");
  }

  /**
   * Reserva el primer par (JRV, cargo) sin acta activa que no haya sido tomado por otro test de
   * esta corrida. Es lo que permite que la suite se re-ejecute sobre la misma base: los pares ya
   * usados quedan ocupados y el test toma el siguiente libre.
   */
  private static Map<String, Object> comboLibre(String token) {
    for (Map<String, Object> jrv : listar(as(token).get(OBSERVER + "/jrvs"))) {
      long jrvId = ((Number) jrv.get("id")).longValue();
      for (Map<String, Object> cargo : listar(as(token).get(OBSERVER + "/jrv/" + jrvId + "/cargos"))) {
        if (Boolean.TRUE.equals(cargo.get("registrado"))) {
          continue;
        }
        long cargoId = ((Number) cargo.get("id")).longValue();
        if (!COMBOS_RESERVADOS.add(jrvId + "-" + cargoId)) {
          continue;
        }
        Map<String, Object> combo = new LinkedHashMap<>();
        combo.put("jrvId", jrvId);
        combo.put("cargoId", cargoId);
        combo.put("cargoNombre", cargo.get("nombre"));
        combo.put("jrvNumero", jrv.get("numero"));
        return combo;
      }
    }
    throw new AssertionError(
        "No queda ningún par JRV+cargo sin acta activa: la base está saturada de actas");
  }

  /** IDs de los candidatos de un cargo. */
  private static List<Long> candidatosDe(long cargoId, String token) {
    List<Long> ids = new ArrayList<>();
    for (Map<String, Object> candidato :
        listar(as(token).get("/api/v1/configuracion/candidatos?cargoId=" + cargoId))) {
      ids.add(((Number) candidato.get("id")).longValue());
    }
    if (ids.isEmpty()) {
      throw new AssertionError("El cargo " + cargoId + " no tiene candidatos en el seed del backend");
    }
    return ids;
  }

  /**
   * Body de un acta. Con {@code cuadrado=false} el total de sufragantes no cierra con la suma de
   * los votos: sirve para fijar que el borrador no valida la integridad electoral.
   */
  private static Map<String, Object> bodyDeActa(
      long jrvId, long cargoId, List<Long> candidatos, boolean cuadrado) {
    List<Map<String, Object>> detalles = new ArrayList<>();
    int suma = 0;
    for (int i = 0; i < candidatos.size(); i++) {
      int votos = 10 * (i + 1);
      suma += votos;
      Map<String, Object> detalle = new LinkedHashMap<>();
      detalle.put("candidatoId", candidatos.get(i));
      detalle.put("votos", votos);
      detalles.add(detalle);
    }
    int blancos = 2;
    int nulos = 1;
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("jrvId", jrvId);
    body.put("cargoId", cargoId);
    body.put("votosBlancos", blancos);
    body.put("votosNulos", nulos);
    body.put("totalSufragantes", cuadrado ? suma + blancos + nulos : suma + blancos + nulos + 999);
    body.put("detalles", detalles);
    return body;
  }

  /** Suma de votos que el body de un acta deja para los candidatos. */
  private static long sumaDeVotos(int cantidadDeCandidatos) {
    long suma = 0;
    for (int i = 0; i < cantidadDeCandidatos; i++) {
      suma += 10L * (i + 1);
    }
    return suma;
  }

  /** Estado que el observer ve en su propia acta. */
  private static String estadoEnMisActas(String token, long actaId) {
    for (Map<String, Object> acta : listar(as(token).get(OBSERVER + "/mis-actas"))) {
      if (((Number) acta.get("id")).longValue() == actaId) {
        return (String) acta.get("estado");
      }
    }
    return null;
  }

  /** Estado de un acta tal como lo ve su autor. */
  private static String estadoDeActa(String token, long actaId) {
    Response r = as(token).get(OBSERVER + "/actas/" + actaId);
    if (r.statusCode() != 200) {
      return null;
    }
    return r.jsonPath().getString("estado");
  }

  /** Lee una respuesta JSON como lista de objetos. */
  private static List<Map<String, Object>> listar(Response r) {
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    return r.jsonPath().getList("$");
  }
}
