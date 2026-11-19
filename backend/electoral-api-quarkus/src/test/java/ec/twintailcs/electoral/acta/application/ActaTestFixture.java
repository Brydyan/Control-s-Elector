package ec.twintailcs.electoral.acta.application;

import jakarta.persistence.EntityManager;

/**
 * Test fixture para pre-popular datos necesarios en tests de acta.
 *
 * <p>Crea usuarios, JRVs y cargos que faltan para satisfacer restricciones FK.
 */
public class ActaTestFixture {

  private final EntityManager entityManager;

  public ActaTestFixture(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /** Crea todos los datos necesarios para los tests. */
  public void setUpTestData() {
    crearUsuarios();
    crearJrvs();
    crearCargos();
    crearRoles();
  }

  /** Crea usuarios necesarios para NotificacionServiceTest (1-9) y UseCaseTests (100-105, 500). */
  private void crearUsuarios() {
    long[] usuarioIds = {
      1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 100L, 101L, 102L, 103L, 104L, 105L, 500L, 993L, 994L,
      995L, 996L, 997L, 998L, 999L
    };

    for (long id : usuarioIds) {
      // Verificar si existe
      Object exists =
          entityManager
              .createNativeQuery("SELECT COUNT(*) FROM usuario WHERE id = ?1")
              .setParameter(1, id)
              .getSingleResult();
      if (((Number) exists).longValue() == 0) {
        entityManager
            .createNativeQuery(
                "INSERT INTO usuario (id, email, password_hash, rol_id, habilitado, nombre, apellido, estado_usuario, creado_en, public_id) "
                    + "VALUES (?1, ?2, ?3, ?4, true, ?5, ?6, ?7, CURRENT_TIMESTAMP, gen_random_uuid())")
            .setParameter(1, id)
            .setParameter(2, "test-user-" + id + "@test.com")
            .setParameter(
                3, "$2a$10$dummyhashedpassword1234567890123456789012345678") // BCrypt format
            .setParameter(4, 1L) // Referencia a rol (asume rol_id=1 existe)
            .setParameter(5, "Test")
            .setParameter(6, "User" + id)
            .setParameter(7, "ACTIVO")
            .executeUpdate();
      }
    }
  }

  /** Crea JRVs necesarias (1-7). */
  private void crearJrvs() {
    for (long id = 1; id <= 7; id++) {
      Object exists =
          entityManager
              .createNativeQuery("SELECT COUNT(*) FROM jrv WHERE id = ?1")
              .setParameter(1, id)
              .getSingleResult();
      if (((Number) exists).longValue() == 0) {
        entityManager
            .createNativeQuery(
                "INSERT INTO jrv (id, numero, zona_id, provincia_id) VALUES (?1, ?2, ?3, ?4)")
            .setParameter(1, id)
            .setParameter(2, 100 + id) // numero = 101-107
            .setParameter(3, 1L) // zona_id
            .setParameter(4, 1L) // provincia_id
            .executeUpdate();
      }
    }
  }

  /** Crea Cargo necesario (1). */
  private void crearCargos() {
    Object exists =
        entityManager
            .createNativeQuery("SELECT COUNT(*) FROM cargo WHERE id = ?1")
            .setParameter(1, 1L)
            .getSingleResult();
    if (((Number) exists).longValue() == 0) {
      entityManager
          .createNativeQuery("INSERT INTO cargo (id, nombre, nivel) VALUES (?1, ?2, ?3)")
          .setParameter(1, 1L)
          .setParameter(2, "Presidente")
          .setParameter(3, "NACIONAL")
          .executeUpdate();
    }
  }

  /** Crea Rol (asume que se necesita para Usuario). */
  private void crearRoles() {
    Object exists =
        entityManager
            .createNativeQuery("SELECT COUNT(*) FROM rol WHERE id = ?1")
            .setParameter(1, 1L)
            .getSingleResult();
    if (((Number) exists).longValue() == 0) {
      entityManager
          .createNativeQuery("INSERT INTO rol (id, nombre) VALUES (?1, ?2)")
          .setParameter(1, 1L)
          .setParameter(2, "OBSERVER")
          .executeUpdate();
    }
  }
}
