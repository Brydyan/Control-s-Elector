package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato de la matriz de autorización por rol: qué endpoint le abre la puerta a qué rol.
 *
 * <p>Los otros archivos de la suite fijan <b>qué</b> hace cada endpoint (el flujo de negocio, la
 * máquina de estados, la forma del body). Este fija <b>quién</b> puede hacerlo. La diferencia es
 * fundamental: un flujo de negocio puede estar perfecto y la autorización puede estar rota igual.
 *
 * <p>La tabla de roles del proyecto (fuente: {@code CLAUDE.md} y la skill
 * {@code control-electoral-review}) es:
 *
 * <ul>
 *   <li>MASTER: todo. Único que revoca sesiones y anula actas APROBADA.
 *   <li>COORDINATOR: disputas, correcciones y aprobaciones en todas las JRVs.
 *   <li>ADMIN: <b>solo lectura</b> — dashboard. Nunca escribe.
 *   <li>OBSERVER: registra actas, corrige las suyas.
 * </ul>
 *
 * <p><b>Reglas de autorización fijadas.</b> Para cada endpoint, esta suite afirma cuatro cosas:
 *
 * <ol>
 *   <li>Sin token → <b>401</b> (nunca 404, nunca 500).
 *   <li>Rol sin el permiso → <b>403</b> (nunca 404, nunca 500).
 *   <li>Rol con el permiso → <b>2xx</b> (o 400/409 si el estado del dominio no permite la
 *       operación: lo que importa es que NO sea 401 ni 403).
 *   <li>El ADMIN nunca escribe: en todo endpoint mutante con guard de método el ADMIN recibe 403.
 * </ol>
 *
 * <p><b>Matriz rol × endpoint (guardas efectivas).</b> Las guardas del backend se leen así:
 *
 * <pre>
 * endpoint                                  MASTER  COORDINATOR  ADMIN  OBSERVER
 * POST /auth/register                       201     403          403    403
 * POST /observer/actas                      201     403          403    201
 * POST /observer/actas/borrador             200     403          403    200
 * POST /observer/actas/{id}/enviar          403*    403          403    200
 * PUT  /observer/actas/{id}/corregir        403*    403          403    200
 * POST /observer/actas/{id}/imagen          201     403          403    201
 * GET  /observer/actas/{id}                 403*    403          403    200
 * PUT  /coordinator/actas/{id}/disputar     200     200          200!   200!
 * PUT  /coordinator/actas/{id}/corregir     200     200          200!   200!
 * PUT  /coordinator/actas/{id}/aprobar      200     200          200!   200!
 * DELETE /coordinator/actas/{id}            204     403          403    403
 * POST /coordinator/actas/{id}/imagen       201     201          403    403
 * POST /evidencias                           201     403          403    201
 * PATCH /evidencias/{id}/estado             200     200          403    403
 * PATCH /evidencias/{id}/observaciones      400*    400*         403    200
 * DELETE /evidencias/{id}                   204     403          403    403
 * GET /admin/usuarios/{publicId}            200     200          200    403
 * PUT /admin/usuarios/{publicId}            200     403          403    403
 * DELETE /admin/usuarios/{publicId}         204     403          403    403
 * GET /admin/sesiones/{sesionId}            200     200          200    403
 * DELETE /admin/sesiones/{sesionId}         204     403          403    403
 * </pre>
 *
 * <p>Los valores marcados con {@code *} no son 403 porque el rol no tiene el permiso, sino porque
 * la <b>autoría</b> lo impide (el MASTER tiene {@code acta:upload} pero no puede tocar el borrador
 * de otro OBSERVER, y el COORDINATOR tiene {@code evidencia:update} pero no puede marcar
 * observaciones sobre una evidencia que no subió). Los marcados con {@code !} son el agujero
 * conocido que se documenta abajo.
 *
 * <p><b>AGUJERO CONOCIDO #121 — el guard de clase del módulo coordinator deja pasar al OBSERVER y
 * al ADMIN.</b> El {@code @PreAuthorize} de clase de {@code CoordinatorController} es
 * {@code hasAnyAuthority('acta:aprobar','acta:rechazar','acta:disputar','acta:read','acta:anular')}.
 * Dos cosas pasan ahí: {@code acta:disputar} no existe como permiso en la semilla RBAC (nadie lo
 * tiene, ni siquiera el MASTER), y {@code acta:read} sí lo tienen el OBSERVER y el ADMIN. Los
 * endpoints {@code disputar}, {@code corregir} y {@code aprobar} no tienen guard de método que
 * estreche el juego. Resultado: <b>un OBSERVER y un ADMIN pueden disputar, corregir y aprobar
 * actas</b>, que es exactamente lo que la tabla de roles prohíbe.
 *
 * <p>Este archivo <b>no maquilla ese agujero</b>: afirma el status que el backend devuelve hoy (200,
 * con el acta efectivamente disputada / corregida / aprobada) y lo deja escrito con la referencia a
 * la issue. Cuando #121 se corrija, estos asserts pasan a 403 y hay que actualizarlos. Los tests
 *
 * <p><b>El agujero #121 está corregido.</b> El guard de clase de {@code CoordinatorController}
 * incluía {@code acta:read} (que OBSERVER y ADMIN tienen) y {@code disputar}, {@code corregir} y
 * {@code aprobar} no tenían guard de método: un OBSERVER podía disputar, corregir y aprobar
 * actas ajenas. Ahora el guard de clase es {@code hasAnyRole('COORDINATOR','MASTER')} —el mismo
 * que el {@code roleGuard} del frontend— y los tres endpoints mutantes llevan guard de método
 * propio.
 *
 * <p><b>Defecto encontrado y corregido por esta suite (no es #121).</b> {@code POST
 * /api/v1/observer/actas/borrador} devolvía <b>500</b> cuando el par (JRV, cargo) ya tenía un acta
 * activa registrada por <b>otro</b> usuario: el use case buscaba el borrador por {@code (jrv, cargo,
 * observerId)}, no lo encontraba e intentaba insertar, violando el índice único {@code
 * uk_acta_activa}. El hermano {@code POST /api/v1/observer/actas} sí devolvía 409 para el mismo
 * caso. Ya corregido: ambos devuelven 409. Está documentado en {@link
 * #guardarBorradorSobreUnParConActaDeOtroObserverDevuelve409()}.
 *
 * <p><b>Sobre los datos.</b> Un acta ocupa para siempre su par (JRV, cargo) mientras no esté
 * ANULADA, así que cada test que necesita un acta real reserva un par libre con
 * {@link #comboLibre(String)} y lo devuelve a ANULADA al terminar (la limpieza de la clase base).
 * Para no quemar pares del pool, los probes de 403 de los endpoints con id usan un id de acta
 * inexistente a propósito: la autorización se resuelve <b>antes</b> de tocar el acta, así que el
 * 403 no depende de que el acta exista. Los probes de 403 de los endpoints sin id (el alta y el
 * borrador) usan un body estructuralmente válido sobre un par ya ocupado por el seed: si la guarda
 * faltara, la respuesta sería 409 o 500 y el test fallaría igual de claro.
 *
 * <p><b>Orden de validación.</b> La validación del body corre <b>antes</b> que la autorización: un
 * body inválido con un rol sin permiso devuelve 400, no 403. Por eso todos los probes de 403 de
 * este archivo mandan un body <b>estructuralmente válido</b>.
 */
class RbacContractIT extends ContractTestSupport {

  /** Prefijo del módulo de autenticación. */
  private static final String AUTH = "/api/v1/auth";

  /** Prefijo del módulo de evidencias. */
  private static final String EVIDENCIAS = "/api/v1/evidencias";

  /** Prefijo del módulo de administración de usuarios. */
  private static final String ADMIN_USUARIOS = "/api/v1/admin/usuarios";

  /** Prefijo del módulo de administración de sesiones. */
  private static final String ADMIN_SESIONES = "/api/v1/admin/sesiones";

  /** Id de acta que no existe: alcanza para probar guardas, que corren antes de la búsqueda. */
  private static final long ACTA_INEXISTENTE = 999999999L;

  /** Id de evidencia que no existe. */
  private static final String EVIDENCIA_INEXISTENTE = "11111111-2222-3333-4444-555555555555";

  /** PNG de 1x1 píxel transparente, 70 bytes: el mismo fixture verificado de la suite de evidencias. */
  private static final byte[] PNG_1X1 =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNgYGBgAAAABQABeqhXUAAAAABJRU5ErkJggg==");

  /** Evidencias que este test subió y que hay que borrar al terminar. */
  private final List<String> evidenciasCreadas = new ArrayList<>();

  // -----------------------------------------------------------------------------------------------
  // Módulo auth
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("POST /auth/register lo autoriza sólo el MASTER: los otros roles reciben 403")
  void registrarUsuarioSoloLoAutorizaElMaster() {
    Map<String, Object> body =
        Map.of(
            "nombre", unique("nombre"),
            "apellido", unique("apellido"),
            "password", TEST_PASSWORD,
            "rol", "OBSERVER");

    Response sinToken = anon().body(json(body)).post(AUTH + "/register");
    Response coordinator = as(tokenFor("COORDINATOR")).body(json(body)).post(AUTH + "/register");
    Response admin = as(tokenFor("ADMIN")).body(json(body)).post(AUTH + "/register");
    Response observer = as(observerToken()).body(json(body)).post(AUTH + "/register");
    Response master = as(masterToken()).body(json(body)).post(AUTH + "/register");

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);
    assertErrorShape(observer, 403);
    assertThat(master.statusCode()).as("body: %s", master.asString()).isEqualTo(201);
    assertThat(master.jsonPath().getString("email")).as("el usuario creado trae su email").isNotBlank();
  }

  // -----------------------------------------------------------------------------------------------
  // Módulo de actas del observer
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("POST /observer/actas exige el permiso acta:upload: lo tienen el OBSERVER y el MASTER")
  void registrarActaExigeElPermisoActaUpload() {
    String observer = observerToken();
    Map<String, Object> body = bodyDeActaValido(observer);

    Response sinToken = anon().body(json(body)).post(OBSERVER + "/actas");
    Response coordinator = as(tokenFor("COORDINATOR")).body(json(body)).post(OBSERVER + "/actas");
    Response admin = as(tokenFor("ADMIN")).body(json(body)).post(OBSERVER + "/actas");

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);

    // El OBSERVER y el MASTER sí pueden: los dos tienen acta:upload.
    ActaDePrueba delObserver = registrarActa(observer);
    ActaDePrueba delMaster = registrarActa(masterToken());

    assertThat(delObserver.id()).isPositive();
    assertThat(delMaster.id()).isPositive();
    assertThat(delObserver.id()).isNotEqualTo(delMaster.id());
  }

  @Test
  @DisplayName("POST /observer/actas/borrador exige el permiso acta:upload: lo tienen el OBSERVER y el MASTER")
  void guardarBorradorExigeElPermisoActaUpload() {
    String observer = observerToken();
    // Body sobre un par ya ocupado: sólo sirve para los probes de 403, donde la guarda corre
    // antes de tocar el acta. Los 2xx van sobre pares libres (ver registrarBorrador).
    Map<String, Object> body = bodyDeActaValido(observer);

    Response sinToken = anon().body(json(body)).post(OBSERVER + "/actas/borrador");
    Response coordinator = as(tokenFor("COORDINATOR")).body(json(body)).post(OBSERVER + "/actas/borrador");
    Response admin = as(tokenFor("ADMIN")).body(json(body)).post(OBSERVER + "/actas/borrador");

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);

    // El OBSERVER y el MASTER sí pueden: cada uno sobre su propio par (JRV, cargo) libre.
    ActaDePrueba delObserver = registrarBorrador(observer);
    ActaDePrueba delMaster = registrarBorrador(masterToken());

    assertThat(delObserver.id()).isPositive();
    assertThat(delMaster.id()).isPositive();
    assertThat(delObserver.id()).isNotEqualTo(delMaster.id());
  }

  @Test
  @DisplayName("POST /observer/actas/borrador sobre un par con acta de otro observer devuelve 409")
  void guardarBorradorSobreUnParConActaDeOtroObserverDevuelve409() {
    // El par (JRV, cargo) admite una sola acta activa. Guardar un borrador sobre un par que ya
    // tiene un acta activa registrada por OTRO usuario tiene que devolver 409, igual que su
    // hermano POST /api/v1/observer/actas.
    //
    // Antes devolvía 500: GuardarBorradorActaUseCase buscaba el borrador por (jrv, cargo,
    // observerId), no veía el acta del otro observer e intentaba insertar; el índice único
    // uk_acta_activa (jrv_id, cargo_id) WHERE estado IN ('BORRADOR','ENVIADA','EN_DISPUTA',
    // 'CORREGIDA') reventaba con DataIntegrityViolationException y caía en el catch-all.
    String observer = observerToken();
    ActaDePrueba actaDeOtro = registrarActa(observer);

    Map<String, Object> body =
        bodyDeActa(
            actaDeOtro.jrvId(),
            actaDeOtro.cargoId(),
            candidatosDe(actaDeOtro.cargoId(), observer),
            true);

    Response r = as(masterToken()).body(json(body)).post(OBSERVER + "/actas/borrador");

    assertErrorShape(r, 409);
    assertThat(r.jsonPath().getString("message")).contains("Ya existe un acta activa");
  }

  @Test
  @DisplayName("POST /observer/actas/{id}/enviar exige el permiso acta:upload y la autoría del borrador")
  void enviarBorradorExigeElPermisoActaUploadYLaAutoria() {
    String observer = observerToken();
    ActaDePrueba acta = registrarBorrador(observer);

    Response sinToken = anon().post(OBSERVER + "/actas/" + acta.id() + "/enviar");
    Response coordinator = as(tokenFor("COORDINATOR")).post(OBSERVER + "/actas/" + acta.id() + "/enviar");
    Response admin = as(tokenFor("ADMIN")).post(OBSERVER + "/actas/" + acta.id() + "/enviar");
    // El MASTER tiene acta:upload: pasa la guarda, pero no puede enviar el borrador de otro
    // observer porque el borrador tiene dueño. El 403 es de autoría, no de permiso.
    Response master = as(masterToken()).post(OBSERVER + "/actas/" + acta.id() + "/enviar");

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);
    assertErrorShape(master, 403);
    assertThat(master.jsonPath().getString("message")).contains("Solo el autor");

    Response delObserver = as(observer).post(OBSERVER + "/actas/" + acta.id() + "/enviar");

    assertThat(delObserver.statusCode()).as("body: %s", delObserver.asString()).isEqualTo(200);
    assertThat(delObserver.jsonPath().getString("estado")).isEqualTo("ENVIADA");
  }

  @Test
  @DisplayName("PUT /observer/actas/{id}/corregir exige el permiso acta:upload y la autoría del acta")
  void corregirActaDelObserverExigeElPermisoActaUploadYLaAutoria() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    // La disputa la abre el COORDINATOR: es la precondición del estado EN_DISPUTA.
    as(tokenFor("COORDINATOR"))
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    Response sinToken =
        anon().body(json(acta.body())).put(OBSERVER + "/actas/" + acta.id() + "/corregir");
    Response coordinator =
        as(tokenFor("COORDINATOR"))
            .body(json(acta.body()))
            .put(OBSERVER + "/actas/" + acta.id() + "/corregir");
    Response admin =
        as(tokenFor("ADMIN")).body(json(acta.body())).put(OBSERVER + "/actas/" + acta.id() + "/corregir");
    // El MASTER pasa la guarda (tiene acta:upload) pero no es el autor del acta.
    Response master =
        as(masterToken())
            .body(json(acta.body()))
            .put(OBSERVER + "/actas/" + acta.id() + "/corregir");

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);
    assertErrorShape(master, 403);

    Response delObserver =
        as(observer).body(json(acta.body())).put(OBSERVER + "/actas/" + acta.id() + "/corregir");

    assertThat(delObserver.statusCode()).as("body: %s", delObserver.asString()).isEqualTo(200);
    assertThat(delObserver.jsonPath().getString("estado")).isEqualTo("CORREGIDA");
  }

  @Test
  @DisplayName("POST /observer/actas/{id}/imagen exige el permiso acta:upload: lo tienen el OBSERVER y el MASTER")
  void subirLaFotoDelActaExigeElPermisoActaUpload() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response sinToken = subirImagen(anon(), OBSERVER + "/actas/" + acta.id() + "/imagen");
    Response coordinator =
        subirImagen(as(tokenFor("COORDINATOR")), OBSERVER + "/actas/" + acta.id() + "/imagen");
    Response admin = subirImagen(as(tokenFor("ADMIN")), OBSERVER + "/actas/" + acta.id() + "/imagen");

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);

    Response delObserver = subirImagen(as(observer), OBSERVER + "/actas/" + acta.id() + "/imagen");
    Response delMaster = subirImagen(as(masterToken()), OBSERVER + "/actas/" + acta.id() + "/imagen");

    assertThat(delObserver.statusCode()).as("body: %s", delObserver.asString()).isEqualTo(201);
    assertThat(delMaster.statusCode()).as("body: %s", delMaster.asString()).isEqualTo(201);
  }

  @Test
  @DisplayName("GET /observer/actas/{id} sólo lo ve el OBSERVER que la registró: los demás roles reciben 403")
  void elDetalleDelActaSoloLoVeQuienLaRegistro() {
    String owner = observerToken();
    ActaDePrueba acta = registrarActa(owner);

    Response sinToken = anon().get(OBSERVER + "/actas/" + acta.id());
    // El COORDINATOR y el ADMIN no tienen acta:upload: la guarda los rechaza.
    Response coordinator = as(tokenFor("COORDINATOR")).get(OBSERVER + "/actas/" + acta.id());
    Response admin = as(tokenFor("ADMIN")).get(OBSERVER + "/actas/" + acta.id());
    // El MASTER tiene acta:upload, así que pasa la guarda, pero el acta no es suya.
    Response master = as(masterToken()).get(OBSERVER + "/actas/" + acta.id());
    // Otro OBSERVER tiene el permiso, pero el acta es de otro: la autorización fina es la autoría.
    Response otroObserver = as(observerToken()).get(OBSERVER + "/actas/" + acta.id());

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);
    assertErrorShape(master, 403);
    // Otro OBSERVER: con RLS real la fila NO le es visible, asi que la app no puede
    // ni siquiera compararla con la suya y responde "no encontrada". Antes (con el
    // rol de superusuario, que ignora RLS) la app veia la fila y respondia 403 con
    // "Solo puedes consultar tus propias actas". La diferencia es la que hace el
    // aislamiento: no se confirma que el acta exista.
    assertErrorShape(otroObserver, 400);
    assertThat(otroObserver.jsonPath().getString("message")).contains("no encontrada");

    // El dueño sí ve su acta.
    Response delDueno = as(owner).get(OBSERVER + "/actas/" + acta.id());

    assertThat(delDueno.statusCode()).as("body: %s", delDueno.asString()).isEqualTo(200);
    assertThat(delDueno.jsonPath().getLong("id")).isEqualTo(acta.id());
  }

  // -----------------------------------------------------------------------------------------------
  // Módulo de actas del coordinator
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("PUT /coordinator/actas/{id}/disputar la ejecuta el COORDINATOR")
  void disputarActaLaEjecutaElCoordinator() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response sinToken =
        anon()
            .body(json(Map.of("motivo", unique("motivo de disputa"))))
            .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");
    Response coordinator =
        as(tokenFor("COORDINATOR"))
            .body(json(Map.of("motivo", unique("motivo de disputa"))))
            .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    assertErrorShape(sinToken, 401);
    assertThat(coordinator.statusCode()).as("body: %s", coordinator.asString()).isEqualTo(200);
    assertThat(coordinator.jsonPath().getString("estado")).isEqualTo("EN_DISPUTA");
  }

  @Test
  @DisplayName("un OBSERVER no puede disputar un acta: 403 y el acta no cambia de estado")
  void observerNoPuedeDisputarUnActa() {
    // El módulo de coordinación es de COORDINATOR y MASTER. Un OBSERVER puede registrar un acta,
    // pero no entrar al módulo de coordinación a entorno de la que no es dueño.
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response r =
        as(observer)
            .body(json(Map.of("motivo", unique("motivo de disputa"))))
            .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    assertProhibido(r, "PUT /coordinator/actas/{id}/disputar");
    assertThat(estadoEnCoordinator(tokenFor("COORDINATOR"), acta.id()))
        .as("un 403 no puede dejar el acta en disputa")
        .isEqualTo("ENVIADA");
  }

  @Test
  @DisplayName("un ADMIN no puede disputar un acta: 403 y el acta no cambia de estado")
  void adminNoPuedeDisputarUnActa() {
    // La tabla de roles dice que el ADMIN es solo lectura.
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response r =
        as(tokenFor("ADMIN"))
            .body(json(Map.of("motivo", unique("motivo de disputa"))))
            .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    assertProhibido(r, "PUT /coordinator/actas/{id}/disputar");
    assertThat(estadoEnCoordinator(tokenFor("COORDINATOR"), acta.id()))
        .as("un 403 no puede dejar el acta en disputa")
        .isEqualTo("ENVIADA");
  }

  @Test
  @DisplayName("PUT /coordinator/actas/{id}/disputar la ejecuta también el MASTER")
  void disputarActaLaEjecutaElMaster() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response r =
        as(masterToken())
            .body(json(Map.of("motivo", unique("motivo de disputa"))))
            .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getString("estado")).isEqualTo("EN_DISPUTA");
  }

  @Test
  @DisplayName("PUT /coordinator/actas/{id}/corregir la ejecuta el COORDINATOR")
  void corregirActaEnElModuloCoordinatorLaEjecutaElCoordinator() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    as(tokenFor("COORDINATOR"))
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    Response sinToken =
        anon().body(json(acta.body())).put(COORDINATOR + "/actas/" + acta.id() + "/corregir");
    Response coordinator =
        as(tokenFor("COORDINATOR"))
            .body(json(acta.body()))
            .put(COORDINATOR + "/actas/" + acta.id() + "/corregir");

    assertErrorShape(sinToken, 401);
    assertThat(coordinator.statusCode()).as("body: %s", coordinator.asString()).isEqualTo(200);
    assertThat(coordinator.jsonPath().getString("estado")).isEqualTo("CORREGIDA");
  }

  @Test
  @DisplayName("un OBSERVER no puede corregir un acta en disputa: 403 y el acta no cambia de estado")
  void observerNoPuedeCorregirUnActa() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    as(tokenFor("COORDINATOR"))
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    Response r =
        as(observer)
            .body(json(acta.body()))
            .put(COORDINATOR + "/actas/" + acta.id() + "/corregir");

    assertProhibido(r, "PUT /coordinator/actas/{id}/corregir");
    assertThat(estadoEnCoordinator(tokenFor("COORDINATOR"), acta.id()))
        .as("un 403 no puede dejar el acta corregida")
        .isEqualTo("EN_DISPUTA");
  }

  @Test
  @DisplayName("un ADMIN no puede corregir un acta en disputa: 403 y el acta no cambia de estado")
  void adminNoPuedeCorregirUnActa() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    as(tokenFor("COORDINATOR"))
        .body(json(Map.of("motivo", unique("motivo de disputa"))))
        .put(COORDINATOR + "/actas/" + acta.id() + "/disputar");

    Response r =
        as(tokenFor("ADMIN"))
            .body(json(acta.body()))
            .put(COORDINATOR + "/actas/" + acta.id() + "/corregir");

    assertProhibido(r, "PUT /coordinator/actas/{id}/corregir");
    assertThat(estadoEnCoordinator(tokenFor("COORDINATOR"), acta.id()))
        .as("un 403 no puede dejar el acta corregida")
        .isEqualTo("EN_DISPUTA");
  }

  @Test
  @DisplayName("PUT /coordinator/actas/{id}/aprobar la ejecuta el COORDINATOR")
  void aprobarActaLaEjecutaElCoordinator() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response sinToken = anon().put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");
    Response coordinator = as(tokenFor("COORDINATOR")).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");

    assertErrorShape(sinToken, 401);
    assertThat(coordinator.statusCode()).as("body: %s", coordinator.asString()).isEqualTo(200);
    assertThat(coordinator.jsonPath().getString("estado")).isEqualTo("APROBADA");
  }

  @Test
  @DisplayName("un OBSERVER no puede aprobar un acta: 403 y el acta no cambia de estado")
  void observerNoPuedeAprobarUnActa() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response r = as(observer).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");

    assertProhibido(r, "PUT /coordinator/actas/{id}/aprobar");
    assertThat(estadoEnCoordinator(tokenFor("COORDINATOR"), acta.id()))
        .as("un 403 no puede dejar el acta aprobada")
        .isEqualTo("ENVIADA");
  }

  @Test
  @DisplayName("un ADMIN no puede aprobar un acta: 403 y el acta no cambia de estado")
  void adminNoPuedeAprobarUnActa() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response r = as(tokenFor("ADMIN")).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");

    assertProhibido(r, "PUT /coordinator/actas/{id}/aprobar");
    assertThat(estadoEnCoordinator(tokenFor("COORDINATOR"), acta.id()))
        .as("un 403 no puede dejar el acta aprobada")
        .isEqualTo("ENVIADA");
  }

  @Test
  @DisplayName("DELETE /coordinator/actas/{id} es sólo del MASTER: los otros tres roles reciben 403")
  void anularActaEsSoloDelMaster() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    // La anulación exige que el acta esté APROBADA.
    as(tokenFor("COORDINATOR")).put(COORDINATOR + "/actas/" + acta.id() + "/aprobar");
    Map<String, Object> body = Map.of("motivo", unique("motivo de anulacion"));

    Response sinToken = anon().body(json(body)).delete(COORDINATOR + "/actas/" + acta.id());
    Response coordinator =
        as(tokenFor("COORDINATOR"))
            .body(json(body))
            .delete(COORDINATOR + "/actas/" + acta.id());
    Response admin = as(tokenFor("ADMIN")).body(json(body)).delete(COORDINATOR + "/actas/" + acta.id());
    Response observer403 =
        as(observer).body(json(body)).delete(COORDINATOR + "/actas/" + acta.id());

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);
    assertErrorShape(observer403, 403);

    // El 403 no anula nada: el acta sigue APPROBADA.
    assertThat(estadoEnCoordinator(tokenFor("COORDINATOR"), acta.id())).isEqualTo("APROBADA");

    Response delMaster =
        as(masterToken()).body(json(body)).delete(COORDINATOR + "/actas/" + acta.id());

    assertThat(delMaster.statusCode()).as("body: %s", delMaster.asString()).isEqualTo(204);
    assertThat(estadoEnCoordinator(tokenFor("COORDINATOR"), acta.id())).isEqualTo("ANULADA");
  }

  @Test
  @DisplayName("POST /coordinator/actas/{id}/imagen lo suben el COORDINATOR y el MASTER, no el ADMIN ni el OBSERVER")
  void subirEvidenciaDeContingenciaLaSubeElCoordinatorYElMaster() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response sinToken = subirImagen(anon(), COORDINATOR + "/actas/" + acta.id() + "/imagen");
    Response coordinator = subirImagen(as(tokenFor("COORDINATOR")), COORDINATOR + "/actas/" + acta.id() + "/imagen");
    Response admin = subirImagen(as(tokenFor("ADMIN")), COORDINATOR + "/actas/" + acta.id() + "/imagen");
    Response observer403 = subirImagen(as(observer), COORDINATOR + "/actas/" + acta.id() + "/imagen");

    assertErrorShape(sinToken, 401);
    assertErrorShape(admin, 403);
    assertErrorShape(observer403, 403);
    assertThat(coordinator.statusCode()).as("body: %s", coordinator.asString()).isEqualTo(201);

    Response delMaster = subirImagen(as(masterToken()), COORDINATOR + "/actas/" + acta.id() + "/imagen");

    assertThat(delMaster.statusCode()).as("body: %s", delMaster.asString()).isEqualTo(201);
  }

  // -----------------------------------------------------------------------------------------------
  // Módulo de evidencias
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("POST /evidencias exige el permiso evidencia:upload: lo tienen el OBSERVER y el MASTER")
  void subirEvidenciaExigeElPermisoEvidenciaUpload() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);

    Response sinToken = subirEvidencia(anon(), acta.id());
    Response coordinator = subirEvidencia(as(tokenFor("COORDINATOR")), acta.id());
    Response admin = subirEvidencia(as(tokenFor("ADMIN")), acta.id());

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);

    String idDelObserver = subirEvidenciaValida(observer, acta.id());
    String idDelMaster = subirEvidenciaValida(masterToken(), acta.id());

    assertThat(idDelObserver).isNotBlank();
    assertThat(idDelMaster).isNotBlank();
    assertThat(idDelObserver).isNotEqualTo(idDelMaster);
  }

  @Test
  @DisplayName("PATCH /evidencias/{id}/estado exige acta:aprobar o acta:rechazar: el ADMIN y el OBSERVER reciben 403")
  void cambiarElEstadoDeLaEvidenciaExigePermisoDeAprobar() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    // Dos evidencias: una la aprueba el COORDINATOR y la otra el MASTER. Una sola no alcanza
    // porque la transición EN_REVISION -> APROBADA es de un solo paso.
    String idDelCoordinator = subirEvidenciaValida(observer, acta.id());
    String idDelMaster = subirEvidenciaValida(observer, acta.id());

    Response sinToken =
        anon()
            .body(json(Map.of("estado", "APROBADA")))
            .patch(EVIDENCIAS + "/" + idDelCoordinator + "/estado");
    Response admin =
        as(tokenFor("ADMIN"))
            .body(json(Map.of("estado", "APROBADA")))
            .patch(EVIDENCIAS + "/" + idDelCoordinator + "/estado");
    Response observer403 =
        as(observer)
            .body(json(Map.of("estado", "APROBADA")))
            .patch(EVIDENCIAS + "/" + idDelCoordinator + "/estado");

    assertErrorShape(sinToken, 401);
    assertErrorShape(admin, 403);
    assertErrorShape(observer403, 403);

    Response delCoordinator =
        as(tokenFor("COORDINATOR"))
            .body(json(Map.of("estado", "APROBADA")))
            .patch(EVIDENCIAS + "/" + idDelCoordinator + "/estado");
    Response delMaster =
        as(masterToken())
            .body(json(Map.of("estado", "APROBADA")))
            .patch(EVIDENCIAS + "/" + idDelMaster + "/estado");

    assertThat(delCoordinator.statusCode()).as("body: %s", delCoordinator.asString()).isEqualTo(200);
    assertThat(delCoordinator.jsonPath().getString("estado")).isEqualTo("APROBADA");
    assertThat(delMaster.statusCode()).as("body: %s", delMaster.asString()).isEqualTo(200);
    assertThat(delMaster.jsonPath().getString("estado")).isEqualTo("APROBADA");
  }

  @Test
  @DisplayName("PATCH /evidencias/{id}/observaciones exige evidencia:update y que el rol sea el dueño")
  void marcarObservacionesExigeEvidenciaUpdateYLaAutoria() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    String id = subirEvidenciaValida(observer, acta.id());
    Map<String, Object> body = Map.of("tipos", List.of("FOTO_BORROSA"));

    Response sinToken = anon().body(json(body)).patch(EVIDENCIAS + "/" + id + "/observaciones");
    // El ADMIN no tiene evidencia:update: la guarda lo rechaza con 403.
    Response admin =
        as(tokenFor("ADMIN")).body(json(body)).patch(EVIDENCIAS + "/" + id + "/observaciones");

    assertErrorShape(sinToken, 401);
    assertErrorShape(admin, 403);

    // El COORDINATOR y el MASTER tienen evidencia:update, así que pasan la guarda, pero la
    // autorización fina es la autoría: no pueden marcar observaciones sobre una evidencia que no
    // subieron. El backend responde 400, no 403.
    Response coordinator =
        as(tokenFor("COORDINATOR"))
            .body(json(body))
            .patch(EVIDENCIAS + "/" + id + "/observaciones");
    Response master = as(masterToken()).body(json(body)).patch(EVIDENCIAS + "/" + id + "/observaciones");

    assertThat(coordinator.statusCode())
        .as("el COORDINATOR pasa la guarda de permiso pero no es el dueño: %s", coordinator.asString())
        .isEqualTo(400);
    assertThat(master.statusCode())
        .as("el MASTER pasa la guarda de permiso pero no es el dueño: %s", master.asString())
        .isEqualTo(400);

    // El OBSERVER que subió la evidencia sí puede.
    Response delObserver = as(observer).body(json(body)).patch(EVIDENCIAS + "/" + id + "/observaciones");

    assertThat(delObserver.statusCode()).as("body: %s", delObserver.asString()).isEqualTo(200);
    assertThat(delObserver.jsonPath().getList("observaciones")).hasSize(1);
  }

  @Test
  @DisplayName("DELETE /evidencias/{id} exige evidencia:delete: sólo el MASTER puede borrarla")
  void borrarEvidenciaExigeElPermisoEvidenciaDelete() {
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    String id = subirEvidenciaValida(observer, acta.id());

    Response sinToken = anon().delete(EVIDENCIAS + "/" + id);
    Response coordinator = as(tokenFor("COORDINATOR")).delete(EVIDENCIAS + "/" + id);
    Response admin = as(tokenFor("ADMIN")).delete(EVIDENCIAS + "/" + id);
    Response observer403 = as(observer).delete(EVIDENCIAS + "/" + id);

    assertErrorShape(sinToken, 401);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);
    assertErrorShape(observer403, 403);

    // El 403 no borró nada: la evidencia sigue existiendo.
    assertThat(as(observer).get(EVIDENCIAS + "/" + id).statusCode())
        .as("un 403 no puede borrar la evidencia")
        .isEqualTo(200);

    Response delMaster = as(masterToken()).delete(EVIDENCIAS + "/" + id);

    assertThat(delMaster.statusCode()).as("body: %s", delMaster.asString()).isEqualTo(204);
    assertThat(as(observer).get(EVIDENCIAS + "/" + id).statusCode())
        .as("la evidencia borrada ya no existe")
        .isEqualTo(404);
  }

  // -----------------------------------------------------------------------------------------------
  // Módulo de usuarios
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /admin/usuarios/{publicId} lo pueden leer MASTER, COORDINATOR y ADMIN; el OBSERVER recibe 403")
  void verUsuarioLoAutorizaElPermisoUsuarioRead() {
    String publicId = publicIdDe(tokenDeUsuarioDePrueba());

    Response sinToken = anon().get(ADMIN_USUARIOS + "/" + publicId);
    Response observer = as(observerToken()).get(ADMIN_USUARIOS + "/" + publicId);
    Response coordinator = as(tokenFor("COORDINATOR")).get(ADMIN_USUARIOS + "/" + publicId);
    Response admin = as(tokenFor("ADMIN")).get(ADMIN_USUARIOS + "/" + publicId);
    Response master = as(masterToken()).get(ADMIN_USUARIOS + "/" + publicId);

    assertErrorShape(sinToken, 401);
    assertErrorShape(observer, 403);
    assertThat(coordinator.statusCode()).as("body: %s", coordinator.asString()).isEqualTo(200);
    assertThat(admin.statusCode()).as("body: %s", admin.asString()).isEqualTo(200);
    assertThat(master.statusCode()).as("body: %s", master.asString()).isEqualTo(200);
    assertThat(master.jsonPath().getString("publicId")).isEqualTo(publicId);
    assertThat(master.jsonPath().getString("email")).isNotBlank();
  }

  @Test
  @DisplayName("PUT /admin/usuarios/{publicId} lo autoriza sólo el MASTER: los otros roles reciben 403")
  void actualizarUsuarioLoAutorizaSoloElMaster() {
    String publicId = publicIdDe(tokenDeUsuarioDePrueba());
    Map<String, Object> body =
        Map.of(
            "nombre", unique("nombre"),
            "apellido", unique("apellido"),
            "rolNombre", "ADMIN",
            "habilitado", true);

    Response sinToken = anon().body(json(body)).put(ADMIN_USUARIOS + "/" + publicId);
    Response observer = as(observerToken()).body(json(body)).put(ADMIN_USUARIOS + "/" + publicId);
    Response coordinator = as(tokenFor("COORDINATOR")).body(json(body)).put(ADMIN_USUARIOS + "/" + publicId);
    Response admin = as(tokenFor("ADMIN")).body(json(body)).put(ADMIN_USUARIOS + "/" + publicId);

    assertErrorShape(sinToken, 401);
    assertErrorShape(observer, 403);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);

    Response master = as(masterToken()).body(json(body)).put(ADMIN_USUARIOS + "/" + publicId);

    assertThat(master.statusCode()).as("body: %s", master.asString()).isEqualTo(200);
    assertThat(master.jsonPath().getString("publicId")).isEqualTo(publicId);
    assertThat(master.jsonPath().getString("rolNombre")).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("DELETE /admin/usuarios/{publicId} lo autoriza sólo el MASTER: los otros roles reciben 403")
  void deshabilitarUsuarioLoAutorizaSoloElMaster() {
    String publicId = publicIdDe(tokenDeUsuarioDePrueba());

    Response sinToken = anon().delete(ADMIN_USUARIOS + "/" + publicId);
    Response observer = as(observerToken()).delete(ADMIN_USUARIOS + "/" + publicId);
    Response coordinator = as(tokenFor("COORDINATOR")).delete(ADMIN_USUARIOS + "/" + publicId);
    Response admin = as(tokenFor("ADMIN")).delete(ADMIN_USUARIOS + "/" + publicId);

    assertErrorShape(sinToken, 401);
    assertErrorShape(observer, 403);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);

    Response master = as(masterToken()).delete(ADMIN_USUARIOS + "/" + publicId);

    assertThat(master.statusCode()).as("body: %s", master.asString()).isEqualTo(204);
    assertThat(as(masterToken()).get(ADMIN_USUARIOS + "/" + publicId).jsonPath().getBoolean("habilitado"))
        .as("el usuario queda deshabilitado")
        .isFalse();
  }

  // -----------------------------------------------------------------------------------------------
  // Módulo de sesiones
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("GET /admin/sesiones/{sesionId} lo pueden leer MASTER, COORDINATOR y ADMIN; el OBSERVER recibe 403")
  void verSesionLoAutorizaElPermisoSesionList() {
    String sesionId = sesionIdDe(emailDeUsuarioDePrueba());

    Response sinToken = anon().get(ADMIN_SESIONES + "/" + sesionId);
    Response observer = as(observerToken()).get(ADMIN_SESIONES + "/" + sesionId);
    Response coordinator = as(tokenFor("COORDINATOR")).get(ADMIN_SESIONES + "/" + sesionId);
    Response admin = as(tokenFor("ADMIN")).get(ADMIN_SESIONES + "/" + sesionId);
    Response master = as(masterToken()).get(ADMIN_SESIONES + "/" + sesionId);

    assertErrorShape(sinToken, 401);
    assertErrorShape(observer, 403);
    assertThat(coordinator.statusCode()).as("body: %s", coordinator.asString()).isEqualTo(200);
    assertThat(admin.statusCode()).as("body: %s", admin.asString()).isEqualTo(200);
    assertThat(master.statusCode()).as("body: %s", master.asString()).isEqualTo(200);
    assertThat(master.jsonPath().getString("publicId")).isEqualTo(sesionId);
  }

  @Test
  @DisplayName("DELETE /admin/sesiones/{sesionId} lo autoriza sólo el MASTER: los otros roles reciben 403")
  void revocarSesionLoAutorizaSoloElMaster() {
    String sesionId = sesionIdDe(emailDeUsuarioDePrueba());

    Response sinToken = anon().delete(ADMIN_SESIONES + "/" + sesionId);
    Response observer = as(observerToken()).delete(ADMIN_SESIONES + "/" + sesionId);
    Response coordinator = as(tokenFor("COORDINATOR")).delete(ADMIN_SESIONES + "/" + sesionId);
    Response admin = as(tokenFor("ADMIN")).delete(ADMIN_SESIONES + "/" + sesionId);

    assertErrorShape(sinToken, 401);
    assertErrorShape(observer, 403);
    assertErrorShape(coordinator, 403);
    assertErrorShape(admin, 403);

    Response master = as(masterToken()).delete(ADMIN_SESIONES + "/" + sesionId);

    assertThat(master.statusCode()).as("body: %s", master.asString()).isEqualTo(204);
    // La sesión revocada ya no está entre las activas.
    List<Map<String, Object>> activas =
        as(masterToken()).get(ADMIN_SESIONES).jsonPath().getList("data");
    assertThat(activas)
        .as("la sesión revocada no aparece en el listado de sesiones activas")
        .noneMatch(sesion -> sesionId.equals(sesion.get("publicId")));
  }

  // -----------------------------------------------------------------------------------------------
  // La regla del ADMIN: nunca escribe
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("El ADMIN recibe 403 en todos los endpoints mutantes con guarda de método")
  void elAdminNuncaEscribeEnNingunEndpointMutante() {
    String admin = tokenFor("ADMIN");
    String observer = observerToken();
    Map<String, Object> bodyDeActa = bodyDeActaValido(observer);

    // Módulo auth.
    assertProhibido(
        as(admin)
            .body(
                json(
                    Map.of(
                        "nombre", unique("nombre"),
                        "apellido", unique("apellido"),
                        "password", TEST_PASSWORD,
                        "rol", "OBSERVER")))
            .post(AUTH + "/register"),
        "POST /auth/register");
    // Módulo observer: todos exigen acta:upload.
    assertProhibido(
        as(admin).body(json(bodyDeActa)).post(OBSERVER + "/actas"), "POST /observer/actas");
    assertProhibido(
        as(admin).body(json(bodyDeActa)).post(OBSERVER + "/actas/borrador"),
        "POST /observer/actas/borrador");
    assertProhibido(
        as(admin).post(OBSERVER + "/actas/" + ACTA_INEXISTENTE + "/enviar"),
        "POST /observer/actas/{id}/enviar");
    assertProhibido(
        as(admin)
            .body(json(bodyDeActa))
            .put(OBSERVER + "/actas/" + ACTA_INEXISTENTE + "/corregir"),
        "PUT /observer/actas/{id}/corregir");
    assertProhibido(
        subirImagen(as(admin), OBSERVER + "/actas/" + ACTA_INEXISTENTE + "/imagen"),
        "POST /observer/actas/{id}/imagen");
    assertProhibido(
        as(admin)
            .body(
                json(
                    Map.of(
                        "jrvId", jrvIdDe("JRV-SE-001", observer),
                        "tipo", "JRV_NO_INSTALADA",
                        "descripcion", unique("incidencia de ensayo"))))
            .post(OBSERVER + "/incidencias"),
        "POST /observer/incidencias");
    // Módulo coordinator: los que sí tienen guarda de método.
    assertProhibido(
        as(admin)
            .body(json(Map.of("motivo", unique("motivo de anulacion"))))
            .delete(COORDINATOR + "/actas/" + ACTA_INEXISTENTE),
        "DELETE /coordinator/actas/{id}");
    assertProhibido(
        subirImagen(as(admin), COORDINATOR + "/actas/" + ACTA_INEXISTENTE + "/imagen"),
        "POST /coordinator/actas/{id}/imagen");
    // Módulo de coordinación: los tres endpoints mutantes llevan guard de método propio
    // (hasAnyRole('COORDINATOR','MASTER')), así que el ADMIN no puede escribir en ninguno.
    assertProhibido(
        as(admin)
            .body(json(Map.of("motivo", unique("motivo de disputa"))))
            .put(COORDINATOR + "/actas/" + ACTA_INEXISTENTE + "/disputar"),
        "PUT /coordinator/actas/{id}/disputar");
    // Ojo: 'corregir' toma un ActaRequestDto, no un motivo. Con un body inválido la
    // validación del bean corre ANTES que la autorización y la respuesta sería 400, no 403.
    assertProhibido(
        as(admin)
            .body(json(bodyDeActaValido(observer)))
            .put(COORDINATOR + "/actas/" + ACTA_INEXISTENTE + "/corregir"),
        "PUT /coordinator/actas/{id}/corregir");
    assertProhibido(
        as(admin).put(COORDINATOR + "/actas/" + ACTA_INEXISTENTE + "/aprobar"),
        "PUT /coordinator/actas/{id}/aprobar");
    assertProhibido(
        as(admin)
            .body(json(Map.of("motivo", unique("motivo de anulacion"))))
            .delete(COORDINATOR + "/actas/" + ACTA_INEXISTENTE),
        "DELETE /coordinator/actas/{actaId}");
    // Módulo de evidencias.
    assertProhibido(
        subirEvidencia(as(admin), ACTA_INEXISTENTE), "POST /evidencias");
    assertProhibido(
        as(admin)
            .body(json(Map.of("estado", "APROBADA")))
            .patch(EVIDENCIAS + "/" + EVIDENCIA_INEXISTENTE + "/estado"),
        "PATCH /evidencias/{id}/estado");
    assertProhibido(
        as(admin)
            .body(json(Map.of("tipos", List.of("FOTO_BORROSA"))))
            .patch(EVIDENCIAS + "/" + EVIDENCIA_INEXISTENTE + "/observaciones"),
        "PATCH /evidencias/{id}/observaciones");
    assertProhibido(
        as(admin).delete(EVIDENCIAS + "/" + EVIDENCIA_INEXISTENTE), "DELETE /evidencias/{id}");
    // Módulo de usuarios y sesiones.
    assertProhibido(
        as(admin)
            .body(
                json(
                    Map.of(
                        "nombre", unique("nombre"),
                        "apellido", unique("apellido"),
                        "rolNombre", "ADMIN",
                        "habilitado", true)))
            .put(ADMIN_USUARIOS + "/" + EVIDENCIA_INEXISTENTE),
        "PUT /admin/usuarios/{publicId}");
    assertProhibido(
        as(admin).delete(ADMIN_USUARIOS + "/" + EVIDENCIA_INEXISTENTE),
        "DELETE /admin/usuarios/{publicId}");
    assertProhibido(
        as(admin).delete(ADMIN_SESIONES + "/" + EVIDENCIA_INEXISTENTE),
        "DELETE /admin/sesiones/{sesionId}");
  }

  @Test
  @DisplayName("el ADMIN no entra al módulo de coordinación: 403 en el buzón, el detalle y el historial")
  void elAdminNoEntraAlModuloDeCoordinacion() {
    // El guard de clase del coordinator es hasAnyRole('COORDINATOR','MASTER'), que reproduce el
    // roleGuard del frontend: /coordinator/actas solo existe para COORDINATOR y MASTER. El ADMIN
    // lee por /api/v1/dashboard, que tiene su propio guard.
    String observer = observerToken();
    ActaDePrueba acta = registrarActa(observer);
    String admin = tokenFor("ADMIN");

    assertProhibido(as(admin).get(COORDINATOR + "/actas"), "GET /coordinator/actas");
    assertProhibido(
        as(admin).get(COORDINATOR + "/actas/" + acta.id()), "GET /coordinator/actas/{actaId}");
    assertProhibido(
        as(admin).get(COORDINATOR + "/actas/" + acta.id() + "/historial"),
        "GET /coordinator/actas/{actaId}/historial");
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers del módulo
  // -----------------------------------------------------------------------------------------------

  /** Acta de prueba con el par (JRV, cargo) reservado y el body con el que se registró. */
  private record ActaDePrueba(long id, long jrvId, long cargoId, Map<String, Object> body) {}

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

  /**
   * Guarda un borrador como OBSERVER sobre un par (JRV, cargo) libre y lo deja anotado para la
   * limpieza del final del test.
   */
  private static ActaDePrueba registrarBorrador(String observerToken) {
    Map<String, Object> combo = comboLibre(observerToken);
    List<Long> candidatos = candidatosDe(cargoId(combo), observerToken);
    Map<String, Object> body = bodyDeActa(jrvId(combo), cargoId(combo), candidatos, true);

    Response r = as(observerToken).body(json(body)).post(OBSERVER + "/actas/borrador");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    assertThat(r.jsonPath().getString("estado")).isEqualTo("BORRADOR");
    long actaId = r.jsonPath().getLong("id");
    registrarActaCreada(actaId, observerToken, body);
    return new ActaDePrueba(actaId, jrvId(combo), cargoId(combo), body);
  }

  /**
   * Body de acta estructuralmente válido, sin reservar ningún par (JRV, cargo) del pool.
   *
   * <p>Los probes de 403 sólo necesitan que el body pase la validación: la autorización se resuelve
   * después de la validación y antes de tocar el acta. Usar un par libre acá lo dejaría reservado
   * en memoria para siempre (la reserva sólo se libera cuando un acta llega a ANULADA).
   */
  private static Map<String, Object> bodyDeActaValido(String token) {
    long jrvId = jrvIdDe("JRV-SE-001", token);
    long cargoId = primerCargoDe(jrvId, token);
    return bodyDeActa(jrvId, cargoId, candidatosDe(cargoId, token), true);
  }

  /** Id interno del primer cargo de una JRV. */
  private static long primerCargoDe(long jrvId, String token) {
    for (Map<String, Object> cargo : listar(as(token).get(OBSERVER + "/jrv/" + jrvId + "/cargos"))) {
      return ((Number) cargo.get("id")).longValue();
    }
    throw new AssertionError("La JRV " + jrvId + " no tiene cargos en el seed del backend");
  }

  /** Estado del acta tal como lo ve el módulo coordinator. */
  private static String estadoEnCoordinator(String coordinator, long actaId) {
    Response r = as(coordinator).get(COORDINATOR + "/actas/" + actaId);
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    return r.jsonPath().getString("estado");
  }

  /** POST multipart de una imagen, con la parte que el endpoint espera. */
  private static Response subirImagen(RequestSpecification spec, String path) {
    return spec.contentType("multipart/form-data")
        .multiPart("imagen", "acta.png", PNG_1X1, "image/png")
        .post(path);
  }

  /** POST multipart de una evidencia, con la parte {@code file} que el endpoint espera. */
  private static Response subirEvidencia(RequestSpecification spec, long actaId) {
    return spec.contentType("multipart/form-data")
        .multiPart("file", "evidencia.png", PNG_1X1, "image/png")
        .post(EVIDENCIAS + "?actaId=" + actaId);
  }

  /** Sube una evidencia válida y devuelve su UUID público. */
  private String subirEvidenciaValida(String token, long actaId) {
    Response r = subirEvidencia(as(token), actaId);
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(201);
    String id = r.jsonPath().getString("id");
    evidenciasCreadas.add(id);
    return id;
  }

  /** Crea un usuario descartable, abre su sesión y devuelve su token. */
  private static String tokenDeUsuarioDePrueba() {
    String email = registerUser("ADMIN");
    return login(email, TEST_PASSWORD);
  }

  /** Crea un usuario descartable con sesión activa y devuelve su email. */
  private static String emailDeUsuarioDePrueba() {
    String email = registerUser("ADMIN");
    // El login abre la sesión que después se revoca o se consulta.
    login(email, TEST_PASSWORD);
    return email;
  }

  /** Id público (UUID) del usuario autenticado. */
  private static String publicIdDe(String token) {
    Response r = as(token).get(AUTH + "/me");
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    return r.jsonPath().getString("id");
  }

  /** Id público de la sesión activa del usuario con ese email. */
  private static String sesionIdDe(String email) {
    Response r = as(masterToken()).get(ADMIN_SESIONES);
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> sesiones = r.jsonPath().getList("data");
    for (Map<String, Object> sesion : sesiones) {
      if (email.equals(sesion.get("usuarioEmail"))) {
        return (String) sesion.get("publicId");
      }
    }
    throw new AssertionError("No hay sesión activa para el usuario " + email);
  }

  /** Afirma que el endpoint le cerró la puerta al ADMIN: 403 con el shape estándar de error. */
  private static void assertProhibido(Response r, String endpoint) {
    assertThat(r.statusCode())
        .as("acceso denegado en %s: body: %s", endpoint, r.asString())
        .isEqualTo(403);
    assertErrorShape(r, 403);
  }

  /**
   * Borra las evidencias que el test subió, para no dejar fotos huérfanas colgando de un acta que
   * la limpieza va a anular. Es best-effort a propósito: el borrado como tal lo afirma su propio
   * test.
   */
  @AfterEach
  void borrarEvidenciasCreadas() {
    for (String id : evidenciasCreadas) {
      as(masterToken()).delete(EVIDENCIAS + "/" + id);
    }
    evidenciasCreadas.clear();
  }
}
