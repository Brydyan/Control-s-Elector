package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del flujo COORDINATOR: bandeja de actas, detalle, historial, disputa, corrección,
 * aprobación, anulación, evidencia de contingencia, incidencias y expediente PDF.
 *
 * <p>Es la mitad "del que coordina" del control electoral. El COORDINATOR revisa el acta que
 * registró el OBSERVER, la disputa si hay una inconsistencia, aplica la corrección, la aprueba y
 * deja constancia de cada transición. El MASTER es el único que puede anular un acta APROBADA.
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code GET /api/v1/coordinator/actas} → 200 con una página: {@code data} (items) + {@code
 *       meta} ({@code page}, {@code size}, {@code totalElements}, {@code totalPages}). Filtra por
 *       {@code estado}, {@code jrvId} y {@code search}.
 *   <li>{@code GET /api/v1/coordinator/actas/{actaId}} → 200 con el detalle completo del acta
 *       (votos por candidato, evidencia y motivo de disputa).
 *   <li>{@code GET /api/v1/coordinator/actas/{actaId}/historial} → 200 con las transiciones
 *       auditadas, de la más reciente a la más antigua.
 *   <li>{@code PUT /api/v1/coordinator/actas/{actaId}/disputar} → 200 con el acta en {@code
 *       EN_DISPUTA}. Repetir la disputa sobre un acta que ya está en disputa → 409.
 *   <li>{@code PUT /api/v1/coordinator/actas/{actaId}/corregir} → 200 con el acta en {@code
 *       CORREGIDA}. Es la corrección aplicada por el COORDINATOR, distinta de la del OBSERVER.
 *   <li>{@code PUT /api/v1/coordinator/actas/{actaId}/aprobar} → 200 con el acta en {@code
 *       APROBADA}. Aprobar un acta que ya está APROBADA → 409.
 *   <li>{@code DELETE /api/v1/coordinator/actas/{actaId}} → 204. Solo MASTER: con token de
 *       COORDINATOR devuelve 403 y deja el acta intacta.
 *   <li>{@code POST /api/v1/coordinator/actas/{actaId}/imagen} → 201 al subir la fotografía de
 *       contingencia (multipart, parte {@code imagen}).
 *   <li>{@code GET /api/v1/coordinator/incidencias} → 200 con el listado de incidencias. Lo pueden
 *       leer COORDINATOR, MASTER y ADMIN.
 *   <li>{@code GET /api/v1/coordinator/actas/{actaId}/expediente-pdf} → 200 con el PDF del
 *       expediente de impugnación ({@code application/pdf}).
 * </ul>
 *
 * <p><b>La máquina de estados es el corazón de este archivo.</b> El ciclo completo se recorre sobre
 * un acta que el OBSERVER registra: {@code ENVIADA} → (COORDINATOR disputa) {@code EN_DISPUTA} →
 * (COORDINATOR corrige) {@code CORREGIDA} → (COORDINATOR aprueba) {@code APROBADA} → (MASTER anula)
 * {@code ANULADA}. Cada paso afirma el {@code estado} observable. Una transición que no se puede
 * ejecutar desde el estado actual devuelve 409 ({@code ActaEstadoInvalidoException}).
 *
 * <p><b>Sobre los datos.</b> La unicidad de un acta es (JRV, cargo), así que cada test que registra
 * un acta reserva un par distinto: {@link #comboLibre(String)} busca el primero sin acta activa que
 * además no haya sido tomado por otro test de esta corrida. Al terminar, cada test devuelve sus
 * actas a {@code ANULADA} (el único estado que no bloquea el par JRV + cargo), de modo que la suite
 * se puede re-ejecutar sobre la misma base sin limpiar nada a mano.
 *
 * <p><b>Desvío conocido entre el controller y el backend.</b> {@code POST
 * /api/v1/coordinator/actas/{actaId}/imagen} documenta 201, pero el backend responde 500: la fila
 * de auditoría de contingencia escribe la acción {@code EVIDENCIA_CONTINGENCIA_COORDINADOR} (34
 * caracteres) en {@code audit_log.accion}, que es {@code VARCHAR(32)}. El test afirma el contrato
 * documentado (201) y por lo tanto queda en rojo hasta que el backend se corrija.
 */
class CoordinatorActasContractIT extends ContractTestSupport {

  /** Fotografía mínima: al backend le importa el Content-Type {@code image/*}, no los bytes. */
  private static final byte[] FOTO_PNG = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'
  };

  /** Acta de prueba con el par (JRV, cargo) reservado y el body con el que se registró. */
  private record ActaDePrueba(long id, long jrvId, long cargoId, Map<String, Object> body) {}

  // -----------------------------------------------------------------------------------------------
  // Bandeja de actas
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /coordinator/actas devuelve una página con items y metadatos de paginación")
  void listarActasDevuelveUnaPaginaConItemsYMeta() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());

    Response r = as(coordinator).get(COORDINATOR + "/actas");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> items = r.jsonPath().getList("data");
    assertThat(items).isNotNull();
    // El acta recién registrada es la más reciente: el listado la trae en la primera página.
    assertThat(items)
        .extracting(item -> ((Number) item.get("id")).longValue())
        .contains(acta.id());
    assertThat(items)
        .allSatisfy(
            item -> {
              assertThat(item.get("id")).isNotNull();
              assertThat(item.get("estado")).asString().isNotBlank();
              assertThat(item.get("jrvId")).isNotNull();
              assertThat(item.get("cargoId")).isNotNull();
              assertThat(item.get("numeroJrv")).asString().startsWith("JRV-");
              assertThat(item.get("nombreCargo")).isNotNull();
            });

    // La página viene envuelta en data + meta: el listing no es una lista pelada.
    assertThat(r.jsonPath().getInt("meta.page")).isZero();
    assertThat(r.jsonPath().getInt("meta.size")).isEqualTo(20);
    assertThat(r.jsonPath().getLong("meta.totalElements")).isGreaterThanOrEqualTo(1);
    assertThat(r.jsonPath().getInt("meta.totalPages"))
        .isEqualTo((int) Math.ceil(r.jsonPath().getLong("meta.totalElements") / 20.0));
  }

  @Test
  @DisplayName("GET /coordinator/actas filtra por estado y por búsqueda libre")
  void listarActasFiltraPorEstadoYPorBusquedaLibre() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());

    Response porBusqueda =
        as(coordinator).get(COORDINATOR + "/actas?search=" + acta.id() + "&estado=ENVIADA");

    assertThat(porBusqueda.statusCode()).as("body: %s", porBusqueda.asString()).isEqualTo(200);
    List<Map<String, Object>> encontrados = porBusqueda.jsonPath().getList("data");
    assertThat(encontrados)
        .extracting(item -> ((Number) item.get("id")).longValue())
        .contains(acta.id());
    assertThat(encontrados)
        .allSatisfy(
            item ->
                assertThat(item.get("estado"))
                    .as("el filtro por estado sólo puede traer actas ENVIADA: %s", item)
                    .isEqualTo("ENVIADA"));

    // El mismo acta no está APROBADA: el filtro de estado discrimina de verdad.
    Response porEstado =
        as(coordinator).get(COORDINATOR + "/actas?search=" + acta.id() + "&estado=APROBADA");

    assertThat(porEstado.statusCode()).as("body: %s", porEstado.asString()).isEqualTo(200);
    List<Map<String, Object>> noAprobadas = porEstado.jsonPath().getList("data");
    assertThat(noAprobadas)
        .extracting(item -> ((Number) item.get("id")).longValue())
        .doesNotContain(acta.id());
  }

  // -----------------------------------------------------------------------------------------------
  // Detalle e historial
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /coordinator/actas/{actaId} devuelve el detalle completo del acta")
  void detalleDeActaDevuelveElDetalleCompleto() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());

    Response r = as(coordinator).get(COORDINATOR + "/actas/" + acta.id());

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getLong("id")).isEqualTo(acta.id());
    assertThat(r.jsonPath().getLong("jrvId")).isEqualTo(acta.jrvId());
    assertThat(r.jsonPath().getLong("cargoId")).isEqualTo(acta.cargoId());
    assertThat(r.jsonPath().getString("estado")).isEqualTo("ENVIADA");
    assertThat(r.jsonPath().getString("numeroJrv")).isNotBlank();
    assertThat(r.jsonPath().getString("nombreCargo")).isNotBlank();
    assertThat(r.jsonPath().getString("fechaRegistro")).isNotBlank();
    assertThat((Object) r.jsonPath().get("registradaPorId")).isNotNull();
    assertThat(r.jsonPath().getString("motivoAnulacion")).isNull();
    assertThat(r.jsonPath().getList("detalles", Map.class))
        .isNotEmpty()
        .allSatisfy(
            detalle -> {
              assertThat(detalle.get("candidatoId")).isNotNull();
              assertThat(((Number) detalle.get("votos")).intValue()).isGreaterThanOrEqualTo(0);
            });
    assertThat(r.jsonPath().getLong("totalVotosCandidatos"))
        .isEqualTo(sumaDeVotos(r.jsonPath().getList("detalles", Map.class).size()));
    // Sin evidencia y sin disputa: esos campos vienen en null, no con valores inventados.
    assertThat((Object) r.jsonPath().get("evidenciaPublicId")).isNull();
    assertThat(r.jsonPath().getString("motivoDisputa")).isNull();
  }

  @Test
  @DisplayName("GET /coordinator/actas/{actaId}/historial devuelve las transiciones auditadas")
  void historialDeActaDevuelveLasTransicionesAuditadas() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());

    // Tres transiciones del módulo coordinator: disputa, corrección y aprobación.
    as(coordinator)
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");
    as(coordinator).body(json(acta.body())).put(COORDINATOR + "/actas/" + acta.id() + "/corregir");
    as(coordinator).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");

    Response r = as(coordinator).get(COORDINATOR + "/actas/" + acta.id() + "/historial");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> entradas = r.jsonPath().getList("$");
    assertThat(entradas)
        .extracting(entrada -> entrada.get("accion"))
        .contains("REGISTRO_ACTA", "ACTA_EN_DISPUTA", "ACTA_CORREGIDA", "ACTA_APROBADA");
    assertThat(entradas)
        .allSatisfy(
            entrada -> {
              assertThat(entrada.get("id")).isNotNull();
              assertThat(entrada.get("accion")).asString().isNotBlank();
              assertThat(entrada.get("timestamp")).asString().isNotBlank();
              assertThat(entrada.get("registradoPorNombre")).asString().isNotBlank();
              assertThat(entrada.get("registradoPorRol")).asString().isNotBlank();
            });

    // La disputa la hizo el coordinator: el historial dice quién y desde qué rol.
    Map<String, Object> disputa =
        entradas.stream()
            .filter(entrada -> "ACTA_EN_DISPUTA".equals(entrada.get("accion")))
            .findFirst()
            .orElseThrow();
    assertThat(disputa.get("registradoPorRol")).isEqualTo("COORDINATOR");

    // Orden de más reciente a más antiguo.
    List<String> marcas = entradas.stream().map(e -> String.valueOf(e.get("timestamp"))).toList();
    assertThat(marcas).isSortedAccordingTo((a, b) -> b.compareTo(a));
  }

  // -----------------------------------------------------------------------------------------------
  // Transiciones de la máquina de estados
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("PUT /coordinator/actas/{actaId}/disputar lleva el acta de ENVIADA a EN_DISPUTA")
  void disputarActaEnviadaLaPasaAEnDisputa() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());
    String motivo = unique("motivo de disputa");

    Response r =
        as(coordinator)
            .body(json(Map.of("motivo", motivo)))
            .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getLong("id")).isEqualTo(acta.id());
    assertThat(r.jsonPath().getString("estado")).isEqualTo("EN_DISPUTA");
    assertThat(estadoEnCoordinator(coordinator, acta.id())).isEqualTo("EN_DISPUTA");

    // El detalle expone el motivo por el que se abrió la disputa.
    Response detalle = as(coordinator).get(COORDINATOR + "/actas/" + acta.id());
    assertThat(detalle.statusCode()).as("body: %s", detalle.asString()).isEqualTo(200);
    assertThat(detalle.jsonPath().getString("motivoDisputa")).contains(motivo);
  }

  @Test
  @DisplayName("PUT /coordinator/actas/{actaId}/corregir aplica la corrección del coordinator")
  void corregirActaEnDisputaLaPasaACorregida() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());
    as(coordinator)
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    Response r =
        as(coordinator)
            .body(json(acta.body()))
            .put(COORDINATOR + "/actas/" + acta.id() + "/corregir");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getLong("id")).isEqualTo(acta.id());
    assertThat(r.jsonPath().getString("estado")).isEqualTo("CORREGIDA");
    assertThat(r.jsonPath().getInt("totalSufragantes"))
        .isEqualTo(((Number) acta.body().get("totalSufragantes")).intValue());
    assertThat(r.jsonPath().getInt("votosBlancos"))
        .isEqualTo(((Number) acta.body().get("votosBlancos")).intValue());
    assertThat(r.jsonPath().getInt("votosNulos"))
        .isEqualTo(((Number) acta.body().get("votosNulos")).intValue());
    assertThat(estadoEnCoordinator(coordinator, acta.id())).isEqualTo("CORREGIDA");
  }

  @Test
  @DisplayName("PUT /coordinator/actas/{actaId}/aprobar lleva el acta a APROBADA")
  void aprobarActaLaPasaAAprobada() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());

    Response r = as(coordinator).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getLong("id")).isEqualTo(acta.id());
    assertThat(r.jsonPath().getString("estado")).isEqualTo("APROBADA");
    assertThat(estadoEnCoordinator(coordinator, acta.id())).isEqualTo("APROBADA");
  }

  @Test
  @DisplayName("PUT /coordinator/actas/{actaId}/disputar sobre un acta ya en disputa devuelve 409")
  void disputarActaQueYaEstaEnDisputaDevuelve409() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());
    as(coordinator)
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    Response r =
        as(coordinator)
            .body(json(Map.of("motivo", unique("motivo de disputa repetido"))))
            .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    assertErrorShape(r, 409);
    assertThat(r.jsonPath().getString("message")).contains("No se puede marcar en disputa");
    // El rechazo no mueve el acta: sigue en disputa, no en un estado imposible.
    assertThat(estadoEnCoordinator(coordinator, acta.id())).isEqualTo("EN_DISPUTA");
  }

  @Test
  @DisplayName("PUT /coordinator/actas/{actaId}/aprobar sobre un acta ya aprobada devuelve 409")
  void aprobarActaQueYaEstaAprobadaDevuelve409() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());
    as(coordinator).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");

    Response r = as(coordinator).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");

    assertErrorShape(r, 409);
    assertThat(r.jsonPath().getString("message")).contains("Solo se pueden aprobar actas");
    assertThat(estadoEnCoordinator(coordinator, acta.id())).isEqualTo("APROBADA");
  }

  // -----------------------------------------------------------------------------------------------
  // Anulación (solo MASTER)
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("DELETE /coordinator/actas/{actaId} lo anula el MASTER y se lo niega al COORDINATOR")
  void anularActaAprobadaSoloEsDelMaster() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());
    as(coordinator).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");
    String motivo = unique("motivo de anulacion");

    // Un COORDINATOR no anula: el endpoint exige el permiso acta:anular, que es del MASTER.
    Response negada =
        as(coordinator)
            .body(json(Map.of("motivo", motivo)))
            .delete(COORDINATOR + "/actas/" + acta.id());

    assertErrorShape(negada, 403);
    assertThat(estadoEnCoordinator(coordinator, acta.id()))
        .as("un 403 no puede cambiar el estado del acta")
        .isEqualTo("APROBADA");

    // El MASTER anula y el acta queda en ANULADA con su motivo.
    Response anulada =
        as(masterToken())
            .body(json(Map.of("motivo", motivo)))
            .delete(COORDINATOR + "/actas/" + acta.id());

    assertThat(anulada.statusCode()).as("body: %s", anulada.asString()).isEqualTo(204);
    assertThat(estadoEnCoordinator(coordinator, acta.id())).isEqualTo("ANULADA");
    Response detalle = as(coordinator).get(COORDINATOR + "/actas/" + acta.id());
    assertThat(detalle.jsonPath().getString("motivoAnulacion")).isEqualTo(motivo);
  }

  // -----------------------------------------------------------------------------------------------
  // Evidencia de contingencia
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("POST /coordinator/actas/{actaId}/imagen sube la fotografía de contingencia")
  void subirImagenDelActaComoCoordinatorDevuelve201() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());

    Response r =
        as(coordinator)
            .contentType("multipart/form-data")
            .multiPart("imagen", "acta.png", FOTO_PNG, "image/png")
            .post(COORDINATOR + "/actas/" + acta.id() + "/imagen");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
  }

  // -----------------------------------------------------------------------------------------------
  // Incidencias
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /coordinator/incidencias devuelve el listado de incidencias al COORDINATOR")
  void listarIncidenciasComoCoordinatorDevuelveElListado() {
    String coordinator = tokenFor("COORDINATOR");

    Response r = as(coordinator).get(COORDINATOR + "/incidencias");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> incidencias = r.jsonPath().getList("$");
    assertThat(incidencias).isNotNull();
    assertThat(incidencias)
        .allSatisfy(
            incidencia -> {
              assertThat(incidencia.get("id")).isNotNull();
              assertThat(incidencia.get("jrvId")).isNotNull();
              assertThat(incidencia.get("tipo")).asString().isNotBlank();
              assertThat(incidencia.get("descripcion")).asString().isNotBlank();
              assertThat((Object) incidencia.get("reportadaPorId")).isNotNull();
              assertThat(incidencia.get("fechaRegistro")).asString().isNotBlank();
            });
  }

  @Test
  @DisplayName("GET /coordinator/incidencias también lo lee el ADMIN, que es solo lectura")
  void listarIncidenciasComoAdminDevuelveElListado() {
    String admin = tokenFor("ADMIN");

    Response r = as(admin).get(COORDINATOR + "/incidencias");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getList("$")).isNotNull();
  }

  // -----------------------------------------------------------------------------------------------
  // Expediente PDF
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /coordinator/actas/{actaId}/expediente-pdf devuelve un PDF del expediente")
  void expedientePdfDevuelveUnDocumentoPdf() {
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observerToken());

    Response r = as(coordinator).get(COORDINATOR + "/actas/" + acta.id() + "/expediente-pdf");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.getContentType()).as("content-type: %s", r.getContentType()).contains("application/pdf");
    assertThat(r.header("Content-Disposition")).contains("expediente-acta-" + acta.id() + ".pdf");
    byte[] bytes = r.asByteArray();
    assertThat(bytes).isNotEmpty();
    assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
  }

  // -----------------------------------------------------------------------------------------------
  // Máquina de estados completa
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("El acta recorre la máquina de estados completa hasta quedar ANULADA")
  void maquinaDeEstadosDelActaRecorreElCicloCompleto() {
    String observer = observerToken();
    String coordinator = tokenFor("COORDINATOR");
    ActaDePrueba acta = registrarActa(observer);

    // 1. El OBSERVER registra el acta: queda ENVIADA.
    assertThat(estadoEnCoordinator(coordinator, acta.id())).isEqualTo("ENVIADA");

    // 2. El COORDINATOR la disputa: queda EN_DISPUTA.
    Response disputa =
        as(coordinator)
            .body(json(Map.of("motivo", unique("motivo de disputa"))))
            .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");
    assertThat(disputa.statusCode()).as("body: %s", disputa.asString()).isEqualTo(200);
    assertThat(disputa.jsonPath().getString("estado")).isEqualTo("EN_DISPUTA");

    // 3. El COORDINATOR la corrige: queda CORREGIDA.
    Response correccion =
        as(coordinator)
            .body(json(acta.body()))
            .put(COORDINATOR + "/actas/" + acta.id() + "/corregir");
    assertThat(correccion.statusCode()).as("body: %s", correccion.asString()).isEqualTo(200);
    assertThat(correccion.jsonPath().getString("estado")).isEqualTo("CORREGIDA");

    // 4. El COORDINATOR la aprueba: queda APROBADA.
    Response aprobacion = as(coordinator).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");
    assertThat(aprobacion.statusCode()).as("body: %s", aprobacion.asString()).isEqualTo(200);
    assertThat(aprobacion.jsonPath().getString("estado")).isEqualTo("APROBADA");

    // 5. El MASTER la anula: queda ANULADA.
    Response anulacion =
        as(masterToken())
            .body(json(Map.of("motivo", unique("motivo de anulacion"))))
            .delete(COORDINATOR + "/actas/" + acta.id());
    assertThat(anulacion.statusCode()).as("body: %s", anulacion.asString()).isEqualTo(204);
    assertThat(estadoEnCoordinator(coordinator, acta.id())).isEqualTo("ANULADA");
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers del módulo
  // -----------------------------------------------------------------------------------------------

  /**
   * Registra un acta como OBSERVER sobre un par (JRV, cargo) libre y la deja anotada para la
   * limpieza del final del test.
   */
  private static ActaDePrueba registrarActa(String observerToken) {
    Map<String, Object> combo = comboLibre(observerToken);
    List<Long> candidatos = candidatosDe(cargoId(combo), observerToken);
    Map<String, Object> body =
        bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response r = as(observerToken).body(json(body)).post(OBSERVER + "/actas");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    long actaId = r.jsonPath().getLong("id");
    registrarActaCreada(actaId, observerToken, body);
    return new ActaDePrueba(actaId, jrvId(combo), cargoId(combo), body);
  }

  /** Estado del acta tal como lo ve el módulo coordinator. */
  private static String estadoEnCoordinator(String coordinator, long actaId) {
    Response r = as(coordinator).get(COORDINATOR + "/actas/" + actaId);
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    return r.jsonPath().getString("estado");
  }
}
