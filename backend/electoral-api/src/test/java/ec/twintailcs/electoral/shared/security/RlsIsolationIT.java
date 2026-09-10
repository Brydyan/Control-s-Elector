package ec.twintailcs.electoral.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de integración RLS — aísla las políticas SQL del contexto Spring.
 *
 * <p>Patrón:
 * <ol>
 *   <li>Testcontainers Postgres 16 (no requiere Spring context).</li>
 *   <li>Aplica manualmente cada uno de los 11 changesets de
 *       {@code database/changelog/*.sql} en orden (mismo orden que el
 *       master de Liquibase).</li>
 *   <li>Crea geografía mínima + 2 OBSERVERs + 1 COORDINATOR + 1 MASTER.</li>
 *   <li>Inserta actas en nombre de cada OBSERVER.</li>
 *   <li>Para cada usuario, abre una conexión, hace SET LOCAL con su
 *       identidad y cuenta las actas visibles.</li>
 *   <li>Aserciones:
 *       <ul>
 *         <li>obsA ve solo su propia acta.</li>
 *         <li>obsB ve solo su propia acta.</li>
 *         <li>COORDINATOR y MASTER ven todas.</li>
 *       </ul>
 *   </li>
 *   <li>También verifica el índice único parcial: dos actas ENVIADA
 *       en la misma (jrv, cargo) chocan.</li>
 * </ol>
 *
 * <p>Justificación del enfoque "raw JDBC" en vez de {@code @SpringBootTest}:
 * queremos probar SOLO las policies SQL. El contexto Spring mete ruido
 * (Hibernate autocommit, EM joins, conexiones transaccionales) que
 * dificulta aislar el comportamiento de las policies.
 */
class RlsIsolationIT {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final List<String> CHANGELOG_FILES = List.of(
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
            "011-rls.sql"
    );

    private Connection adminConn;
    private long obsAId;
    private long obsBId;
    private long coordId;
    private long masterId;
    private long jrvId;
    private long cargoId;

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        adminConn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        adminConn.setAutoCommit(false);

        applyChangelogs();
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
        // Leemos el archivo desde el classpath; los tests corren desde
        // backend/electoral-api y los resources están en classpath:database/changelog/
        try (var is = getClass().getClassLoader().getResourceAsStream("database/changelog/" + fileName)) {
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
            stmt.execute("INSERT INTO canton (nombre, codigo, provincia_id) VALUES ('La Libertad', '2402', " + provinciaId + ")");
            long cantonId;
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM canton WHERE codigo='2402'")) {
                rs.next();
                cantonId = rs.getLong(1);
            }
            stmt.execute("INSERT INTO parroquia (nombre, es_urbana, canton_id) VALUES ('Central', true, " + cantonId + ")");
            long parroquiaId;
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM parroquia WHERE nombre='Central'")) {
                rs.next();
                parroquiaId = rs.getLong(1);
            }
            stmt.execute("INSERT INTO jrv (numero, recinto, total_electores, parroquia_id) VALUES ('JRV-TEST-001', 'Escuela Test', 300, " + parroquiaId + ")");
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM jrv WHERE numero='JRV-TEST-001'")) {
                rs.next();
                jrvId = rs.getLong(1);
            }
            stmt.execute("INSERT INTO cargo (nombre, nivel, puestos) VALUES ('Prefecto/a', 'PROVINCIA', 1)");
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM cargo WHERE nombre='Prefecto/a'")) {
                rs.next();
                cargoId = rs.getLong(1);
            }

            // Usuarios con rol real (FK a tabla rol poblada por 005-seed).
            obsAId = insertUsuario(stmt, "OBSERVER", "rlstest-obsA@test");
            obsBId = insertUsuario(stmt, "OBSERVER", "rlstest-obsB@test");
            coordId = insertUsuario(stmt, "COORDINATOR", "rlstest-coord@test");
            masterId = insertUsuario(stmt, "MASTER", "rlstest-master@test");
        }
        adminConn.commit();
    }

    private long insertUsuario(java.sql.Statement stmt, String rolNombre, String email) throws SQLException {
        long rolId;
        try (ResultSet rs = stmt.executeQuery("SELECT id FROM rol WHERE nombre='" + rolNombre + "'")) {
            rs.next();
            rolId = rs.getLong(1);
        }
        stmt.execute("INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido) "
                + "VALUES ('" + email + "', 'hash', " + rolId + ", true, NOW(), 'Test', '" + rolNombre + "')");
        try (ResultSet rs = stmt.executeQuery("SELECT id FROM usuario WHERE email='" + email + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    @Test
    void observerOnlySeesOwnActas() throws Exception {
        insertActaEnviada(obsAId, jrvId, cargoId);
        insertActaEnviada(obsBId, jrvId, cargoId);

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
    void uniquePartialIndexBlocksDuplicateActiveActa() throws Exception {
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
    // Helpers
    // ---------------------------------------------------------------------

    private void insertActaEnviada(long usuarioId, long jrvId, long cargoId) throws SQLException {
        // El insert corre como admin (sin RLS efectiva o con bypass).
        try (PreparedStatement ps = adminConn.prepareStatement(
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
     * Cuenta actas visibles para un usuario bajo RLS. Abre una conexión
     * nueva (sin auto-commit), aplica SET LOCAL para la identidad y rol
     * del usuario, hace SELECT COUNT, y commitea.
     */
    private int countActasVisibleAs(long userId, String rolName) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("SET LOCAL app.current_user_id = ?")) {
                ps.setLong(1, userId);
                ps.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement("SET LOCAL app.current_user_rol = ?")) {
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

    // ---------------------------------------------------------------------
    // Cleanup utility (no usado por JUnit pero mantiene importes vivos)
    // ---------------------------------------------------------------------
    @SuppressWarnings("unused")
    private static List<String> unusedToAvoidImportCleanup() {
        return new ArrayList<>();
    }
}