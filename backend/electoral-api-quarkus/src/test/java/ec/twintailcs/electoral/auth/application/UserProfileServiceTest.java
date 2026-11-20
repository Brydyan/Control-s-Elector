package ec.twintailcs.electoral.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios para actualización de email personal.
 *
 * <p>Validaciones: - Contraseña debe ser correcta - Email debe tener formato válido - Email no
 * puede ser igual al email control - Email debe ser único en el sistema (si no es null)
 */
@QuarkusTest
class UserProfileServiceTest {

  private static final String PASSWORD = "TestPass@123";

  @Inject UserProfileService service;
  @Inject PasswordHasher hasher;
  @Inject EntityManager em;

  private Usuario usuario;

  @BeforeEach
  void setup() {
    // Limpiar usuarios de tests anteriores para evitar violaciones de constraint
    // en bases de datos compartidas (en CI o cuando tests corren en paralelo).
    // Orden: delete child tables before parents (cascade-safe, FK dependency order)
    QuarkusTransaction.requiringNew()
        .call(
            () -> {
              em.createNativeQuery("delete from user_invitations").executeUpdate();
              em.createNativeQuery("delete from notificacion").executeUpdate();
              em.createNativeQuery("delete from acta").executeUpdate();
              em.createNativeQuery("delete from observer_jrv_asignacion").executeUpdate();
              em.createNativeQuery("delete from jrv_cargo").executeUpdate();
              em.createNativeQuery("delete from audit_log").executeUpdate();
              em.createNativeQuery("delete from jrv").executeUpdate();
              em.createNativeQuery("delete from evidencia").executeUpdate();
              em.createNativeQuery("delete from recinto").executeUpdate();
              em.createNativeQuery("delete from sesion").executeUpdate();
              em.createNativeQuery("delete from usuario where id > 1").executeUpdate();
              em.createNativeQuery("alter sequence usuario_id_seq restart with 2").executeUpdate();
              return null;
            });

    usuario =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Rol rol = Rol.find("nombre", "MASTER").firstResult();
                  Usuario u = new Usuario();
                  u.setEmail("test-" + UUID.randomUUID() + "@test.local");
                  u.setNombre("Test-" + UUID.randomUUID());
                  u.setApellido("User");
                  u.setPasswordHash(hasher.hash(PASSWORD));
                  u.setRol(rol);
                  u.setHabilitado(true);
                  u.persist();
                  return u;
                });
  }

  @Test
  @DisplayName("actualizar email personal válido exitoso")
  void actualizarEmailPersonalExitoso() {
    String newEmail = "newemail@gmail.com";

    QuarkusTransaction.requiringNew()
        .call(
            () -> {
              service.updatePersonalEmail(usuario.getPublicId(), newEmail, PASSWORD);
              return null;
            });

    usuario = Usuario.find("publicId", usuario.getPublicId()).firstResult();
    assertThat(usuario.getEmailPersonal()).isEqualTo(newEmail.toLowerCase());
  }

  @Test
  @DisplayName("limpiar email personal (null) exitoso")
  void limpiarEmailPersonalExitoso() {
    QuarkusTransaction.requiringNew()
        .call(
            () -> {
              service.updatePersonalEmail(usuario.getPublicId(), null, PASSWORD);
              return null;
            });

    usuario = Usuario.find("publicId", usuario.getPublicId()).firstResult();
    assertThat(usuario.getEmailPersonal()).isNull();
  }

  @Test
  @DisplayName("contraseña incorrecta lanza excepción")
  void contrasenaIncorrecta() {
    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          service.updatePersonalEmail(
                              usuario.getPublicId(), "new@gmail.com", "WrongPass@123");
                          return null;
                        }))
        .isInstanceOf(UserProfileException.class)
        .hasMessageContaining("incorrecta");
  }

  @Test
  @DisplayName("email con formato inválido lanza excepción")
  void emailFormatoInvalido() {
    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          service.updatePersonalEmail(
                              usuario.getPublicId(), "notanemail", PASSWORD);
                          return null;
                        }))
        .isInstanceOf(UserProfileException.class)
        .hasMessageContaining("inválido");
  }

  @Test
  @DisplayName("email igual al email control lanza excepción")
  void emailIgualAlControl() {
    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          service.updatePersonalEmail(
                              usuario.getPublicId(), usuario.getEmail(), PASSWORD);
                          return null;
                        }))
        .isInstanceOf(UserProfileException.class)
        .hasMessageContaining("igual");
  }

  @Test
  @DisplayName("email duplicado lanza excepción")
  void emailDuplicado() {
    // Crear otro usuario con email personal
    Usuario otroUsuario =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Rol rol = Rol.find("nombre", "MASTER").firstResult();
                  Usuario u = new Usuario();
                  u.setEmail("test2-" + UUID.randomUUID() + "@test.local");
                  u.setNombre("Test2");
                  u.setApellido("User2");
                  u.setPasswordHash(hasher.hash(PASSWORD));
                  u.setRol(rol);
                  u.setEmailPersonal("duplicated@gmail.com");
                  u.setHabilitado(true);
                  u.persist();
                  return u;
                });

    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          service.updatePersonalEmail(
                              usuario.getPublicId(), "duplicated@gmail.com", PASSWORD);
                          return null;
                        }))
        .isInstanceOf(UserProfileException.class)
        .hasMessageContaining("ya está en uso");
  }
}
