package ec.twintailcs.electoral.auth.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsuarioTest {

  @Test
  @DisplayName("Debe fallar si el email es nulo o vacío")
  void debeFallarSiEmailVacio() {
    Usuario u = new Usuario();
    assertThrows(UsuarioInvalidoException.class, () -> u.setEmail(null));
    assertThrows(UsuarioInvalidoException.class, () -> u.setEmail("   "));
  }

  @Test
  @DisplayName("Debe sanitizar email a minúsculas y trim")
  void debeSanitizarEmail() {
    Usuario u = new Usuario();
    u.setEmail("  USER@Dominio.COM  ");
    assertEquals("user@dominio.com", u.getEmail());
  }

  @Test
  @DisplayName("Debe validar datos obligatorios de usuario")
  void debeValidarDatosObligatorios() {
    Usuario u = new Usuario();
    assertThrows(UsuarioInvalidoException.class, u::validarDatosObligatorios);

    u.setEmail("test@test.com");
    u.setNombre("Juan");
    u.setApellido("Perez");
    u.setPasswordHash("hashed_secret");
    assertThrows(UsuarioInvalidoException.class, u::validarDatosObligatorios);

    Rol rol = new Rol("OPERATOR");
    u.setRol(rol);
    u.validarDatosObligatorios(); // Debe pasar
  }
}
