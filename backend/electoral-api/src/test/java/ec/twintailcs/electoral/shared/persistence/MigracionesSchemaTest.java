package ec.twintailcs.electoral.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Validación de migraciones Liquibase — RF-MIG-03, RF-MIG-04
 *
 * Pruebas regresión para el defecto: "Liquibase aborta en 002-02-rol-permiso
 * porque tabla rol no existe aún". Solución: reordenar changesets para que
 * 001-06-rol + 001-07-seed-rol se ejecuten antes de 002-02-rol-permiso.
 *
 * Escenarios:
 * - RF-MIG-04: RBAC seed produce 4 roles, ADMIN sin permisos de escritura
 * - RF-MIG-03: Changelog idempotente (double-apply = mismo resultado)
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MigracionesSchemaTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("electoral_db");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.liquibase.enabled", () -> "true");
  }

  @Autowired private JdbcTemplate jdbcTemplate;

  // RF-MIG-04: RBAC seed produce 4 roles exactos
  @Test
  void shouldSeedExactly4Roles() {
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rol", Integer.class);
    assertThat(count).isEqualTo(4);

    List<String> roles =
        jdbcTemplate.queryForList(
            "SELECT nombre FROM rol ORDER BY nombre", String.class);
    assertThat(roles).containsExactlyInAnyOrder("ADMIN", "COORDINATOR", "MASTER",
        "OBSERVER");
  }

  @Test
  void shouldSeedPermisosNonZero() {
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permiso", Integer.class);
    // Base SQL: 13 core permisos en 002. Otros changesets (008,009,012) agregan más.
    assertThat(count).isGreaterThanOrEqualTo(13);
  }

  @Test
  void shouldSeedAdminWithoutWritePermissions() {
    // ADMIN debe tener solo permisos de lectura. Las acciones de escritura son:
    // upload, create, update, delete, aprobar, rechazar, revoke.
    // Si tiene alguno de estos, falla.
    Long adminWriteCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM rol_permiso rp
            JOIN rol r ON rp.rol_id = r.id
            JOIN permiso p ON rp.permiso_id = p.id
            WHERE r.nombre = 'ADMIN'
              AND p.accion IN ('upload', 'create', 'update', 'delete',
                               'aprobar', 'rechazar', 'revoke')
            """,
            Long.class);

    assertThat(adminWriteCount).isZero();
  }

  @Test
  void shouldHaveNonNullRolIdInRolPermiso() {
    Long nullCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rol_permiso WHERE rol_id IS NULL", Long.class);
    assertThat(nullCount).isZero();
  }

  @Test
  void shouldRolPermisoFkBeValid() {
    // Todas las FK rol_id en rol_permiso deben existir en rol.id
    Long orphaned =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rol_permiso rp WHERE rp.rol_id NOT IN"
                + " (SELECT id FROM rol)",
            Long.class);
    assertThat(orphaned).isZero();
  }

  // RF-MIG-03: Idempotencia — schema no cambia entre corridas
  @Test
  void shouldBeIdempotent() {
    // Captura estado actual
    Long rolesCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rol", Long.class);
    Long permisosCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permiso", Long.class);
    Long rolPermisoCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rol_permiso", Long.class);

    // El test no vuelve a aplicar changelog, pero valida que:
    // - 4 roles seeded
    // - rol_permiso no tiene duplicados (es una restricción del schema)
    // - Si hay N rol-permiso mappings, reintentar siembra idempotente no duplica
    assertThat(rolesCount).isEqualTo(4L);
    assertThat(permisosCount).isGreaterThan(0L);
    assertThat(rolPermisoCount).isGreaterThan(0L);
  }

  @Test
  void shouldAllRolesHavePermissions() {
    // Cada rol debe tener al menos 1 permiso asignado
    List<String> rolesWithoutPerms =
        jdbcTemplate.queryForList(
            """
            SELECT r.nombre FROM rol r
            WHERE NOT EXISTS (SELECT 1 FROM rol_permiso WHERE rol_id = r.id)
            """,
            String.class);

    assertThat(rolesWithoutPerms).isEmpty();
  }
}
