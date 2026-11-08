package ec.twintailcs.electoral.auth.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailAEnviar;
import ec.twintailcs.electoral.auth.infrastructure.email.RecordingEmailService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flujo de invitacion de una persona (T-AUTH-INV-02).
 *
 * <p>Verifica lo que el SDD pide, sobre una base real y con el RLS de verdad:
 *
 * <ul>
 *   <li>el MASTER invita y la cuenta queda creada, pendiente de activacion, con el login generado y
 *       el correo de contacto guardado;
 *   <li>la invitacion queda registrada con quien invito, desde que IP y cuando expira;
 *   <li>el email sale al correo de contacto (no al login) y lleva el login dentro, que es lo que la
 *       persona necesita para entrar;
 *   <li>un rol sin permiso recibe 403;
 *   <li>la segunda invitacion dentro de 5 segundos recibe 429 con {@code Retry-After};
 *   <li>un email invalido recibe 400 y no crea nada.
 * </ul>
 */
@QuarkusTest
@TestProfile(UserInvitationTestProfile.class)
class UserInvitationIT {

  private static final String NOMBRE = "Juan";
  private static final String APELLIDO = "García";
  private static final String EMAIL_PERSONAL = "juan.garcia@example.com";

  /**
   * IP por test.
   *
   * <p>El limitador de invitaciones es por IP y vive en toda la aplicacion de test: si dos tests
   * usaran la misma, el segundo recibiria el 429 del primero y los dos fallos serian del otro.
   */
  private static final String IP_CUENTA = ip("7");

  private static final String IP_REGISTRO = ip("8");

  private static final String IP_EMAIL = ip("9");

  private static final String IP_SIN_PERMISO = ip("10");

  private static final String IP_EMAIL_INVALIDO = ip("11");

  private static final String IP_LIMITE = ip("12");

  private static final String IP_UNICIDAD_A = ip("13");

  private static final String IP_UNICIDAD_B = ip("14");

  private static final String IP_ACTIVACION = ip("15");

  /**
   * Arma una IP de test con un segundo octeto aleatorio.
   *
   * <p>El octeto aleatorio hace que cada corrida use direcciones distintas: las pruebas que
   * comprueban que NO queda ninguna invitacion (un 403, un email invalido) cuentan las filas de SU
   * IP, y con una IP fija una corrida anterior dejaria filas que hacen fallar la de ahora.
   */
  private static String ip(String ultimo) {
    return "198.51."
        + java.util.concurrent.ThreadLocalRandom.current().nextInt(10, 200)
        + "."
        + ultimo;
  }

  @Inject PasswordHasher hasher;
  @Inject JwtService jwt;
  @Inject EntityManager em;

  private String tokenMaster;
  private Long masterId;

  @BeforeEach
  void crearMasterDePrueba() {
    RecordingEmailService.limpiar();

    Usuario master =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Rol rol = Rol.find("nombre", "MASTER").firstResult();
                  Usuario u = new Usuario();
                  u.setEmail("master-" + UUID.randomUUID() + "@test.local");
                  u.setNombre("Master");
                  u.setApellido("Prueba");
                  u.setPasswordHash(hasher.hash("clave-de-prueba-123"));
                  u.setRol(rol);
                  u.setHabilitado(true);
                  u.persist();
                  return u;
                });

    masterId = master.getId();
    tokenMaster = tokenDe(master, "MASTER");
  }

  // -------------------------------------------------------------------------
  // Escenario 1: la invitacion crea la cuenta, la registra y manda el email
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("el MASTER invita y la cuenta queda creada con su login y su email de contacto")
  void elMasterInvitaYLaCuentaQuedaCreada() {
    Response res = invitarDesde(IP_CUENTA, "García");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.jsonPath().getString("username"))
        .as("el login se genera con nombre, apellido y rol")
        .matches("juagarobs\\d*@control\\.ttcs");
    assertThat(res.jsonPath().getString("username"))
        .as("y sale del dominio institucional")
        .endsWith("@control.ttcs");
    assertThat(res.jsonPath().getString("emailPersonal")).isEqualTo(EMAIL_PERSONAL);
    assertThat(res.jsonPath().getString("status")).isEqualTo("INVITED");
    assertThat(res.jsonPath().getString("usuarioId")).isNotBlank();

    List<Object[]> usuarios =
        leer(
            "select email, email_personal, estado_usuario from usuario where email = '"
                + res.jsonPath().getString("username")
                + "'");

    assertThat(usuarios).hasSize(1);
    assertThat(usuarios.get(0)[1]).isEqualTo(EMAIL_PERSONAL);
    assertThat(usuarios.get(0)[2])
        .as("la cuenta nace esperando la activacion")
        .isEqualTo("PENDIENTE_INVITACION");
  }

  @Test
  @DisplayName("la invitacion queda registrada con quien invito, desde que IP y cuando expira")
  void laInvitacionQuedaRegistrada() {
    invitarDesde(IP_REGISTRO, "Ruiz");

    List<Object[]> filas =
        leer(
            "select email_personal, invited_by, status, ip_address, expiry_at from user_invitations order by id desc limit 1");

    assertThat(filas).hasSize(1);
    assertThat(filas.get(0)[0]).isEqualTo(EMAIL_PERSONAL);
    assertThat(filas.get(0)[1]).as("la invitacion queda atribuida al MASTER").isEqualTo(masterId);
    assertThat(filas.get(0)[2]).isEqualTo("INVITED");
    assertThat(filas.get(0)[3]).isEqualTo(IP_REGISTRO);
    assertThat(filas.get(0)[4]).as("la invitacion tiene fecha de vencimiento").isNotNull();
  }

  @Test
  @DisplayName("el email sale al correo de contacto y lleva el login dentro")
  void elEmailSaleAlCorreoDeContactoYLlevaElLogin() {
    invitarDesde(IP_EMAIL, "Soto");

    List<EmailAEnviar> enviados = esperarEmails(1);

    assertThat(enviados).hasSize(1);
    EmailAEnviar email = enviados.get(0);
    assertThat(email.destinatario())
        .as("el mensaje va al correo de contacto, no al login")
        .isEqualTo(EMAIL_PERSONAL);
    assertThat(email.username())
        .as("el email dice con que credencial entra")
        .matches("juasotobs\\d*@control\\.ttcs");
    assertThat(email.activationLink()).contains("/auth/activate?token=");
  }

  @Test
  @DisplayName("el login generado es unico: dos personas iguales reciben logins distintos")
  void elLoginGeneradoEsUnico() {
    Response res = invitarDesde(IP_UNICIDAD_A, "Vega");
    assertThat(res.getStatusCode()).isEqualTo(200);

    // La segunda invitacion va desde otra IP: el limitador es por IP y aqui lo
    // que se prueba es la colision del login, no el limite.
    Response segunda = invitarDesde(IP_UNICIDAD_B, "Vega");

    assertThat(segunda.getStatusCode()).isEqualTo(200);
    assertThat(segunda.jsonPath().getString("username"))
        .as("el segundo Juan Garcia observador no puede tener el mismo login")
        .isNotEqualTo(res.jsonPath().getString("username"));
  }

  @Test
  @DisplayName("con el link del email la persona activa la cuenta y la invitacion queda ACTIVATED")
  void conElLinkDelEmailLaPersonaActivaLaCuenta() {
    Response res = invitarDesde(IP_ACTIVACION, "Castro");
    String username = res.jsonPath().getString("username");

    List<EmailAEnviar> enviados = esperarEmails(1);
    assertThat(enviados).hasSize(1);

    // El token viaja en el link, envuelto en base64url sin relleno.
    String link = enviados.get(0).activationLink();
    String token = link.substring(link.indexOf("token=") + "token=".length());

    Response activacion =
        given()
            .contentType("application/json")
            .body(
                """
                {"token":"%s","password":"ClaveSegura#2026","passwordConfirm":"ClaveSegura#2026"}
                """
                    .formatted(token))
            .when()
            .post("/api/v1/auth/activate");

    assertThat(activacion.getStatusCode()).isEqualTo(200);

    List<Object[]> estado =
        leer("select estado_usuario from usuario where email = '" + username + "'");
    assertThat(estado).hasSize(1);
    assertThat(estado.get(0)[0]).as("la cuenta queda activa").isEqualTo("ACTIVO");

    List<Object[]> invitacion =
        leer(
            "select status, activated_at from user_invitations where email_personal = '"
                + EMAIL_PERSONAL
                + "' order by id desc limit 1");
    assertThat(invitacion).hasSize(1);
    assertThat(invitacion.get(0)[0])
        .as("la invitacion que trajo a la persona queda registrada como activada")
        .isEqualTo("ACTIVATED");
    assertThat(invitacion.get(0)[1]).as("con la marca de tiempo").isNotNull();
  }

  // -------------------------------------------------------------------------
  // Escenario 5: solo MASTER puede invitar
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("un rol sin permiso recibe 403 y no se crea ninguna cuenta")
  void unRolSinPermisoRecibe403() {
    Usuario observador =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Rol rol = Rol.find("nombre", "OBSERVER").firstResult();
                  Usuario u = new Usuario();
                  u.setEmail("obs-" + UUID.randomUUID() + "@test.local");
                  u.setNombre("Obs");
                  u.setApellido("Prueba");
                  u.setPasswordHash(hasher.hash("clave-de-prueba-123"));
                  u.setRol(rol);
                  u.setHabilitado(true);
                  u.persist();
                  return u;
                });

    Response res =
        given()
            .header("Authorization", "Bearer " + tokenDe(observador, "OBSERVER"))
            .header("X-Forwarded-For", IP_SIN_PERMISO)
            .contentType("application/json")
            .body(
                """
                {"nombre":"Juan","apellido":"García","emailPersonal":"juan@example.com","rolNombre":"OBSERVER"}
                """)
            .when()
            .post("/api/v1/users/invite");

    assertThat(res.getStatusCode()).isEqualTo(403);
    assertThat(
            contar(
                "select count(*) from user_invitations where ip_address = '"
                    + IP_SIN_PERMISO
                    + "'"))
        .as("un 403 no deja invitaciones")
        .isZero();
  }

  // -------------------------------------------------------------------------
  // Escenario 2: rate limit
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("la segunda invitacion dentro de 5 segundos responde 429 con Retry-After")
  void laSegundaInvitacionDentroDeLaVentanaResponde429() {
    assertThat(invitarDesde(IP_LIMITE, "Diaz").getStatusCode()).isEqualTo(200);

    Response segunda =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .header("X-Forwarded-For", IP_LIMITE)
            .contentType("application/json")
            .body(
                """
                {"nombre":"Ana","apellido":"Ruiz","emailPersonal":"ana@example.com","rolNombre":"OBSERVER"}
                """)
            .when()
            .post("/api/v1/users/invite");

    assertThat(segunda.getStatusCode()).isEqualTo(429);
    assertThat(segunda.getHeader("Retry-After")).isNotNull();
    assertThat(Integer.parseInt(segunda.getHeader("Retry-After")))
        .isBetween(
            1, (int) ec.twintailcs.electoral.auth.security.InvitationRateLimiter.VENTANA_SEGUNDOS);
  }

  // -------------------------------------------------------------------------
  // Escenario 4: email invalido
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("un email invalido responde 400 y no crea nada")
  void unEmailInvalidoResponde400() {
    Response res =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .header("X-Forwarded-For", IP_EMAIL_INVALIDO)
            .contentType("application/json")
            .body(
                """
                {"nombre":"Juan","apellido":"García","emailPersonal":"no-es-un-email","rolNombre":"OBSERVER"}
                """)
            .when()
            .post("/api/v1/users/invite");

    assertThat(res.getStatusCode()).isEqualTo(400);
    assertThat(
            contar(
                "select count(*) from user_invitations where ip_address = '"
                    + IP_EMAIL_INVALIDO
                    + "'"))
        .as("un email invalido no deja invitaciones")
        .isZero();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Invita a "Juan &lt;apellido&gt;".
   *
   * <p>El apellido va por parametro a proposito: el login se genera a partir de nombre, apellido y
   * rol, y el generador busca el primero libre con un sufijo numerico. Si todos los tests usaran el
   * mismo apellido, en una base con historia el generador agotaria sus 100 intentos y la invitacion
   * responderia 400 por un motivo que no tiene que ver con lo que se prueba. Con un apellido por
   * test, cada uno tiene su propia secuencia.
   */
  private Response invitarDesde(String ip, String apellido) {
    return given()
        .header("Authorization", "Bearer " + tokenMaster)
        .header("X-Forwarded-For", ip)
        .header("User-Agent", "junit")
        .contentType("application/json")
        .body(
            """
            {"nombre":"Juan","apellido":"%s","emailPersonal":"%s","rolNombre":"OBSERVER"}
            """
                .formatted(apellido, EMAIL_PERSONAL))
        .when()
        .post("/api/v1/users/invite");
  }

  private String tokenDe(Usuario usuario, String rol) {
    return jwt.issue(
            usuario.getPublicId(), usuario.getEmail(), rol, List.of(), UUID.randomUUID().toString())
        .access();
  }

  /**
   * Lee del a base con el contexto de RLS del MASTER.
   *
   * <p>El contexto hace falta para las tablas con RLS: sin el, la consulta no ve filas.
   */
  private List<Object[]> leer(String sql) {
    List<?> crudo = leerCrudo(sql);
    List<Object[]> filas = new java.util.ArrayList<>();
    for (Object fila : crudo) {
      filas.add(fila instanceof Object[] array ? array : new Object[] {fila});
    }
    return filas;
  }

  /** Consulta cruda con el contexto de RLS del MASTER. */
  private List<?> leerCrudo(String sql) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              em.createNativeQuery("select set_config('app.current_user_id', :id, true)")
                  .setParameter("id", String.valueOf(masterId))
                  .getSingleResult();
              return em.createNativeQuery(sql).getResultList();
            });
  }

  /** Espera a que el worker de la cola mande el numero de emails esperado. */
  private List<EmailAEnviar> esperarEmails(int cantidad) {
    long limite = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < limite) {
      List<EmailAEnviar> enviados = RecordingEmailService.enviados();
      if (enviados.size() >= cantidad) {
        return enviados;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return RecordingEmailService.enviados();
  }

  /**
   * Cuenta filas con el contexto de RLS del MASTER.
   *
   * <p>Un {@code select count(*)} devuelve una lista con un numero, no con filas: el helper
   * contempla las dos formas.
   */
  private long contar(String sql) {
    List<?> filas = leerCrudo(sql);
    if (filas.isEmpty()) {
      return 0;
    }
    Object valor = filas.get(0);
    if (valor instanceof Number numero) {
      return numero.longValue();
    }
    if (valor instanceof Object[] fila && fila.length > 0 && fila[0] instanceof Number numero) {
      return numero.longValue();
    }
    return 0;
  }
}
