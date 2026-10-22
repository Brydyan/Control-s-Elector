package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del módulo de EVIDENCIAS: subida, consulta, descarga, estados, observaciones y borrado
 * de la foto de un acta.
 *
 * <p>La evidencia es el binario de la foto del acta. En la base solo vive su metadata; el archivo
 * va a S3/MinIO y lo referencia una clave. Este archivo fija el contrato HTTP observable de ese
 * módulo —status, forma del body, campos presentes y shape del error— sin tocar nada de cómo lo
 * implementa el backend. La misma suite validará el backend Quarkus de la fase 2 (#113).
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code POST /api/v1/evidencias?actaId={id}} → 201 con la metadata de la evidencia. El
 *       archivo viaja como parte multipart {@code file} y el acta como query param {@code actaId}.
 *       El content-type tiene que ser {@code image/*}: el backend valida el tipo declarado, no los
 *       bytes.
 *   <li>{@code GET /api/v1/evidencias/{id}} → 200 con la metadata (nunca el binario). La evidencia
 *       se identifica por un UUID público, no por el id interno.
 *   <li>{@code GET /api/v1/evidencias/{id}/download-url} → 200 con una URL prefirmada de S3 y su
 *       instante de emisión.
 *   <li>{@code PATCH /api/v1/evidencias/{id}/estado} → 200 con la evidencia en el estado pedido. Solo
 *       se aceptan {@code APROBADA} y {@code RECHAZADA}; cualquier otro destino es 400.
 *   <li>{@code PATCH /api/v1/evidencias/{id}/observaciones} → 200 con la lista de observaciones
 *       marcada. Solo el OBSERVER que subió la evidencia puede marcarlas, y como máximo 2.
 *   <li>{@code DELETE /api/v1/evidencias/{id}} → 204. La evidencia deja de existir: un GET posterior
 *       da 404.
 * </ul>
 *
 * <p><b>Permisos (semilla RBAC del backend).</b> {@code evidencia:upload} y {@code evidencia:read}
 * los tienen OBSERVER, MASTER y (solo lectura) ADMIN; {@code evidencia:download} y {@code
 * evidencia:read} los tienen los cuatro roles; {@code evidencia:delete} es exclusivo de MASTER;
 * {@code evidencia:update} (observaciones) es de OBSERVER, COORDINATOR y MASTER. ADMIN es solo
 * lectura: nunca sube, nunca borra y nunca transiciona.
 *
 * <p><b>Sobre el fixture binario.</b> El backend valida el content-type, no los bytes, pero el
 * generador del expediente PDF decodifica la imagen: con bytes inválidos revienta con 500. Por eso
 * la suite sube un PNG real de 1x1 (70 bytes) y verifica en {@link #verificarFixturePng()} que los
 * bytes son efectivamente un PNG, en vez de confiar en un base64 copiado a ciegas.
 *
 * <p><b>Defecto conocido del backend Spring (reportado en T7, sin maquillar).</b> Subir una
 * evidencia sin el param {@code actaId} debería devolver 400 con el shape estándar y hoy devuelve
 * <b>401</b>: el {@code GlobalExceptionHandler} declara el handler de la parte multipart faltante
 * también para el parámetro faltante, pero su parámetro es de un solo tipo, así que Spring no
 * puede resolverlo, cae en el {@code DefaultHandlerExceptionResolver}, y el {@code sendError} dispara
 * un dispatch a {@code /error} que el {@code SecurityConfig} no tiene en {@code permitAll}. El test
 * afirma el contrato (400), no el síntoma, y por lo tanto queda en rojo hasta que el backend se
 * corrija.
 *
 * <p>El arreglo es de backend y son dos handlers en vez de uno (en
 * {@code ec.twintailcs.electoral.shared.web.GlobalExceptionHandler}):
 *
 * <pre>{@code
 * @ExceptionHandler(MissingServletRequestPartException.class)
 * public ResponseEntity<Map<String, Object>> missingPart(MissingServletRequestPartException ex) {
 *   return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
 * }
 *
 * @ExceptionHandler(MissingServletRequestParameterException.class)
 * public ResponseEntity<Map<String, Object>> missingParam(MissingServletRequestParameterException ex) {
 *   return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
 * }
 * }</pre>
 *
 * <p>Con eso el caso responde 400 con el shape estándar y el backend Quarkus de la fase 2 no tiene
 * que reproducir un artefacto de Spring que no le corresponde.
 */
class EvidenciasContractIT extends ContractTestSupport {

  /** Prefijo del módulo de evidencias. */
  private static final String EVIDENCIAS = "/api/v1/evidencias";

  /**
   * PNG de 1x1 píxel transparente, 70 bytes, verificado byte a byte: firma PNG, IHDR 1x1, CRCs
   * correctos y un IDAT que infla. Es el fixture más chico que ImageIO (y por lo tanto OpenPDF)
   * puede decodificar.
   */
  private static final String PNG_1X1_BASE64 =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNgYGBgAAAABQABeqhXUAAAAABJRU5ErkJggg==";

  /** El PNG decodificado del base64 de arriba. */
  private static final byte[] PNG_1X1 = Base64.getDecoder().decode(PNG_1X1_BASE64);

  /** Acta de prueba con el par (JRV, cargo) reservado y el body con el que se registró. */
  private record ActaDePrueba(long id, long jrvId, long cargoId, Map<String, Object> body) {}

  /** Evidencia subida por el test: el UUID público con el que se la consulta y la clave en S3. */
  private record EvidenciaSubida(String id, String s3Key) {}

  /** Evidencias que este test subió y que hay que borrar al terminar. */
  private final List<String> evidenciasCreadas = new ArrayList<>();

  @BeforeAll
  static void verificarFixturePng() {
    // El fixture se autoverifica: si el base64 se corrompe, el fallo dice acá y no en un 500
    // raro del generador de PDF del expediente.
    assertThat(PNG_1X1).as("el fixture debe tener bytes").isNotEmpty();
    assertThat(PNG_1X1[0] & 0xFF).as("firma PNG (0x89)").isEqualTo(0x89);
    assertThat(new String(PNG_1X1, 1, 3, StandardCharsets.US_ASCII)).isEqualTo("PNG");
    assertThat(PNG_1X1.length).as("tamaño del PNG de 1x1").isEqualTo(70);
  }

  // -----------------------------------------------------------------------------------------------
  // Subida
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("POST /evidencias sube la foto de un acta y devuelve la metadata de la evidencia")
  void subirEvidenciaValidaDevuelve201ConLaMetadata() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response r =
        subirEvidencia(observer, "?actaId=" + acta.id(), "evidencia.png", "image/png", PNG_1X1);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    String id = r.jsonPath().getString("id");
    assertThat(id).as("la evidencia se identifica por un UUID publico: %s", r.asString()).isNotBlank();
    assertThat(UUID.fromString(id)).isNotNull();
    evidenciasCreadas.add(id);
    assertThat(r.jsonPath().getLong("jrvId")).isEqualTo(acta.jrvId());
    assertThat(r.jsonPath().getLong("cargoId")).isEqualTo(acta.cargoId());
    assertThat(r.jsonPath().getLong("usuarioId")).as("el usuario que subio la foto").isNotNull();
    // La clave del objeto la fija el servidor: no viene del cliente.
    assertThat(r.jsonPath().getString("s3Key"))
        .as("la clave la impone el backend: %s", r.asString())
        .startsWith("actas/" + acta.id() + "/")
        .endsWith(".webp");
    assertThat(r.jsonPath().getLong("sizeBytes")).isEqualTo(PNG_1X1.length);
    assertThat(r.jsonPath().getString("contentType")).isEqualTo("image/png");
    assertThat(r.jsonPath().getString("estado")).isEqualTo("EN_REVISION");
    assertThat(r.jsonPath().getString("creadoEn")).isNotBlank();
    assertThat(r.jsonPath().getString("actualizadoEn")).isNotBlank();
    // Sin observaciones marcadas todavia.
    assertThat(r.jsonPath().getList("observaciones")).isEmpty();

    // Y la evidencia existe: se puede leer por su UUID publico.
    Response leida = as(observer).get(EVIDENCIAS + "/" + id);
    assertThat(leida.statusCode()).as("body: %s", leida.asString()).isEqualTo(200);
    assertThat(leida.jsonPath().getString("id")).isEqualTo(id);
  }

  @Test
  @DisplayName("POST /evidencias sin el param actaId devuelve 400 con el shape estándar")
  void subirEvidenciaSinActaIdDevuelve400ConElShapeEstandar() {
    String observer = observerToken();
    registrarActa(observer);

    Response r = subirEvidencia(observer, "", "evidencia.png", "image/png", PNG_1X1);

    // CONTRATO: 400 con el shape estándar de error.
    // HOY: 401. El handler único de "parte o parámetro faltante" declara dos tipos de excepción
    // pero recibe un solo parámetro, así que Spring no puede resolverlo, cae en el
    // DefaultHandlerExceptionResolver y su sendError dispara un dispatch a /error que el
    // SecurityConfig no tiene en permitAll. Es un defecto del backend: el test afirma el
    // contrato, no el síntoma.
    assertErrorShape(r, 400);
  }

  @Test
  @DisplayName("POST /evidencias sin la parte file devuelve 400 con el shape estándar")
  void subirEvidenciaSinLaParteFileDevuelve400ConElShapeEstandar() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response r =
        as(observer)
            .contentType("multipart/form-data")
            .multiPart("otroCampo", "valor")
            .post(EVIDENCIAS + "?actaId=" + acta.id());

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).contains("file");
  }

  @Test
  @DisplayName("POST /evidencias rechaza los content-type que no son image/*")
  void subirEvidenciaConTipoDeArchivoNoPermitidoDevuelve400() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    byte[] texto = "esto no es una imagen".getBytes(StandardCharsets.UTF_8);

    Response textoPlano =
        subirEvidencia(observer, "?actaId=" + acta.id(), "nota.txt", "text/plain", texto);
    Response pdf =
        subirEvidencia(observer, "?actaId=" + acta.id(), "acta.pdf", "application/pdf", texto);

    assertErrorShape(textoPlano, 400);
    assertErrorShape(pdf, 400);
    assertThat(textoPlano.jsonPath().getString("message")).contains("image/*");
    assertThat(pdf.jsonPath().getString("message")).contains("image/*");
  }

  @Test
  @DisplayName("POST /evidencias acepta cualquier content-type image/*, no sólo jpg/png/webp")
  void subirEvidenciaAceptaCualquierContentTypeImage() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    // El backend valida el prefijo "image/", no el sub-tipo: lo que se declara es lo que se
    // guarda. La suite fija ese comportamiento observable (el sub-tipo no se discrimina).
    Response r =
        subirEvidencia(observer, "?actaId=" + acta.id(), "evidencia.webp", "image/webp", PNG_1X1);

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    assertThat(r.jsonPath().getString("contentType")).isEqualTo("image/webp");
    evidenciasCreadas.add(r.jsonPath().getString("id"));
  }

  @Test
  @DisplayName("POST /evidencias con un actaId inexistente devuelve 400 con el shape estándar")
  void subirEvidenciaConActaIdInexistenteDevuelve400() {
    String observer = observerToken();

    Response r = subirEvidencia(observer, "?actaId=999999999", "evidencia.png", "image/png", PNG_1X1);

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).contains("Acta no encontrada");
  }

  // -----------------------------------------------------------------------------------------------
  // Consulta
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /evidencias/{id} devuelve la metadata de la evidencia, no el binario")
  void obtenerEvidenciaDevuelveLaMetadata() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r = as(observer).get(EVIDENCIAS + "/" + evidencia.id());

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getString("id")).isEqualTo(evidencia.id());
    assertThat(r.jsonPath().getLong("jrvId")).isEqualTo(acta.jrvId());
    assertThat(r.jsonPath().getLong("cargoId")).isEqualTo(acta.cargoId());
    assertThat(r.jsonPath().getString("s3Key")).isEqualTo(evidencia.s3Key());
    assertThat(r.jsonPath().getLong("sizeBytes")).isEqualTo(PNG_1X1.length);
    assertThat(r.jsonPath().getString("contentType")).isEqualTo("image/png");
    assertThat(r.jsonPath().getString("estado")).isEqualTo("EN_REVISION");
    // La metadata es JSON: el binario no viaja por acá.
    assertThat(r.getContentType()).as("content-type: %s", r.getContentType()).contains("application/json");
  }

  @Test
  @DisplayName("GET /evidencias/{id} de una evidencia inexistente devuelve 404")
  void obtenerEvidenciaInexistenteDevuelve404() {
    String token = tokenFor("COORDINATOR");

    Response r = as(token).get(EVIDENCIAS + "/" + "11111111-2222-3333-4444-555555555555");

    assertErrorShape(r, 404);
    assertThat(r.jsonPath().getString("message")).contains("Evidencia no encontrada");
  }

  // -----------------------------------------------------------------------------------------------
  // URL de descarga
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /evidencias/{id}/download-url devuelve una URL prefirmada de descarga")
  void obtenerUrlDeDescargaDevuelveUnaUrlPrefirmada() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r = as(observer).get(EVIDENCIAS + "/" + evidencia.id() + "/download-url");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getString("id")).isEqualTo(evidencia.id());
    assertThat(r.jsonPath().getString("emitidoEn")).isNotBlank();

    String url = r.jsonPath().getString("url");
    assertThat(url).as("la url de descarga: %s", url).isNotBlank();
    // Es una URL absoluta de storage, no una ruta de esta API.
    assertThat(url).as("la url de descarga: %s", url).matches("^https?://.+$");
    assertThat(url).doesNotStartWith(BASE_URL);
    // Y está prefirmada: firma y expiración de SigV4.
    assertThat(url).contains("X-Amz-Signature=");
    assertThat(url).contains("X-Amz-Expires=");
    // La URL apunta al objeto que se subió.
    assertThat(url).contains(evidencia.s3Key());
  }

  @Test
  @DisplayName("GET /evidencias/{id}/download-url de una evidencia inexistente devuelve 404")
  void obtenerUrlDeDescargaDeEvidenciaInexistenteDevuelve404() {
    String token = tokenFor("COORDINATOR");

    Response r =
        as(token).get(EVIDENCIAS + "/" + "11111111-2222-3333-4444-555555555555" + "/download-url");

    assertErrorShape(r, 404);
  }

  @Test
  @DisplayName("GET /evidencias/{id}/download-url lo puede pedir el ADMIN, que tiene evidencia:read")
  void obtenerUrlDeDescargaComoAdminDevuelve200() {
    String admin = tokenFor("ADMIN");
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r = as(admin).get(EVIDENCIAS + "/" + evidencia.id() + "/download-url");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getString("url")).isNotBlank();
  }

  @Test
  @DisplayName("GET /evidencias/{id}/download-url sin token devuelve 401 con el shape estándar")
  void obtenerUrlDeDescargaSinTokenDevuelve401() {
    Response r = anon().get(EVIDENCIAS + "/" + UUID.randomUUID() + "/download-url");

    assertErrorShape(r, 401);
  }

  // -----------------------------------------------------------------------------------------------
  // Estado
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("PATCH /evidencias/{id}/estado aprueba la evidencia y el cambio queda persistido")
  void cambiarElEstadoDeLaEvidenciaLaAprobada() {
    String coordinator = tokenFor("COORDINATOR");
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r =
        as(coordinator)
            .body(json(Map.of("estado", "APROBADA")))
            .patch(EVIDENCIAS + "/" + evidencia.id() + "/estado");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getString("id")).isEqualTo(evidencia.id());
    assertThat(r.jsonPath().getString("estado")).isEqualTo("APROBADA");

    // El estado no es una proyección de la respuesta: queda en la evidencia.
    Response releida = as(observer).get(EVIDENCIAS + "/" + evidencia.id());
    assertThat(releida.statusCode()).as("body: %s", releida.asString()).isEqualTo(200);
    assertThat(releida.jsonPath().getString("estado")).isEqualTo("APROBADA");
  }

  @Test
  @DisplayName("PATCH /evidencias/{id}/estado a un destino no transicionable devuelve 400")
  void cambiarElEstadoAUnDestinoInvalidoDevuelve400() {
    String coordinator = tokenFor("COORDINATOR");
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r =
        as(coordinator)
            .body(json(Map.of("estado", "EN_REVISION")))
            .patch(EVIDENCIAS + "/" + evidencia.id() + "/estado");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).contains("APROBADA");
    // La evidencia no se movió.
    assertThat(as(observer).get(EVIDENCIAS + "/" + evidencia.id()).jsonPath().getString("estado"))
        .isEqualTo("EN_REVISION");
  }

  @Test
  @DisplayName("PATCH /evidencias/{id}/estado le devuelve 403 al ADMIN, que es solo lectura")
  void cambiarElEstadoComoAdminDevuelve403() {
    String admin = tokenFor("ADMIN");
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r =
        as(admin)
            .body(json(Map.of("estado", "APROBADA")))
            .patch(EVIDENCIAS + "/" + evidencia.id() + "/estado");

    assertErrorShape(r, 403);
    assertThat(as(observer).get(EVIDENCIAS + "/" + evidencia.id()).jsonPath().getString("estado"))
        .isEqualTo("EN_REVISION");
  }

  // -----------------------------------------------------------------------------------------------
  // Observaciones
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("PATCH /evidencias/{id}/observaciones marca las observaciones de la evidencia")
  void marcarObservacionesDevuelveLaListaMarcada() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r =
        as(observer)
            .body(json(Map.of("tipos", List.of("FOTO_BORROSA"))))
            .patch(EVIDENCIAS + "/" + evidencia.id() + "/observaciones");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> observaciones = r.jsonPath().getList("observaciones");
    assertThat(observaciones).as("observaciones: %s", r.asString()).hasSize(1);
    assertThat(observaciones.get(0).get("tipo")).isEqualTo("FOTO_BORROSA");
    assertThat(observaciones.get(0).get("creadoEn")).isNotNull();
  }

  @Test
  @DisplayName("PATCH /evidencias/{id}/observaciones con más de dos categorías devuelve 400")
  void marcarMasDeDosObservacionesDevuelve400() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r =
        as(observer)
            .body(
                json(
                    Map.of(
                        "tipos",
                        List.of("FOTO_BORROSA", "FIRMA_FALTANTE", "OTRA"))))
            .patch(EVIDENCIAS + "/" + evidencia.id() + "/observaciones");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).contains("2 observaciones");
  }

  @Test
  @DisplayName("PATCH /evidencias/{id}/observaciones sólo lo puede hacer quien subió la evidencia")
  void marcarObservacionesDeOtroObserverDevuelve400() {
    String owner = observerToken();
    ActaDePrueba acta = registrarActa(owner);
    EvidenciaSubida evidencia = subirEvidenciaValida(owner, acta.id());
    String otro = observerToken();

    Response r =
        as(otro)
            .body(json(Map.of("tipos", List.of("OTRA"))))
            .patch(EVIDENCIAS + "/" + evidencia.id() + "/observaciones");

    assertErrorShape(r, 400);
    assertThat(r.jsonPath().getString("message")).contains("subió la evidencia");
  }

  @Test
  @DisplayName("PATCH /evidencias/{id}/observaciones le devuelve 403 al ADMIN, que es solo lectura")
  void marcarObservacionesComoAdminDevuelve403() {
    String admin = tokenFor("ADMIN");
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response r =
        as(admin)
            .body(json(Map.of("tipos", List.of("FOTO_BORROSA"))))
            .patch(EVIDENCIAS + "/" + evidencia.id() + "/observaciones");

    assertErrorShape(r, 403);
  }

  // -----------------------------------------------------------------------------------------------
  // Borrado
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("DELETE /evidencias/{id} borra la evidencia y después da 404")
  void borrarEvidenciaLaEliminaYDespuesDa404() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    Response borrada = as(masterToken()).delete(EVIDENCIAS + "/" + evidencia.id());

    assertThat(borrada.statusCode()).as("body: %s", borrada.asString()).isEqualTo(204);

    Response despues = as(observer).get(EVIDENCIAS + "/" + evidencia.id());
    assertErrorShape(despues, 404);
  }

  @Test
  @DisplayName("DELETE /evidencias/{id} le devuelve 403 a quien no tiene evidencia:delete")
  void borrarEvidenciaSinPermisoDevuelve403() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    EvidenciaSubida evidencia = subirEvidenciaValida(observer, acta.id());

    // El OBSERVER no tiene evidencia:delete, y el ADMIN tampoco: borrar es de MASTER.
    Response r = as(observer).delete(EVIDENCIAS + "/" + evidencia.id());
    Response rAdmin = as(tokenFor("ADMIN")).delete(EVIDENCIAS + "/" + evidencia.id());

    assertErrorShape(r, 403);
    assertErrorShape(rAdmin, 403);
    // El 403 no borró nada: la evidencia sigue existiendo.
    assertThat(as(observer).get(EVIDENCIAS + "/" + evidencia.id()).statusCode())
        .as("un 403 no puede borrar la evidencia")
        .isEqualTo(200);
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
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response r = as(observerToken).body(json(body)).post(OBSERVER + "/actas");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    long actaId = r.jsonPath().getLong("id");
    registrarActaCreada(actaId, observerToken, body);
    return new ActaDePrueba(actaId, jrvId(combo), cargoId(combo), body);
  }

  /** Sube una evidencia válida y devuelve su UUID público y su clave en S3. */
  private EvidenciaSubida subirEvidenciaValida(String token, long actaId) {
    Response r = subirEvidencia(token, "?actaId=" + actaId, "evidencia.png", "image/png", PNG_1X1);
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    String id = r.jsonPath().getString("id");
    evidenciasCreadas.add(id);
    return new EvidenciaSubida(id, r.jsonPath().getString("s3Key"));
  }

  /** POST multipart al módulo de evidencias. */
  private static Response subirEvidencia(
      String token, String query, String fileName, String contentType, byte[] bytes) {
    RequestSpecification spec = as(token).contentType("multipart/form-data");
    if (bytes == null) {
      spec = spec.multiPart("otroCampo", "valor");
    } else {
      spec = spec.multiPart("file", fileName, bytes, contentType);
    }
    return spec.post(EVIDENCIAS + query);
  }

  /**
   * Borra las evidencias que el test subió, para no dejar foto huérfana en el storage ni filas
   * colgando de un acta que la limpieza va a anular. Es best-effort a propósito: el borrado como
   * tal lo afirma su propio test.
   */
  @AfterEach
  void borrarEvidenciasCreadas() {
    for (String id : evidenciasCreadas) {
      as(masterToken()).delete(EVIDENCIAS + "/" + id);
    }
    evidenciasCreadas.clear();
  }
}
