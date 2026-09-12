package ec.twintailcs.electoral.evidencia.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ec.twintailcs.electoral.acta.domain.Acta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenciaTest {

  @Test
  @DisplayName("Debe validar que sizeBytes sea mayor a cero")
  void debeFallarSiSizeBytesMenorOIgualAZero() {
    Evidencia e = new Evidencia();
    assertThrows(EvidenciaInvalidaException.class, () -> e.setSizeBytes(0));
    assertThrows(EvidenciaInvalidaException.class, () -> e.setSizeBytes(-50));
    e.setSizeBytes(100);
    assertEquals(100, e.getSizeBytes());
  }

  @Test
  @DisplayName("Debe validar que S3Key no sea nulo o vacío")
  void debeFallarSiS3KeyVacio() {
    Evidencia e = new Evidencia();
    assertThrows(EvidenciaInvalidaException.class, () -> e.setS3Key(null));
    assertThrows(EvidenciaInvalidaException.class, () -> e.setS3Key("   "));
    e.setS3Key("actas/1/test.webp");
    assertEquals("actas/1/test.webp", e.getS3Key());
  }

  @Test
  @DisplayName("Debe validar datos obligatorios correctamente")
  void debeValidarDatosObligatorios() {
    Evidencia e = new Evidencia();
    assertThrows(EvidenciaInvalidaException.class, e::validarDatosObligatorios);

    e.setActa(new Acta());
    assertThrows(EvidenciaInvalidaException.class, e::validarDatosObligatorios);

    e.setJrvId(1L);
    assertThrows(EvidenciaInvalidaException.class, e::validarDatosObligatorios);

    e.setCargoId(2L);
    assertThrows(EvidenciaInvalidaException.class, e::validarDatosObligatorios);

    e.setUsuarioId(3L);
    assertThrows(EvidenciaInvalidaException.class, e::validarDatosObligatorios);

    e.setS3Key("key.webp");
    assertThrows(EvidenciaInvalidaException.class, e::validarDatosObligatorios);

    e.setContentType("image/webp");
    assertThrows(EvidenciaInvalidaException.class, e::validarDatosObligatorios);

    e.setSizeBytes(1024);
    e.validarDatosObligatorios(); // Debe pasar sin excepción
  }
}
