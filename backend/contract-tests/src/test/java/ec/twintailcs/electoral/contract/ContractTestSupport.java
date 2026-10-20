package ec.twintailcs.electoral.contract;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
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

  private static final String MASTER_EMAIL =
      setting("contract.master.email", "CONTRACT_MASTER_EMAIL", "admsismas@control.ttcs");
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
