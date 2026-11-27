package ec.twintailcs.electoral.acta.application.usecase;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import org.junit.jupiter.api.Test;

class ConsultarActaDetalleEvidenciaSeleccionTest {

  @Test
  void prefiereLaEvidenciaMasRecienteNoRechazada() {
    Evidencia elegible = evidencia(EstadoEvidencia.APROBADA);
    Evidencia ultimaRechazada = evidencia(EstadoEvidencia.RECHAZADA);

    assertSame(
        elegible, ConsultarActaDetalleUseCase.seleccionarEvidencia(elegible, ultimaRechazada));
  }

  @Test
  void usaLaMasRecienteSiTodasEstanRechazadas() {
    Evidencia ultimaRechazada = evidencia(EstadoEvidencia.RECHAZADA);

    assertSame(
        ultimaRechazada, ConsultarActaDetalleUseCase.seleccionarEvidencia(null, ultimaRechazada));
  }

  @Test
  void noSeleccionaNadaSiNoHayEvidencia() {
    assertNull(ConsultarActaDetalleUseCase.seleccionarEvidencia(null, null));
  }

  private Evidencia evidencia(EstadoEvidencia estado) {
    Evidencia evidencia = new Evidencia();
    evidencia.setEstado(estado);
    return evidencia;
  }
}
