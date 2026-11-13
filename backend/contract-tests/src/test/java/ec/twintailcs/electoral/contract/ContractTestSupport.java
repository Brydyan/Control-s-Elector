package ec.twintailcs.electoral.contract;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base compartida de la suite de regresión por contrato.
 *
 * <p>Reglas que esta clase hace posibles:
 *
 * <ul>
 *   <li>La suite habla <b>solo HTTP</b>. Ningún test importa tipos de {@code
 *       ec.twintailcs.electoral} ni de Spring: el módulo {@code backend/contract-tests} no declara
 *       esas dependencias. Esa es la razón de existir del módulo — ver su {@code pom.xml}.
 *   <li>El backend bajo prueba se elige por URL, no por wiring. La misma suite corre contra el
 *       backend Spring Boot hoy y contra el backend Quarkus mañana.
 *   <li>Los datos que el test crea llevan un sufijo único por corrida, para que la suite sea
 *       re-ejecutable sobre la misma base sin limpiar nada.
 * </ul>
 *
 * <p>Configuración por variables del sistema (o de entorno):
 *
 * <ul>
 *   <li>{@code contract.baseUrl} / {@code CONTRACT_BASE_URL} — URL del backend. Default {@code
 *       http://localhost:8083} (stack de desarrollo local).
 *   <li>{@code contract.master.email} / {@code CONTRACT_MASTER_EMAIL} — email del MASTER seedeado.
 *       Default {@code admsismas@control.ttcs} (derivado por el {@code DataSeeder}: 3 letras de
 *       nombre + 3 de apellido + 3 de rol).
 *   <li>{@code contract.master.password} / {@code CONTRACT_MASTER_PASSWORD} — password del MASTER
 *       seedeado. Default {@code admin12345} ({@code app.seed.admin-password}).
 * </ul>
 */
public abstract class ContractTestSupport {

  /** URL del backend vivo contra el que se corre la suite. */
  public static final String BASE_URL = resolveBaseUrl();

  /** Mapper Jackson 3 — misma versión y reglas de formato que usa el backend. */
  protected static final ObjectMapper JSON = JsonMapper.builder().build();

  // El admin de bootstrap lo crea la migracion `026-admin-inicial.sql`. Antes
  // lo creaba el `DataSeeder`, que generaba el email con `EmailGenerator`
  // (`admsismas@control.ttcs` para nombre/apellido/rol por defecto); ese seeder
  // se borro porque insertaba un admin nuevo en cada arranque. Con la
  // migracion, el email es el declarado en el changelog. Este default tiene que
  // seguirlo, o el login MASTER falla con 401 y los 31 tests de la suite caen.
  private static final String MASTER_EMAIL =
      setting("contract.master.email", "CONTRACT_MASTER_EMAIL", "admin@control-elector.local");
  private static final String MASTER_PASSWORD =
      setting("contract.master.password", "CONTRACT_MASTER_PASSWORD", "admin12345");

  /** Password de los usuarios que la suite registra para poder iniciar sesión. */
  protected static final String TEST_PASSWORD = "ContractTest123";

  /** Tokens por rol, resueltos una vez por JVM. */
  private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

  /** Sufijo único por corrida: hace re-ejecutable la suite sobre la misma base. */
  private static final String RUN_ID =
      randomLetters(3) + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000));

  private static final AtomicInteger UNIQUE_COUNTER = new AtomicInteger(0);

  @BeforeAll
  static void configureRestAssured() {
    RestAssured.baseURI = BASE_URL;
    RestAssured.requestSpecification =
        new RequestSpecBuilder().setContentType(ContentType.JSON).build();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  // ---------------------------------------------------------------------------------------------
  // Requests
  // ---------------------------------------------------------------------------------------------

  /** Request sin autenticar. */
  protected static RequestSpecification anon() {
    return given();
  }

  /** Request con un access token bearer. */
  protected static RequestSpecification as(String token) {
    return given().header("Authorization", "Bearer " + token);
  }

  /** Serializa un body a JSON. */
  protected static String json(Object body) {
    return JSON.writeValueAsString(body);
  }

  /** Lee una respuesta JSON como lista de objetos. Soporta PagedResponse.content o array directo. Falla si no es 200. */
  protected static List<Map<String, Object>> listar(Response r) {
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    Object root = r.jsonPath().get("$");

    // Si root es una List, es un array directo (no paginado)
    if (root instanceof List) {
      return (List<Map<String, Object>>) root;
    }

    // Si es un Map, es PagedResponse con .content; acceder solo a content, no a root
    if (root instanceof Map) {
      List<Map<String, Object>> content = r.jsonPath().getList("content");
      return content != null ? content : new ArrayList<>();
    }

    // Si no es ni List ni Map, devolver lista vacía
    return new ArrayList<>();
  }

  // ---------------------------------------------------------------------------------------------
  // Autenticación
  // ---------------------------------------------------------------------------------------------

  /** Hace login y devuelve el access token. Falla la suite si las credenciales no son válidas. */
  protected static String login(String email, String password) {
    Response r =
        anon()
            .body(json(Map.of("email", email, "password", password)))
            .post("/api/v1/auth/login");
    assertThat(r.statusCode())
        .as("login de %s debe devolver 200, body: %s", email, r.asString())
        .isEqualTo(200);
    return r.jsonPath().getString("accessToken");
  }

  /**
   * Token del usuario MASTER seedeado. Es el único usuario que existe sin depender del registro, y
   * el único que puede registrar a los demás.
   */
  protected static String masterToken() {
    return TOKENS.computeIfAbsent("MASTER", k -> login(MASTER_EMAIL, MASTER_PASSWORD));
  }

  /**
   * Registra un usuario nuevo con el rol dado y devuelve su email. El backend genera el email a
   * partir de nombre + apellido + rol, con sufijo numérico si ya existe, así que el email real se
   * lee de la respuesta.
   */
  protected static String registerUser(String rol) {
    String nombre = "C" + randomLetters(2);
    String apellido = "T" + randomLetters(2);
    Response r =
        as(masterToken())
            .body(
                json(
                    Map.of(
                        "nombre", nombre,
                        "apellido", apellido,
                        "password", TEST_PASSWORD,
                        "rol", rol)))
            .post("/api/v1/auth/register");
    assertThat(r.statusCode())
        .as("register de rol %s debe devolver 201, body: %s", rol, r.asString())
        .isEqualTo(201);
    return r.jsonPath().getString("email");
  }

  /**
   * Token de un usuario con el rol dado. Crea el usuario si hace falta y lo reutiliza durante toda
   * la corrida.
   */
  protected static String tokenFor(String rol) {
    return TOKENS.computeIfAbsent(rol, k -> login(registerUser(k), TEST_PASSWORD));
  }

  /**
   * Token de un OBSERVER recién creado, exclusivo de un test.
   *
   * <p>A diferencia de {@link #tokenFor(String)}, este NO se memoiza: cada test que necesita un
   * observer propio (por ejemplo para probar el aislamiento entre dos observers) tiene que tener
   * uno distinto.
   */
  protected static String observerToken() {
    return login(registerUser("OBSERVER"), TEST_PASSWORD);
  }

  // ---------------------------------------------------------------------------------------------
  // Datos únicos
  // ---------------------------------------------------------------------------------------------

  /**
   * Nombre único por corrida para entidades que el test crea. Permite re-ejecutar la suite sobre la
   * misma base sin que las entidades de la corrida anterior choquen.
   */
  protected static String unique(String base) {
    return base + " " + RUN_ID + "-" + UNIQUE_COUNTER.incrementAndGet();
  }

  // ---------------------------------------------------------------------------------------------
  // Datos electorales: JRVs, cargos, candidatos y ciclo de vida de las actas de prueba
  // ---------------------------------------------------------------------------------------------

  /** Prefijo del módulo de observador. */
  protected static final String OBSERVER = "/api/v1/observer";

  /** Prefijo del módulo de coordinación. */
  protected static final String COORDINATOR = "/api/v1/coordinator";

  /** Pares (JRV, cargo) ya reservados por esta corrida. */
  private static final Set<String> COMBOS_RESERVADOS = ConcurrentHashMap.newKeySet();

  /** Actas que el test en curso creó, para devolverlas a ANULADA al terminar. */
  private static final List<ActaCreada> ACTAS_CREADAS = new ArrayList<>();

  /** Acta creada por el test en curso, con lo necesario para poder liberarla. */
  protected record ActaCreada(long id, String token, Map<String, Object> body) {}

  /**
   * Reserva el primer par (JRV, cargo) sin acta activa que no haya sido tomado por otro test de
   * esta corrida.
   *
   * <p>Es lo que permite que la suite sea re-ejecutable sobre la misma base: la unicidad de un acta
   * es (JRV, cargo), así que un acta activa bloquea su par para siempre. Cada test toma un par
   * distinto y {@link #liberarActasCreadas()} los devuelve al final.
   */
  protected static Map<String, Object> comboLibre(String token) {
    for (Map<String, Object> jrv : listar(as(token).get(OBSERVER + "/jrvs"))) {
      long jrvId = ((Number) jrv.get("id")).longValue();
      for (Map<String, Object> cargo :
          listar(as(token).get(OBSERVER + "/jrv/" + jrvId + "/cargos"))) {
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

  /** ID interno de la JRV de un combo reservado. */
  protected static long jrvId(Map<String, Object> combo) {
    return ((Number) combo.get("jrvId")).longValue();
  }

  /**
   * Devuelve al pool un par (JRV, cargo) reservado que el test no llegó a usar.
   *
   * <p>{@link #comboLibre(String)} reserva el par en memoria para que otro test de la misma corrida
   * no lo tome. La reserva se libera sola cuando el acta que lo ocupa llega a ANULADA, pero un test
   * que reserva un par y <b>no</b> crea un acta (porque el backend rechazó el registro) tiene que
   * devolverlo a mano: si no, el pool se encoge en cada corrida y la suite se satura con el mensaje
   * "No queda ningún par JRV+cargo sin acta activa".
   */
  protected static void liberarComboReservado(Map<String, Object> combo) {
    COMBOS_RESERVADOS.remove(jrvId(combo) + "-" + cargoId(combo));
  }

  /** ID interno del cargo de un combo reservado. */
  protected static long cargoId(Map<String, Object> combo) {
    return ((Number) combo.get("cargoId")).longValue();
  }

  /** ID interno de la JRV con ese número. */
  protected static long jrvIdDe(String numero, String token) {
    for (Map<String, Object> jrv : listar(as(token).get(OBSERVER + "/jrvs"))) {
      if (numero.equals(jrv.get("numero"))) {
        return ((Number) jrv.get("id")).longValue();
      }
    }
    throw new AssertionError("La JRV " + numero + " no existe en el seed del backend");
  }

  /** IDs de los candidatos de un cargo. */
  protected static List<Long> candidatosDe(long cargoId, String token) {
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
  protected static Map<String, Object> bodyDeActa(
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
  protected static long sumaDeVotos(int cantidadDeCandidatos) {
    long suma = 0;
    for (int i = 0; i < cantidadDeCandidatos; i++) {
      suma += 10L * (i + 1);
    }
    return suma;
  }

  /**
   * Mensaje exacto que el backend devuelve cuando la suma de votos no cierra con el total de
   * sufragantes.
   *
   * <p>El mensaje es parte del contrato: el frontend lo muestra al observer tal cual, así que su
   * forma exacta ({@code ≠}, el orden de los sumandos, el nombre de cada campo) no puede cambiar
   * sin romper la aplicación. Los números salen del propio body que se mandó —no de constantes
   * sueltas— para que el assert no pueda quedar viejo si el helper de armado cambia.
   */
  protected static String mensajeDeSumaDescuadrada(Map<String, Object> body) {
    int suma = 0;
    Object detalles = body.get("detalles");
    if (detalles instanceof List<?> lista) {
      for (Object item : lista) {
        if (item instanceof Map<?, ?> detalle) {
          suma += ((Number) detalle.get("votos")).intValue();
        }
      }
    }
    int blancos = ((Number) body.get("votosBlancos")).intValue();
    int nulos = ((Number) body.get("votosNulos")).intValue();
    int total = suma + blancos + nulos;
    int enviado = ((Number) body.get("totalSufragantes")).intValue();
    return "Los votos no cuadran. Candidatos="
        + suma
        + " + Blancos="
        + blancos
        + " + Nulos="
        + nulos
        + " = "
        + total
        + " ≠ "
        + enviado
        + " (total sufragantes)";
  }

  /** Registra un acta creada por el test para que la limpieza la libere al terminar. */
  protected static void registrarActaCreada(long id, String token, Map<String, Object> body) {
    ACTAS_CREADAS.add(new ActaCreada(id, token, body));
  }

  /** Estado de un acta tal como lo ve su autor. */
  protected static String estadoDeActa(String token, long actaId) {
    Response r = as(token).get(OBSERVER + "/actas/" + actaId);
    if (r.statusCode() != 200) {
      return null;
    }
    return r.jsonPath().getString("estado");
  }

  /**
   * Devuelve a ANULADA las actas que el test creó.
   *
   * <p>Un acta activa bloquea su par (JRV, cargo) para siempre: sin esto la suite se puede correr
   * una sola vez sobre una base dada. La única forma de liberar el par es dejar el acta en {@code
   * ANULADA}, y para llegar ahí hay que recorrer la máquina de estados.
   *
   * <p>No es parte del contrato de ningún módulo: usa endpoints de observer, coordinator y master
   * sólo como infraestructura, y no afirma nada sobre sus respuestas.
   */
  @AfterEach
  void liberarActasCreadas() {
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
      estado = estadoDeActa(acta.token(), acta.id());
    }
    if ("ANULADA".equals(estado)) {
      // El acta quedó anulada: el par (JRV, cargo) vuelve al pool. Sin esto el set en memoria
      // queda más chico que la base y la suite se satura al quinto test con acta.
      liberarCombo(acta.body());
    }
  }

  /**
   * Devuelve el par (JRV, cargo) al pool de reservas.
   *
   * <p>Solo se hace si el acta llegó a ANULADA: un acta que no se pudo liberar sigue bloqueando su
   * par en la base, así que dejarlo reservado es lo conservative.
   */
  private static void liberarCombo(Map<String, Object> body) {
    if (body == null) {
      return;
    }
    Object jrv = body.get("jrvId");
    Object cargo = body.get("cargoId");
    if (jrv == null || cargo == null) {
      return;
    }
    COMBOS_RESERVADOS.remove(jrv + "-" + cargo);
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

  // ---------------------------------------------------------------------------------------------
  // Contrato de error (compartido por todos los módulos)
  // ---------------------------------------------------------------------------------------------

  /**
   * Verifica el shape estándar del error del backend.
   *
   * <p>El backend responde con {@code {timestamp, status, error, message}} y, en errores de
   * validación de bean, agrega {@code fields}. Esta forma la produce el
   * {@code GlobalExceptionHandler} y el {@code SecurityConfig} del backend Spring; la fase 2 de la
   * migración (#113) debe reproducirla exactamente.
   */
  protected static void assertErrorShape(Response response, int expectedStatus) {
    assertThat(response.statusCode())
        .as("status esperado %s, body: %s", expectedStatus, response.asString())
        .isEqualTo(expectedStatus);
    assertThat(response.jsonPath().getString("timestamp"))
        .as("el error debe traer timestamp: %s", response.asString())
        .isNotBlank();
    assertThat(response.jsonPath().getInt("status"))
        .as("el campo status del body debe coincidir con el HTTP status: %s", response.asString())
        .isEqualTo(expectedStatus);
    assertThat(response.jsonPath().getString("error"))
        .as("el error debe traer error: %s", response.asString())
        .isNotBlank();
    assertThat(response.jsonPath().getString("message"))
        .as("el error debe traer message: %s", response.asString())
        .isNotBlank();
  }

  // ---------------------------------------------------------------------------------------------
  // Internos
  // ---------------------------------------------------------------------------------------------

  /** Resuelve un setting desde variable de sistema, luego variable de entorno, luego default. */
  private static String setting(String systemProperty, String envVar, String fallback) {
    String value = System.getProperty(systemProperty);
    if (value == null || value.isBlank()) {
      value = System.getenv(envVar);
    }
    return (value == null || value.isBlank()) ? fallback : value;
  }

  private static String resolveBaseUrl() {
    return setting("contract.baseUrl", "CONTRACT_BASE_URL", "http://localhost:8083");
  }

  private static String randomLetters(int length) {
    String alphabet = "abcdefghijklmnopqrstuvwxyz";
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
    }
    return sb.toString();
  }
}
