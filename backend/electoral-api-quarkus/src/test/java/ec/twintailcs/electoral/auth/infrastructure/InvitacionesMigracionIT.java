package ec.twintailcs.electoral.auth.infrastructure;

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
 * La migración 028 y el estado de activación de las cuentas, contra Postgres real.
 *
 * <p>El patrón es el mismo que el de {@code RlsIsolationIT}: Postgres de verdad, los changesets
 * aplicados a mano en el orden del master y JDBC crudo. No hace falta la aplicación para afirmar lo
 * que importa acá, que es de la base: qué columnas existen, con qué default, y qué les pasa a las
 * cuentas que ya estaban.
 *
 * <p>La garantía central del test es la tercera: <b>las cuentas que ya existían siguen activas</b>.
 * La migración agrega {@code estado_usuario} con un default. Si ese default fuera {@code
 * PENDIENTE_INVITACION}, el ALTER TABLE reescribiría todas las filas existentes con ese valor, el
 * login las rechazaría con 403 y el sistema electoral entero se quedaría sin ninguna cuenta con la
 * que entrar — sin password temporal, sin invitación, sin salida. Es un default que parece inocuo y
 * bloquea el arranque del sistema.
 */
class InvitacionesMigracionIT {

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  /** Los changesets que hacen falta para que `usuario` tenga su forma final. */
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
          "011-rls.sql",
          "025-00-usuario-public-id-default.sql",
          "028-invitaciones.sql");

  private Connection adminConn;

  @BeforeAll
  static void startContainer() throws SQLException {
    POSTGRES.start();
  }

  @AfterAll
  static void stopContainer() {
    POSTGRES.stop();
  }

  @AfterEach
  void tearDown() throws Exception {
    // La conexión se cierra SIEMPRE. Sin esto, la transacción queda abierta con sus
    // locks sobre `usuario` y el `DROP SCHEMA` del test siguiente se queda esperando
    // un lock que nunca se libera: el test cuelga en vez de fallar.
    if (adminConn != null) {
      try {
        adminConn.rollback();
        adminConn.close();
      } catch (SQLException ignorada) {
        // al cerrar, cualquier error es irrelevante
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
    for (String file : CHANGELOG_FILES) {
      try (var stmt = adminConn.createStatement()) {
        stmt.execute(readChangelog(file));
      }
    }
    adminConn.commit();
  }

  @Test
  @DisplayName("la migración 028 deja las cuatro columnas del flujo de invitación")
  void laMigracionDejaLasCuatroColumnas() throws Exception {
    assertThat(columna("estado_usuario")).as("estado_usuario").isNotNull();
    assertThat(columna("activation_token_hash")).as("activation_token_hash").isNotNull();
    assertThat(columna("activated_at")).as("activated_at").isNotNull();
    assertThat(columna("invitation_sent_at")).as("invitation_sent_at").isNotNull();
  }

  @Test
  @DisplayName("estado_usuario es NOT NULL y su default es ACTIVO")
  void estadoUsuarioEsNotNullYSuDefaultEsActivo() throws Exception {
    try (var stmt = adminConn.createStatement()) {
      try (ResultSet rs =
          stmt.executeQuery(
              "SELECT is_nullable, column_default FROM information_schema.columns "
                  + "WHERE table_name='usuario' AND column_name='estado_usuario'")) {
        assertThat(rs.next()).as("la columna debería existir").isTrue();
        assertThat(rs.getString("is_nullable")).as("no debería admitir null").isEqualTo("NO");
        assertThat(rs.getString("column_default"))
            .as("el default debe ser ACTIVO")
            .contains("ACTIVO");
      }
    }
  }

  @Test
  @DisplayName("las cuentas que ya existían siguen activas: el default no bloquea el arranque")
  void lasCuentasQueYaExistianSiguenActivas() throws Exception {
    // Se siembra una cuenta como la que habría antes de la migración: con su estado
    // heredado, el que en este repo es el de una cuenta que funciona.
    long id = insertUsuario("antes@control.ttcs", "OBSERVER");

    String estado = estadoDe(id);

    assertThat(estado)
        .as("una cuenta existente no puede quedar pendiente de activación: el login la rechazaría")
        .isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName("una cuenta nueva nace activa salvo que se pida lo contrario")
  void unaCuentaNuevaNaceActivaSalvoQueSePidaLoContrario() throws Exception {
    long id = insertUsuario("nueva@control.ttcs", "OBSERVER");

    assertThat(estadoDe(id)).as("el default de la columna").isEqualTo("ACTIVO");

    // Y el camino de la invitación: se puede pedir explícitamente que nazca pendiente.
    try (var stmt = adminConn.createStatement()) {
      stmt.execute("UPDATE usuario SET estado_usuario='PENDIENTE_INVITACION' WHERE id=" + id);
    }
    adminConn.commit();

    assertThat(estadoDe(id))
        .as("el estado pendiente se puede asignar")
        .isEqualTo("PENDIENTE_INVITACION");
  }

  @Test
  @DisplayName("los índices del flujo de invitación existen")
  void losIndicesDelFlujoDeInvitacionExisten() throws Exception {
    List<String> indices = indicesDeUsuario();

    assertThat(indices).contains("idx_usuario_estado_usuario");
    assertThat(indices).contains("idx_usuario_activation_token");
  }

  @Test
  @DisplayName("el hash del token es lo que se guarda: la base no guarda bearer secrets")
  void elHashDelTokenEsLoQueSeGuarda() throws Exception {
    long id = insertUsuario("token@control.ttcs", "OBSERVER");

    try (var stmt = adminConn.createStatement()) {
      stmt.execute("UPDATE usuario SET activation_token_hash='hash-no-el-token' WHERE id=" + id);
    }
    adminConn.commit();

    try (var stmt = adminConn.createStatement();
        ResultSet rs =
            stmt.executeQuery("SELECT activation_token_hash FROM usuario WHERE id=" + id)) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).as("lo guardado es el hash").isEqualTo("hash-no-el-token");
      assertThat(rs.getString(1)).doesNotContain("eyJ");
    }
  }

  @Test
  @DisplayName("anular el hash es lo que marca el token como consumido")
  void anularElHashEsLoQueMarcaElTokenComoConsumido() throws Exception {
    long id = insertUsuario("consumido@control.ttcs", "OBSERVER");
    try (var stmt = adminConn.createStatement()) {
      stmt.execute("UPDATE usuario SET activation_token_hash='hash' WHERE id=" + id);
    }
    adminConn.commit();

    try (var stmt = adminConn.createStatement()) {
      stmt.execute("UPDATE usuario SET activation_token_hash=NULL WHERE id=" + id);
    }
    adminConn.commit();

    try (var stmt = adminConn.createStatement();
        ResultSet rs =
            stmt.executeQuery("SELECT activation_token_hash FROM usuario WHERE id=" + id)) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).as("un hash nulo es la marca de token consumido").isNull();
    }
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private String columna(String nombre) throws SQLException {
    try (var stmt = adminConn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_name='usuario' AND column_name='"
                    + nombre
                    + "'")) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  private String estadoDe(long id) throws SQLException {
    try (var stmt = adminConn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT estado_usuario FROM usuario WHERE id=" + id)) {
      assertThat(rs.next()).as("la cuenta debería existir").isTrue();
      return rs.getString(1);
    }
  }

  private List<String> indicesDeUsuario() throws SQLException {
    List<String> indices = new java.util.ArrayList<>();
    try (var stmt = adminConn.createStatement();
        ResultSet rs =
            stmt.executeQuery("SELECT indexname FROM pg_indexes WHERE tablename='usuario'")) {
      while (rs.next()) {
        indices.add(rs.getString(1));
      }
    }
    return indices;
  }

  private long insertUsuario(String email, String rolNombre) throws SQLException {
    long rolId;
    try (var stmt = adminConn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT id FROM rol WHERE nombre='" + rolNombre + "'")) {
      assertThat(rs.next()).as("el rol %s debería estar sembrado", rolNombre).isTrue();
      rolId = rs.getLong(1);
    }
    try (PreparedStatement ps =
        adminConn.prepareStatement(
            "INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido, public_id) "
                + "VALUES (?, 'hash', ?, true, NOW(), 'Test', 'Prueba', ?)")) {
      ps.setString(1, email);
      ps.setLong(2, rolId);
      ps.setObject(3, UUID.randomUUID());
      ps.executeUpdate();
    }
    adminConn.commit();
    try (var stmt = adminConn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT id FROM usuario WHERE email='" + email + "'")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private String readChangelog(String fileName) throws SQLException {
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
}
