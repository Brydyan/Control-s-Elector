package ec.twintailcs.electoral.auth.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SesionTest {

  @Test
  @DisplayName("Debe validar datos obligatorios de la sesión")
  void debeValidarDatosObligatorios() {
    Sesion sesion = new Sesion();
    assertThrows(IllegalArgumentException.class, sesion::validarDatosObligatorios);

    sesion.setUsuarioId(1L);
    assertThrows(IllegalArgumentException.class, sesion::validarDatosObligatorios);

    sesion.setSesionId("uuid-sesion-id");
    assertThrows(IllegalArgumentException.class, sesion::validarDatosObligatorios);

    sesion.setRefreshHash("hash-secret");
    assertThrows(IllegalArgumentException.class, sesion::validarDatosObligatorios);

    sesion.setExpiraEn(Instant.now().plusSeconds(3600));
    assertDoesNotThrow(sesion::validarDatosObligatorios);
  }
}
