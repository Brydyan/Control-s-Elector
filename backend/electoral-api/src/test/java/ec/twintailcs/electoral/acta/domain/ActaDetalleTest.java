package ec.twintailcs.electoral.acta.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ec.twintailcs.electoral.electoral.domain.Candidato;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActaDetalleTest {

  @Test
  @DisplayName("Debe instanciar correctamente con datos válidos")
  void debeInstanciarConDatosValidos() {
    Acta acta = new Acta();
    Candidato candidato = new Candidato();
    ActaDetalle detalle = new ActaDetalle(acta, candidato, 10);

    assertEquals(acta, detalle.getActa());
    assertEquals(candidato, detalle.getCandidato());
    assertEquals(10, detalle.getVotos());
  }

  @Test
  @DisplayName("Debe lanzar excepción si acta es nula")
  void debeFallarSiActaEsNula() {
    Candidato candidato = new Candidato();
    assertThrows(ActaInvalidaException.class, () -> new ActaDetalle(null, candidato, 5));
  }

  @Test
  @DisplayName("Debe lanzar excepción si candidato es nulo")
  void debeFallarSiCandidatoEsNulo() {
    Acta acta = new Acta();
    assertThrows(ActaInvalidaException.class, () -> new ActaDetalle(acta, null, 5));
  }

  @Test
  @DisplayName("Debe lanzar excepción si votos es nulo o negativo")
  void debeFallarSiVotosInvalido() {
    Acta acta = new Acta();
    Candidato candidato = new Candidato();

    assertThrows(ActaInvalidaException.class, () -> new ActaDetalle(acta, candidato, null));
    assertThrows(ActaInvalidaException.class, () -> new ActaDetalle(acta, candidato, -1));

    ActaDetalle detalle = new ActaDetalle(acta, candidato, 5);
    assertThrows(ActaInvalidaException.class, () -> detalle.setVotos(null));
    assertThrows(ActaInvalidaException.class, () -> detalle.setVotos(-3));
  }
}
