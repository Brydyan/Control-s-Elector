package ec.twintailcs.electoral.acta.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.twintailcs.electoral.acta.application.ActaTestFixture;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConsultarActaDetalleEvidenciaTest {

  @Inject ConsultarActaDetalleUseCase useCase;
  @Inject EntityManager entityManager;

  @Test
  @TestTransaction
  void muestraLaEvidenciaMasRecienteNoRechazada() {
    new ActaTestFixture(entityManager).setUpTestData();

    Long cargoId =
        ((Number) entityManager.createNativeQuery("select id from cargo limit 1").getSingleResult())
            .longValue();
    Acta acta = new Acta();
    acta.setJrvId(1L);
    acta.setCargoId(cargoId);
    acta.setRegistradaPorId(500L);
    acta.setEstado(EstadoActa.ANULADA);
    acta.setTotalSufragantes(100);
    acta.setVotosBlancos(0);
    acta.setVotosNulos(0);
    acta.persistAndFlush();

    Evidencia rechazada = crearEvidencia(acta, cargoId, "actas/rechazada.webp");
    rechazada.setEstado(EstadoEvidencia.RECHAZADA);
    rechazada.persistAndFlush();

    Evidencia aprobada = crearEvidencia(acta, cargoId, "actas/nueva.webp");
    aprobada.setEstado(EstadoEvidencia.APROBADA);
    aprobada.persistAndFlush();

    var detalle = useCase.ejecutar(acta.getId());

    assertEquals(aprobada.getPublicId(), detalle.evidenciaPublicId());
    assertNotNull(detalle.urlEvidencia());
    assertTrue(detalle.urlEvidencia().contains(aprobada.getS3Key()));
  }

  private Evidencia crearEvidencia(Acta acta, Long cargoId, String s3Key) {
    Evidencia evidencia = new Evidencia();
    evidencia.setActaId(acta.getId());
    evidencia.setJrvId(acta.getJrvId());
    evidencia.setCargoId(cargoId);
    evidencia.setUsuarioId(acta.getRegistradaPorId());
    evidencia.setS3Key(s3Key);
    evidencia.setSizeBytes(1L);
    evidencia.setContentType("image/webp");
    return evidencia;
  }
}
