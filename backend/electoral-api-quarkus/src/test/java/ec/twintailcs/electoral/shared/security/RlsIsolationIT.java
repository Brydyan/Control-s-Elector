package ec.twintailcs.electoral.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de integración RLS — aísla las políticas SQL del contexto de la aplicación.
 *
 * <p>Port exacto del {@code RlsIsolationIT} del backend Spring. La suite de regresión por contrato
 * no puede afirmar esto: habla solo HTTP y nunca ve la base. Las políticas de row-level security
 * viven en Postgres, y la sesión que las aplica depende de un {@code SET LOCAL} por request — nada
 * de eso es observable desde afuera del proceso.
 *
 * <p>Patrón:
 *
 * <ol>
 *   <li>Testcontainers Postgres 16 (no requiere el contexto de la aplicación).
 *   <li>Aplica manualmente cada uno de los 11 changesets de {@code database/changelog/*.sql} en
 *       orden (mismo orden que el master de Liquibase). El changelog tiene un solo dueño — el
 *       módulo Spring — y el pom de este módulo lo copia al classpath, así que el schema que se
 *       prueba es exactamente el que corre en produccion.
 *   <li>Crea geografía mínima + 2 OBSERVERs + 1 COORDINATOR + 1 MASTER.
 *   <li>Inserta actas en nombre de cada OBSERVER.
 *   <li>Para cada usuario, abre una conexión, hace SET LOCAL con su identidad y cuenta las actas
 *       visibles.
 *   <li>Aserciones:
 *       <ul>
 *         <li>obsA ve solo su propia acta.
 *         <li>obsB ve solo su propia acta.
 *         <li>COORDINATOR y MASTER ven todas.
 *       </ul>
 *   <li>También verifica el índice único parcial: dos actas ENVIADA en la misma (jrv, cargo)
 *       chocan.
 * </ol>
 *
 * <p>Justificación del enfoque "raw JDBC" en vez de levantar la aplicación: queremos probar SOLO
 * las policies SQL. El arranque de la app mete ruido (Hibernate autocommit, joins del EM,
 * conexiones transaccionales) que dificulta aislar el comportamiento de las policies.
 */
class RlsIsolationIT {

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  private static final List<String> CHANGELOG_FILES =
      List.of(
          "001-initial-schema.sql",
          "002-rbac-seed.sql",
          "003-menus.sql",
          "004-public-ids.sql",
          "005-fks.sql",
          "006-geografia.sql",
          "007-usuario-nombre-apellido.sql",
          "008-configuracion.sql",
          "009-acta-observer-permisos.sql",
          "010-tablas-jpa.sql",
          "011-rls.sql");

  private Connection adminConn;
  private long obsAId;
  private long obsBId;
  private long coordId;
  private long masterId;
  private long jrvId;
  private long cargoId;
  private long cargoBId;

  // Nombre del rol no-superuser usado para validar la aplicacion real
  // de las policies. PostgreSQL superusers bypassan RLS por atributo,
  // asi que el postgres por defecto del container no sirve para probar.
  private static final String RLS_USER_NAME = "rls_app_user";
  private static final String RLS_PASSWORD = "rls_app_pwd";

  @BeforeAll
  static void startContainer() throws SQLException {
    POSTGRES.start();
    // Crear el rol no-superuser una sola vez, antes de cualquier test.
    // Lo hacemos aca (no en @BeforeEach) porque DROP ROLE falla si el rol
    // tiene privilegios sobre objetos; recrearlo en cada test seria fragil.
    try (Connection bootstrap =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      bootstrap
          .createStatement()
          .execute(
              "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='"
                  + RLS_USER_NAME
                  + "') THEN "
                  + "CREATE ROLE "
                  + RLS_USER_NAME
                  + " LOGIN PASSWORD '"
                  + RLS_PASSWORD
                  + "' NOSUPERUSER NOBYPASSRLS; "
                  + "END IF; END $$;");
      bootstrap
          .createStatement()
          .execute(
              "GRANT CONNECT ON DATABASE " + POSTGRES.getDatabaseName() + " TO " + RLS_USER_NAME);
    }
  }

  @AfterAll
  static void stopContainer() {
    POSTGRES.stop();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (adminConn != null) {
      try {
        adminConn.close();
      } catch (SQLException ignored) {
      }
      adminConn = null;
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    adminConn =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    adminConn.setAutoCommit(false);
    try (var stmt = adminConn.createStatement()) {
      stmt.execute("DROP SCHEMA public CASCADE");
      stmt.execute("CREATE SCHEMA public");
    }
    adminConn.commit();

    applyChangelogs();
    // Re-grant privilegios al rol no-superuser. DROP SCHEMA CASCADE borra
    // los objetos pero el rol sobrevive; los GRANT sobre los objetos nuevos
    // hay que reaplicarlos en cada test.
    try (var stmt = adminConn.createStatement()) {
      stmt.execute("GRANT USAGE ON SCHEMA public TO " + RLS_USER_NAME);
      stmt.execute(
          "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "
              + RLS_USER_NAME);
      stmt.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO " + RLS_USER_NAME);
    }
    adminConn.commit();

    seedData();
  }

  private void applyChangelogs() throws SQLException {
    // Antes de aplicar 010/011 (que dependen de geografía), sembramos
    // geografía manualmente. Esto replica lo que hace 006 pero como 006
    // solo agrega columnas si existen las tablas, sembramos via INSERT
    // después de correr los changesets base.
    for (String file : CHANGELOG_FILES) {
      String sql = readChangelog(file);
      try (var stmt = adminConn.createStatement()) {
        stmt.execute(sql);
      } catch (SQLException e) {
        throw new SQLException("Failed applying " + file + ": " + e.getMessage(), e);
      }
    }
    adminConn.commit();
  }

  private String readChangelog(String fileName) throws SQLException {
    // Leemos el archivo desde el classpath: el changelog tiene un solo dueño (el modulo
    // Spring) y el pom de este modulo lo copia a target/classes/database/changelog/.
    // OJO: sin barra inicial — ClassLoader.getResourceAsStream no la descarta (a diferencia
    // de Class.getResource), y con barra devuelve null.
    try (var is =
        getClass().getClassLoader().getResourceAsStream("database/changelog/" + fileName)) {
      if (is == null) {
        throw new SQLException("Changelog no encontrado: " + fileName);
      }
      return new String(is.readAllBytes());
    } catch (java.io.IOException e) {
      throw new SQLException("No pude leer " + fileName, e);
    }
  }

  private void seedData() throws SQLException {
    try (var stmt = adminConn.createStatement()) {
      // Geografía.
      stmt.execute("INSERT INTO provincia (nombre, codigo) VALUES ('Santa Elena', '24')");
      long provinciaId;
      try (ResultSet rs = stmt.executeQuery("SELECT id FROM provincia WHERE codigo='24'")) {
        rs.next();
        provinciaId = rs.getLong(1);
      }
      stmt.execute(
          "INSERT INTO canton (nombre, codigo, provincia_id) VALUES ('La Libertad', '2402', "
              + provinciaId
              + ")");
      long cantonId;
      try (ResultSet rs = stmt.executeQuery("SELECT id FROM canton WHERE codigo='2402'")) {
        rs.next();
        cantonId = rs.getLong(1);
      }
      stmt.execute(
          "INSERT INTO parroquia (nombre, es_urbana, canton_id) VALUES ('Central', true, "
              + cantonId
              + ")");
      long parroquiaId;
      try (ResultSet rs = stmt.executeQuery("SELECT id FROM parroquia WHERE nombre='Central'")) {
        rs.next();
        parroquiaId = rs.getLong(1);
      }
      stmt.execute(
          "INSERT INTO jrv (numero, recinto, total_electores, parroquia_id) VALUES ('JRV-TEST-001', 'Escuela Test', 300, "
              + parroquiaId
              + ")");
      try (ResultSet rs = stmt.executeQuery("SELECT id FROM jrv WHERE numero='JRV-TEST-001'")) {
        rs.next();
        jrvId = rs.getLong(1);
      }
      stmt.execute(
          "INSERT INTO cargo (nombre, nivel, puestos) VALUES ('Prefecto/a', 'PROVINCIA', 1)");
      try (ResultSet rs = stmt.executeQuery("SELECT id FROM cargo WHERE nombre='Prefecto/a'")) {
        rs.next();
        cargoId = rs.getLong(1);
      }

      stmt.execute("INSERT INTO cargo (nombre, nivel, puestos) VALUES ('Alcalde/sa', 'CANTON', 1)");
      try (ResultSet rs = stmt.executeQuery("SELECT id FROM cargo WHERE nombre='Alcalde/sa'")) {
        rs.next();
        cargoBId = rs.getLong(1);
      }

      // Usuarios con rol real (FK a tabla rol poblada por 005-seed).
      obsAId = insertUsuario(stmt, "OBSERVER", "rlstest-obsA@test");
      obsBId = insertUsuario(stmt, "OBSERVER", "rlstest-obsB@test");
      coordId = insertUsuario(stmt, "COORDINATOR", "rlstest-coord@test");
      masterId = insertUsuario(stmt, "MASTER", "rlstest-master@test");
    }
    adminConn.commit();
  }

  private long insertUsuario(java.sql.Statement stmt, String rolNombre, String email)
      throws SQLException {
    long rolId;
    try (ResultSet rs = stmt.executeQuery("SELECT id FROM rol WHERE nombre='" + rolNombre + "'")) {
      rs.next();
      rolId = rs.getLong(1);
    }
    stmt.execute(
        "INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido, public_id) "
            + "VALUES ('"
            + email
            + "', 'hash', "
            + rolId
            + ", true, NOW(), 'Test', '"
            + rolNombre
            + "', '"
            + UUID.randomUUID()
            + "')");
    try (ResultSet rs = stmt.executeQuery("SELECT id FROM usuario WHERE email='" + email + "'")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  // ---------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("un OBSERVER solo ve las actas que registro: RLS lo aisla del resto")
  void elObserverSoloVeLasActasQueRegistro() throws Exception {
    // Importa porque es la garantia de aislamiento de la mesa: un observer no puede
    // leer (ni de ahi colarse a) el acta de otro observer de la misma JRV. La
    // sesion aplica SET LOCAL con la identidad del usuario, y la politica de
    // SELECT filtra por registrada_por.
    insertActaEnviada(obsAId, jrvId, cargoId);
    insertActaEnviada(obsBId, jrvId, cargoBId);

    int visibleObsA = countActasVisibleAs(obsAId, "OBSERVER");
    int visibleObsB = countActasVisibleAs(obsBId, "OBSERVER");
    int visibleCoord = countActasVisibleAs(coordId, "COORDINATOR");
    int visibleMaster = countActasVisibleAs(masterId, "MASTER");

    assertThat(visibleObsA).as("obsA ve solo su acta").isEqualTo(1);
    assertThat(visibleObsB).as("obsB ve solo su acta").isEqualTo(1);
    assertThat(visibleCoord).as("COORDINATOR ve ambas").isEqualTo(2);
    assertThat(visibleMaster).as("MASTER ve ambas").isEqualTo(2);
  }

  @Test
  @DisplayName("el indice unico parcial impide dos actas activas en el mismo par JRV+cargo")
  void elIndiceUnicoParcialImpideDosActasActivasEnElMismoPar() throws Exception {
    // Importa porque es el invariante de unicidad de la mesa, y la ultima linea de
    // defensa cuando dos observers registran el mismo par a la vez: la base tiene que
    // rechazar el segundo INSERT con 23505 (unique_violation), no duplicar la fila.
    insertActaEnviada(obsAId, jrvId, cargoId);

    // El segundo insert debe fallar por uk_acta_activa (jrv_id, cargo_id) WHERE estado IN (...)
    SQLException thrown = null;
    try {
      insertActaEnviada(obsBId, jrvId, cargoId);
    } catch (SQLException e) {
      thrown = e;
    }
    assertThat((Object) thrown)
        .as("El índice único parcial debe rechazar la segunda acta activa")
        .isNotNull();
    assertThat(thrown.getSQLState())
        .as("SQLState esperado 23505 (unique_violation)")
        .isEqualTo("23505");
  }

  // ---------------------------------------------------------------------
  // T-AUTH-INV-02: RLS de la auditoria de invitaciones
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("cada usuario solo ve su propia invitacion y el MASTER las ve todas")
  void cadaUsuarioSoloVeSuPropiaInvitacion() throws Exception {
    insertInvitacion(obsAId, "obs-a@test.local");
    insertInvitacion(obsBId, "obs-b@test.local");

    assertThat(countVisible("user_invitations", obsAId, "OBSERVER"))
        .as("obsA solo ve su invitacion")
        .isEqualTo(1);
    assertThat(countVisible("user_invitations", obsBId, "OBSERVER"))
        .as("obsB solo ve su invitacion")
        .isEqualTo(1);
    assertThat(countVisible("user_invitations", masterId, "MASTER"))
        .as("el MASTER ve todas")
        .isEqualTo(2);
  }

  @Test
  @DisplayName("sin contexto de RLS la invitacion no se ve: el correo de contacto no se filtra")
  void sinContextoDeRlsLaInvitacionNoSeVe() throws Exception {
    insertInvitacion(obsAId, "obs-a@test.local");

    assertThat(countVisibleSinContexto("user_invitations"))
        .as("sin identidad no se lee ninguna invitacion")
        .isZero();
  }

  @Test
  @DisplayName("la escritura exige que invited_by sea el usuario del contexto")
  void laEscrituraExigeQueInvitedBySeaElContexto() throws Exception {
    // Con el contexto de obsA no se puede registrar una invitacion en nombre de obsB.
    SQLException thrown = null;
    try (Connection conn =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), RLS_USER_NAME, RLS_PASSWORD)) {
      conn.setAutoCommit(false);
      try (PreparedStatement ps =
          conn.prepareStatement("SELECT set_config('app.current_user_id', ?, true)")) {
        ps.setString(1, Long.toString(obsAId));
        ps.execute();
      }
      try (PreparedStatement ps =
          conn.prepareStatement(
              "INSERT INTO user_invitations (usuario_id, email_personal, invited_by, expiry_at) VALUES (?, 'x@test.local', ?, NOW() + INTERVAL '30 days')")) {
        ps.setLong(1, obsBId);
        ps.setLong(2, obsAId);
        ps.executeUpdate();
      } catch (SQLException e) {
        thrown = e;
      }
      conn.rollback();
    }

    assertThat((Object) thrown)
        .as("la politica de insercion rechaza atribuir la invitacion a otro")
        .isNotNull();
    assertThat(thrown.getSQLState()).isEqualTo("42501");
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  /**
   * Cuenta las filas visibles de una tabla con el contexto de RLS de un usuario.
   *
   * <p>La tabla va por parametro porque el mismo helper sirve para {@code acta} y para las tablas
   * del bloque legal: lo que se prueba en todos los casos es que las politicas aislan.
   */
  private int countVisible(String tabla, long userId, String rolName) throws SQLException {
    try (Connection conn =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), RLS_USER_NAME, RLS_PASSWORD)) {
      conn.setAutoCommit(false);
      try (PreparedStatement ps =
          conn.prepareStatement("SELECT set_config('app.current_user_id', ?, true)")) {
        ps.setString(1, Long.toString(userId));
        ps.execute();
      }
      try (PreparedStatement ps =
          conn.prepareStatement("SELECT set_config('app.current_user_rol', ?, true)")) {
        ps.setString(1, rolName);
        ps.execute();
      }
      int count;
      try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tabla);
          ResultSet rs = ps.executeQuery()) {
        rs.next();
        count = rs.getInt(1);
      }
      conn.commit();
      return count;
    }
  }

  /** Cuenta las filas visibles SIN contexto de RLS (la sesion no identifica a nadie). */
  private int countVisibleSinContexto(String tabla) throws SQLException {
    try (Connection conn =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), RLS_USER_NAME, RLS_PASSWORD)) {
      conn.setAutoCommit(false);
      int count;
      try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tabla);
          ResultSet rs = ps.executeQuery()) {
        rs.next();
        count = rs.getInt(1);
      }
      conn.commit();
      return count;
    }
  }

  /** Inserta una fila de auditoria de invitacion como el administrador. */
  private void insertInvitacion(long usuarioId, String emailPersonal) throws SQLException {
    try (var stmt = adminConn.createStatement()) {
      stmt.execute(
          "INSERT INTO user_invitations (usuario_id, email_personal, invited_by, expiry_at) VALUES ("
              + usuarioId
              + ", '"
              + emailPersonal
              + "', "
              + masterId
              + ", NOW() + INTERVAL '30 days')");
    }
    adminConn.commit();
  }

  private void insertActaEnviada(long usuarioId, long jrvId, long cargoId) throws SQLException {
    // El insert corre como admin (sin RLS efectiva o con bypass).
    try (PreparedStatement ps =
        adminConn.prepareStatement(
            "INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes, votos_blancos, votos_nulos, fecha_registro, public_id, version) "
                + "VALUES (?, ?, ?, 'ENVIADA', 100, 5, 3, NOW(), ?, 0)")) {
      ps.setLong(1, jrvId);
      ps.setLong(2, cargoId);
      ps.setLong(3, usuarioId);
      ps.setObject(4, UUID.randomUUID());
      ps.executeUpdate();
    }
    adminConn.commit();
  }

  /**
   * Cuenta actas visibles para un usuario bajo RLS. Abre una conexión nueva (sin auto-commit),
   * aplica SET LOCAL para la identidad y rol del usuario, hace SELECT COUNT, y commitea.
   */
  private int countActasVisibleAs(long userId, String rolName) throws SQLException {
    try (Connection conn =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), RLS_USER_NAME, RLS_PASSWORD)) {
      conn.setAutoCommit(false);
      try (PreparedStatement ps =
          conn.prepareStatement("SELECT set_config('app.current_user_id', ?, true)")) {
        ps.setString(1, Long.toString(userId));
        ps.execute();
      }
      try (PreparedStatement ps =
          conn.prepareStatement("SELECT set_config('app.current_user_rol', ?, true)")) {
        ps.setString(1, rolName);
        ps.execute();
      }
      int count;
      try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM acta");
          ResultSet rs = ps.executeQuery()) {
        rs.next();
        count = rs.getInt(1);
      }
      conn.commit();
      return count;
    }
  }
}
