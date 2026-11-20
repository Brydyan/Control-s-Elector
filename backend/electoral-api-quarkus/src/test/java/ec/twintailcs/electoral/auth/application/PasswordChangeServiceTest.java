package ec.twintailcs.electoral.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios para cambio de contraseña.
 *
 * <p>Validaciones: - Contraseña actual debe ser correcta - Nueva contraseña debe cumplir requisitos
 * - Nueva no puede ser igual a la anterior - Ambas nuevas deben coincidir
 */
@QuarkusTest
class PasswordChangeServiceTest {

  private static final String CURRENT_PASSWORD = "OldPass@123";
  private static final String NEW_PASSWORD = "NewPass@2026";

  @Inject PasswordChangeService service;
  @Inject PasswordHasher hasher;

  private Usuario usuario;

  @BeforeEach
  void setup() {
    usuario =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Rol rol = Rol.find("nombre", "MASTER").firstResult();
                  Usuario u = new Usuario();
                  u.setEmail("test-" + UUID.randomUUID() + "@test.local");
                  u.setNombre("Test-" + UUID.randomUUID());
                  u.setApellido("User");
                  u.setPasswordHash(hasher.hash(CURRENT_PASSWORD));
                  u.setRol(rol);
                  u.setHabilitado(true);
                  u.persist();
                  return u;
                });
  }

  @Test
  @DisplayName("cambio de contraseña exitoso actualiza el hash")
  void cambioDeContrasenaExitoso() {
    String oldHash = usuario.getPasswordHash();

    QuarkusTransaction.requiringNew()
        .call(
            () -> {
              service.changePassword(
                  usuario.getPublicId(), CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
              return null;
            });

    // Recargar usuario
    usuario = Usuario.find("publicId", usuario.getPublicId()).firstResult();
    String newHash = usuario.getPasswordHash();

    assertThat(newHash).as("el hash debería cambiar").isNotEqualTo(oldHash);
    assertThat(hasher.matches(NEW_PASSWORD, newHash))
        .as("la nueva contraseña debería matchear")
        .isTrue();
  }

  @Test
  @DisplayName("contraseña actual incorrecta lanza excepción")
  void contrasenaActualIncorrecta() {
    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          service.changePassword(
                              usuario.getPublicId(), "WrongPassword@1", NEW_PASSWORD, NEW_PASSWORD);
                          return null;
                        }))
        .isInstanceOf(PasswordChangeException.class)
        .hasMessageContaining("incorrecta");
  }

  @Test
  @DisplayName("nuevas contraseñas no coinciden lanza excepción")
  void contrasenasMismatchException() {
    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          service.changePassword(
                              usuario.getPublicId(),
                              CURRENT_PASSWORD,
                              NEW_PASSWORD,
                              "DifferentPass@2026");
                          return null;
                        }))
        .isInstanceOf(PasswordChangeException.class)
        .hasMessageContaining("coinciden");
  }

  @Test
  @DisplayName("nueva contraseña igual a la anterior lanza excepción")
  void nuevaIgualAAnterior() {
    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          service.changePassword(
                              usuario.getPublicId(),
                              CURRENT_PASSWORD,
                              CURRENT_PASSWORD,
                              CURRENT_PASSWORD);
                          return null;
                        }))
        .isInstanceOf(PasswordChangeException.class)
        .hasMessageContaining("igual a la anterior");
  }

  @Test
  @DisplayName("nueva contraseña sin requisitos válidos lanza excepción")
  void nuevaContrasenaSinRequisitos() {
    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          service.changePassword(
                              usuario.getPublicId(), CURRENT_PASSWORD, "weak", "weak");
                          return null;
                        }))
        .isInstanceOf(PasswordChangeException.class)
        .hasMessageContaining("requisitos");
  }
}
