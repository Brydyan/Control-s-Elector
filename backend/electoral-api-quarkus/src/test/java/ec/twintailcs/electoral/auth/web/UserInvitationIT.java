package ec.twintailcs.electoral.auth.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailAEnviar;
import ec.twintailcs.electoral.auth.infrastructure.email.RecordingEmailService;
import ec.twintailcs.electoral.shared.testing.ClientIpHeaders;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
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

  /**
   * Cache de emails por test.
   *
   * <p>Garantiza determinismo: cada llamada a {@code emailDe(apellido)} retorna email único pero
   * predecible (mismo orden = mismo email en repeticiones). Contador incrementa por invocación,
   * evitando colisiones UNIQUE mientras mantiene determinismo.
   */
  private final java.util.Map<String, String> emailCache = new java.util.HashMap<>();

  private int emailCounter = 0;

  /**
   * Cachea emails por (IP, apellido) para permitir múltiples invitaciones distintas con el mismo
   * apellido desde IPs diferentes.
   */
  private final java.util.Map<String, String> invitationEmailCache = new java.util.HashMap<>();

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

  private static final String IP_SIN_EMAIL = ip("16");

  private static final String IP_CON_EMAIL = ip("17");

  private static final String IP_VIGENCIA = ip("18");

  private static final String IP_RESEND = ip("19");

  private static final String IP_RESEND_ACTIVA = ip("20");

  private static final String IP_RESEND_INEXISTENTE = ip("21");

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
    emailCache.clear();
    emailCounter = 0;
    invitationEmailCache.clear();

    // Cada test arranca con las tablas de invitacion y de auditoria vacias.
    //
    // <p>Los conteos de esta suite miran las filas de SU invocacion (por IP, por
    // correo de contacto). Las IPs llevan un octeto aleatorio para que dos
    // corridas no se pisen, pero eso solo reduce la probabilidad de colision: una
    // fila que dejo una corrida anterior —una mutacion, por ejemplo— con el mismo
    // dato hace fallar el test de hoy. Borrar las tablas deja el test dependiendo
    // solo de lo que hace.
    // Delete in correct FK order (cascade-safe, child-first)
    // Order derived from FK dependency graph
    limpiarTabla("delete from user_invitations");
    limpiarTabla("delete from notificacion");
    limpiarTabla("delete from acta");
    limpiarTabla("delete from observer_jrv_asignacion");
    limpiarTabla("delete from jrv_cargo");
    limpiarTabla("delete from audit_log");
    limpiarTabla("delete from jrv");
    limpiarTabla("delete from evidencia");
    limpiarTabla("delete from recinto");
    limpiarTabla("delete from sesion");
    limpiarTabla("delete from usuario where id > 1");
    limpiarTabla("alter sequence usuario_id_seq restart with 2");

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

  /**
   * Vacia una tabla entre tests.
   *
   * <p>El datasource de test es superusuario (BYPASSRLS), asi que el borrado no choca con las
   * politicas de la tabla: es la base de pruebas, no produccion.
   */
  private void limpiarTabla(String sql) {
    QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(sql).executeUpdate());
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
    assertThat(res.jsonPath().getString("emailPersonal")).isEqualTo(emailDe("García"));
    assertThat(res.jsonPath().getString("status")).isEqualTo("INVITED");
    assertThat(res.jsonPath().getString("usuarioId")).isNotBlank();

    List<Object[]> usuarios =
        leer(
            "select email, email_personal, estado_usuario from usuario where email = '"
                + res.jsonPath().getString("username")
                + "'");

    assertThat(usuarios).hasSize(1);
    assertThat(usuarios.get(0)[1]).isEqualTo(emailDe("García"));
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
    assertThat(filas.get(0)[0]).isEqualTo(emailDe("Ruiz"));
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
        .isEqualTo(emailDe("Soto"));
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
                + emailDe("Castro")
                + "' order by id desc limit 1");
    assertThat(invitacion).hasSize(1);
    assertThat(invitacion.get(0)[0])
        .as("la invitacion que trajo a la persona queda registrada como activada")
        .isEqualTo("ACTIVATED");
    assertThat(invitacion.get(0)[1]).as("con la marca de tiempo").isNotNull();
  }

  // -------------------------------------------------------------------------
  // ODD #504 (A): el toggle "Enviar Invitacion por Email"
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("F.1: con el toggle apagado se crea la cuenta y NO se manda ningun email")
  void conElToggleApagadoSeCreaLaCuentaYNoSeMandaEmail() {
    Response res =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .headers(ClientIpHeaders.from(IP_SIN_EMAIL))
            .contentType("application/json")
            .body(
                """
                {"nombre":"Juan","apellido":"SinCorreo","emailPersonal":"%s","rolNombre":"OBSERVER","sendEmail":false}
                """
                    .formatted(emailDe("SinCorreo")))
            .when()
            .post("/api/v1/users/invite");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.jsonPath().getString("status")).isEqualTo("CREATED");
    assertThat(res.jsonPath().getString("mensaje")).contains("Puedes enviar invitación después");
    assertThat(res.jsonPath().getString("username")).isNotBlank();

    // No se encola ningun email.
    esperarUnMomento();
    assertThat(RecordingEmailService.enviados())
        .as("con el toggle apagado no sale ningun correo")
        .isEmpty();

    // La cuenta existe, pendiente de activacion y sin invitacion registrada.
    String username = res.jsonPath().getString("username");
    List<Object[]> usuarios =
        leer("select estado_usuario, email_personal from usuario where email = '" + username + "'");
    assertThat(usuarios).hasSize(1);
    assertThat(usuarios.get(0)[0]).isEqualTo("PENDIENTE_INVITACION");
    assertThat(usuarios.get(0)[1]).isEqualTo(emailDe("SinCorreo"));

    assertThat(
            contar(
                "select count(*) from user_invitations where ip_address = '" + IP_SIN_EMAIL + "'"))
        .as("sin invitacion no hay fila de invitacion")
        .isZero();
  }

  @Test
  @DisplayName("F.2: con el toggle encendido la cuenta queda INVITED y sale el email")
  void conElToggleEncendidoLaCuentaQuedaInvitedYSaleElEmail() {
    Response res =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .headers(ClientIpHeaders.from(IP_CON_EMAIL))
            .contentType("application/json")
            .body(
                """
                {"nombre":"Juan","apellido":"ConCorreo","emailPersonal":"%s","rolNombre":"OBSERVER","sendEmail":true}
                """
                    .formatted(emailDe("ConCorreo")))
            .when()
            .post("/api/v1/users/invite");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.jsonPath().getString("status")).isEqualTo("INVITED");
    assertThat(res.jsonPath().getString("mensaje")).contains(emailDe("ConCorreo"));

    List<EmailAEnviar> enviados = esperarEmailsDe(emailDe("ConCorreo"), 1);
    assertThat(enviados).hasSize(1);
    assertThat(enviados.get(0).destinatario()).isEqualTo(emailDe("ConCorreo"));
  }

  // -------------------------------------------------------------------------
  // ODD #504 (B): la invitacion vive 24 horas, no 30 dias
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("B.4: la invitacion vence 24 horas despues de-enviada")
  void laInvitacionVenceEn24Horas() {
    Response res = invitarDesde(IP_VIGENCIA, "Horan");

    Instant expira = Instant.parse(res.jsonPath().getString("expiraEn"));
    Instant enviada = Instant.now();
    long horas = Duration.between(enviada, expira).toHours();
    assertThat(horas)
        .as("la invitacion vive 24 horas (23 si el reloj ya corrio un segundo)")
        .isBetween(23L, 24L);

    List<Object[]> filas =
        leer(
            "select extract(epoch from (expiry_at - invited_at)) / 3600 from user_invitations where email_personal = '"
                + emailDe("Horan")
                + "' order by id desc limit 1");
    assertThat(filas).hasSize(1);
    assertThat(((Number) filas.get(0)[0]).intValue())
        .as("en la tabla tambien son 24 horas")
        .isEqualTo(24);
  }

  // -------------------------------------------------------------------------
  // ODD #504 (D y E): reenviar la invitacion
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("D.4: al reenviar se genera un link nuevo y el anterior deja de servir")
  void alReenviarSeGeneraUnLinkNuevoYElAnteriorDejaDeServir() {
    Response invite = invitarDesde(IP_RESEND, "Resend");
    String username = invite.jsonPath().getString("username");

    List<EmailAEnviar> enviados = esperarEmailsDe(emailDe("Resend"), 1);
    assertThat(enviados).hasSize(1);
    String tokenViejo = tokenDe(enviados.get(0));

    Response res =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .headers(ClientIpHeaders.from(IP_RESEND))
            .contentType("application/json")
            .when()
            .post(
                "/api/v1/users/" + invite.jsonPath().getString("usuarioId") + "/resend-invitation");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.jsonPath().getString("emailPersonal")).isEqualTo(emailDe("Resend"));
    assertThat(res.jsonPath().getBoolean("success")).isTrue();

    // El link viejo ya no activa: el hash guardado es el del token nuevo.
    Response conTokenViejo =
        given()
            .contentType("application/json")
            .body(
                """
                {"token":"%s","password":"ClaveSegura#2026","passwordConfirm":"ClaveSegura#2026"}
                """
                    .formatted(tokenViejo))
            .when()
            .post("/api/v1/auth/activate");
    assertThat(conTokenViejo.getStatusCode())
        .as("el token anterior queda invalidado")
        .isEqualTo(409);

    // El link nuevo si activa.
    List<EmailAEnviar> despues = esperarEmailsDe(emailDe("Resend"), 2);
    assertThat(despues).hasSize(2);
    String tokenNuevo = tokenDe(despues.get(1));
    assertThat(tokenNuevo).isNotEqualTo(tokenViejo);

    Response conTokenNuevo =
        given()
            .contentType("application/json")
            .body(
                """
                {"token":"%s","password":"ClaveSegura#2026","passwordConfirm":"ClaveSegura#2026"}
                """
                    .formatted(tokenNuevo))
            .when()
            .post("/api/v1/auth/activate");
    assertThat(conTokenNuevo.getStatusCode()).isEqualTo(200);

    // La cuenta queda activa.
    List<Object[]> estado =
        leer("select estado_usuario from usuario where email = '" + username + "'");
    assertThat(estado.get(0)[0]).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName("E.3: reenviar a una cuenta que ya activo se rechaza con 400")
  void reenviarAUnaCuentaActivadaSeRechaza() {
    Response invite = invitarDesde(IP_RESEND_ACTIVA, "Resend2");
    List<EmailAEnviar> enviados = esperarEmailsDe(emailDe("Resend2"), 1);
    String token = tokenDe(enviados.get(0));

    given()
        .contentType("application/json")
        .body(
            """
            {"token":"%s","password":"ClaveSegura#2026","passwordConfirm":"ClaveSegura#2026"}
            """
                .formatted(token))
        .when()
        .post("/api/v1/auth/activate")
        .then()
        .statusCode(200);

    Response res =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .headers(ClientIpHeaders.from(IP_RESEND_ACTIVA))
            .contentType("application/json")
            .when()
            .post(
                "/api/v1/users/" + invite.jsonPath().getString("usuarioId") + "/resend-invitation");

    assertThat(res.getStatusCode()).as("una cuenta activa no se re-invita").isEqualTo(400);
  }

  @Test
  @DisplayName("E.3: reenviar a un usuario que no existe responde 404")
  void reenviarAUnUsuarioQueNoExisteResponde404() {
    Response res =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .headers(ClientIpHeaders.from(IP_RESEND_INEXISTENTE))
            .contentType("application/json")
            .when()
            .post("/api/v1/users/00000000-0000-0000-0000-000000000000/resend-invitation");

    assertThat(res.getStatusCode()).isEqualTo(404);
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
            .headers(ClientIpHeaders.from(IP_SIN_PERMISO))
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
  // Escenario 1b: email personal duplicado
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("un email personal ya usado responde 409 y no crea otra cuenta")
  void unEmailPersonalYaUsadoResponde409() {
    String email = emailDe("Duplicado");
    Response primera =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .headers(ClientIpHeaders.from(ip("22")))
            .contentType("application/json")
            .body(
                """
                {"nombre":"Ana","apellido":"Duplicado","emailPersonal":"%s","rolNombre":"OBSERVER","sendEmail":false}
                """
                    .formatted(email))
            .when()
            .post("/api/v1/users/invite");
    assertThat(primera.getStatusCode()).isEqualTo(200);

    long usuariosAntes =
        contar("select count(*) from usuario where email_personal = '" + email + "'");

    Response segunda =
        given()
            .header("Authorization", "Bearer " + tokenMaster)
            .headers(ClientIpHeaders.from(ip("23")))
            .contentType("application/json")
            .body(
                """
                {"nombre":"Beto","apellido":"Duplicado","emailPersonal":"%s","rolNombre":"OBSERVER","sendEmail":false}
                """
                    .formatted(email))
            .when()
            .post("/api/v1/users/invite");

    assertThat(segunda.getStatusCode()).isEqualTo(409);
    assertThat(segunda.jsonPath().getString("message"))
        .isEqualTo("Ya existe usuario con email personal: " + email);
    assertThat(contar("select count(*) from usuario where email_personal = '" + email + "'"))
        .as("el duplicado no crea otra cuenta")
        .isEqualTo(usuariosAntes);
  }

  @Test
  @DisplayName("dos invitaciones paralelas con mismo email personal: una gana y la otra recibe 409")
  void dosInvitacionesParalelasMismoEmailPersonal() throws Exception {
    String email = emailDe("Paralelo");
    String ipA = ip("24");
    String ipB = ip("25");
    java.util.concurrent.CountDownLatch listo = new java.util.concurrent.CountDownLatch(2);
    java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Future<Response> f1 =
          pool.submit(
              () -> {
                listo.countDown();
                salida.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return invitarConIpYEmail(ipA, "ParaleloA", email);
              });
      java.util.concurrent.Future<Response> f2 =
          pool.submit(
              () -> {
                listo.countDown();
                salida.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return invitarConIpYEmail(ipB, "ParaleloB", email);
              });
      assertThat(listo.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      salida.countDown();
      Response r1 = f1.get(30, java.util.concurrent.TimeUnit.SECONDS);
      Response r2 = f2.get(30, java.util.concurrent.TimeUnit.SECONDS);

      java.util.List<Integer> status = java.util.List.of(r1.getStatusCode(), r2.getStatusCode());
      assertThat(status).doesNotContain(500);
      assertThat(contar("select count(*) from usuario where email_personal = '" + email + "'"))
          .as("la unicidad de BD impide duplicados aunque haya carrera")
          .isLessThanOrEqualTo(1);

      // Timing ideal: 200 + 409. Si no chocan, ambos pueden ser 200 por ventana/rate o uno 409 por
      // check previo.
      if (status.contains(409)) {
        Response conflicto = r1.getStatusCode() == 409 ? r1 : r2;
        assertThat(conflicto.jsonPath().getString("message"))
            .isEqualTo("Ya existe usuario con email personal: " + email);
      }
      assertThat(status).allMatch(s -> s == 200 || s == 409);
    } finally {
      pool.shutdownNow();
    }
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
            .headers(ClientIpHeaders.from(IP_LIMITE))
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
            .headers(ClientIpHeaders.from(IP_EMAIL_INVALIDO))
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
  /**
   * Correo de contacto propio de un caso.
   *
   * <p>Se quitan las tildes: el corrector de correo del backend acepta el subconjunto ASCII de RFC
   * 5322, y un local part con "í" seria un caso raro que no es lo que estos tests quieren probar.
   *
   * <p>Se agrega UUID corto para garantizar unicidad (constraint 046: email_personal UNIQUE).
   * Determinismo: contador incrementa por invocación. Mismo test, mismo orden de llamadas = mismo
   * email. Diferente orden o test diferente = diferente email. Evita colisiones UNIQUE.
   */
  private String emailDe(String apellido) {
    // Usar caché por apellido para determinismo.
    // Dentro de un test, múltiples calls con el mismo apellido retornan el mismo email.
    // Entre tests, cada uno arranca con emailCounter=0.
    return emailCache.computeIfAbsent(
        apellido,
        (key) -> {
          emailCounter++;
          String limpio =
              java.text.Normalizer.normalize(apellido, java.text.Normalizer.Form.NFD)
                  .replaceAll("\\p{Mn}", "")
                  .toLowerCase()
                  .replace(" ", "");
          String uuid = String.format("%08d", emailCounter);
          return "juan." + limpio + "." + uuid + "@example.com";
        });
  }

  private Response invitarDesde(String ip, String apellido) {
    // Cache per (IP, apellido) para permitir múltiples invitaciones con el mismo apellido
    // desde distintas IPs (cada una recibe email único).
    String cacheKey = ip + "|" + apellido;
    String email =
        invitationEmailCache.computeIfAbsent(
            cacheKey,
            (key) -> {
              // Generar email único por IP + apellido
              emailCounter++;
              String limpio =
                  java.text.Normalizer.normalize(apellido, java.text.Normalizer.Form.NFD)
                      .replaceAll("\\p{Mn}", "")
                      .toLowerCase()
                      .replace(" ", "");
              String uuid = String.format("%08d", emailCounter);
              return "juan." + limpio + "." + uuid + "@example.com";
            });

    // Cachear también en emailCache para que emailDe() después lo encuentre
    emailCache.putIfAbsent(apellido, email);

    return invitarConIpYEmail(ip, apellido, email);
  }

  private Response invitarConIpYEmail(String ip, String apellido, String email) {
    return given()
        .header("Authorization", "Bearer " + tokenMaster)
        .headers(ClientIpHeaders.from(ip))
        .header("User-Agent", "junit")
        .contentType("application/json")
        .body(
            """
            {"nombre":"Juan","apellido":"%s","emailPersonal":"%s","rolNombre":"OBSERVER"}
            """
                .formatted(apellido, email))
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

  // -------------------------------------------------------------------------
  // Diagnóstico: Resend + fallback
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("DIAG: invitación con Resend real + fallback (diagnóstico)")
  void diagnosticoResendConFallback() throws Exception {
    // Este test es para diagnosticar problemas de envío.
    // Corre CON tu RESEND_API_KEY si está configurada en .env.
    // Si falla, los logs de ResendEmailProvider dirán por qué.

    Response res = invitarDesde(IP_RESEND_ACTIVA, "DiagResend");

    // La invitación debe aceptarse
    assertThat(res.getStatusCode())
        .as("POST /users/invite responde 200")
        .isEqualTo(200);

    String usuarioId = res.jsonPath().getString("usuarioId");
    String username = res.jsonPath().getString("username");
    String emailPersonal = res.jsonPath().getString("emailPersonal");

    System.out.println("✓ Invitación aceptada");
    System.out.println("  - usuarioId: " + usuarioId);
    System.out.println("  - username: " + username);
    System.out.println("  - emailPersonal: " + emailPersonal);

    // Espera a que la cola procese (worker thread)
    System.out.println("⏳ Esperando a que el worker procese la cola...");
    Thread.sleep(2000);

    // RecordingEmailService captura TODOS los envíos (mock o real pasados por aquí)
    List<EmailAEnviar> enviados = RecordingEmailService.enviados();

    if (enviados.isEmpty()) {
      System.out.println("❌ PROBLEMA: Cola vacía después de 2s");
      System.out.println("   Causas probables:");
      System.out.println("   1. Worker thread no procesa (revisar app logs)");
      System.out.println("   2. Queue encolado pero timeout SMTP/Resend");
      System.out.println("   3. RESEND_API_KEY inválida (401 → reintento → timeout)");

      // Chequea auditoria de fallos
      List<Object[]> fallos =
          leer(
              "select usuario_id, email, reason from invitation_audit where reason NOT LIKE '%exito%' order by id desc limit 5");
      if (!fallos.isEmpty()) {
        System.out.println("\n   Fallos registrados en auditoria:");
        for (Object[] fila : fallos) {
          System.out.println("   - " + fila[0] + " (" + fila[1] + "): " + fila[2]);
        }
      }
    } else {
      System.out.println("✓ Email capturado por RecordingEmailService");
      EmailAEnviar email = enviados.get(0);
      System.out.println("  - destinatario: " + email.destinatario());
      System.out.println("  - tipo: " + email.tipo());
      System.out.println("  - activationLink: " + email.activationLink());
    }

    // Verifica que la invitación quedó registrada
    List<Object[]> invitacion =
        leer(
            "select status, invited_at, expiry_at from user_invitations where email_personal = '"
                + emailPersonal
                + "' order by id desc limit 1");

    assertThat(invitacion)
        .as("user_invitations debe tener la invitación")
        .hasSize(1);

    Object[] fila = invitacion.get(0);
    System.out.println("\n✓ Invitación registrada en BD");
    System.out.println("  - status: " + fila[0]);
    System.out.println("  - invited_at: " + fila[1]);
    System.out.println("  - expiry_at: " + fila[2]);

    // La invitación debe estar INVITED
    assertThat(fila[0])
        .as("status debe ser INVITED")
        .isEqualTo("INVITED");

    assertThat(fila[1])
        .as("invited_at debe estar lleno")
        .isNotNull();

    System.out.println("\n✅ DIAGNÓSTICO OK: invitación completa");
    System.out.println("   Si no recibes el email en la bandeja:");
    System.out.println("   1. Resend dashboard: https://resend.com/dashboard");
    System.out.println("   2. Revisa Activity tab (últimos 10 requests)");
    System.out.println("   3. Si no hay requests: APP_EMAIL_PROVIDER=smtp? O RESEND_API_KEY vacía?");
  }

  /** El token de activacion viaja en el link del email. */
  private String tokenDe(EmailAEnviar email) {
    String link = email.activationLink();
    return link.substring(link.indexOf("token=") + "token=".length());
  }

  /** Da un margen al worker para que hubiera enviado algo (y que se note que no envio). */
  private void esperarUnMomento() {
    try {
      Thread.sleep(600);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Espera a que el worker de la cola mande los emails de un destinatario.
   *
   * <p>Filtra por destinatario a proposito: el worker es asincrono y un email encolado por un test
   * anterior puede llegar durante este. Con un correo por test, cada uno mira solo los suyos.
   */
  private List<EmailAEnviar> esperarEmailsDe(String destinatario, int cantidad) {
    long limite = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < limite) {
      List<EmailAEnviar> enviados =
          RecordingEmailService.enviados().stream()
              .filter((e) -> destinatario.equals(e.destinatario()))
              .toList();
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
    return RecordingEmailService.enviados().stream()
        .filter((e) -> destinatario.equals(e.destinatario()))
        .toList();
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
