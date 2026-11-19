package ec.twintailcs.electoral.acta.application.usecase;

import static org.junit.jupiter.api.Assertions.*;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ec.twintailcs.electoral.acta.application.ActaTestFixture;

/**
 * Unit tests for MarcarEnDisputaActaUseCase.
 *
 * <p>Tests rejection/dispute workflow and reason storage: - Marcar acta ENVIADA → EN_DISPUTA -
 * Marcar acta CORREGIDA → EN_DISPUTA - Validar que motivo es obligatorio - Validar que se guarda
 * motivo_rechazo - Validar que se crea notificación para el observer
 */
@QuarkusTest
class MarcarEnDisputaActaUseCaseTest {

  @Inject MarcarEnDisputaActaUseCase useCase;
  @Inject EntityManager entityManager;

  @BeforeEach
  @Transactional
  void setUpTestData() {
    new ActaTestFixture(entityManager).setUpTestData();
  }

  @Test
  @Transactional
  void marcarEnDisputaGuardaMotivoRechazo() {
    // Setup
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 1L, 100L);
    String motivo = "Votos no coinciden con el acta física";
    Long coordinatorId = 999L;

    // Act
    Acta result = useCase.execute(acta.getId(), coordinatorId, motivo, "192.168.1.1");

    // Assert
    assertEquals(EstadoActa.EN_DISPUTA, result.getEstado());
    assertEquals(motivo, result.getMotivoRechazo());
  }

  @Test
  @Transactional
  void marcarEnDisputaSinMotivoFalla() {
    // Setup
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 2L, 101L);
    Long coordinatorId = 998L;

    // Act & Assert
    assertThrows(
        ActaInvalidaException.class,
        () -> useCase.execute(acta.getId(), coordinatorId, null, "192.168.1.2"));
  }

  @Test
  @Transactional
  void marcarEnDisputaConMotivoVacioFalla() {
    // Setup
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 3L, 102L);
    Long coordinatorId = 997L;

    // Act & Assert
    assertThrows(
        ActaInvalidaException.class,
        () -> useCase.execute(acta.getId(), coordinatorId, "   ", "192.168.1.3"));
  }

  @Test
  @Transactional
  void marcarEnDisputaActaCorregidaExitosa() {
    // Setup
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.CORREGIDA, 4L, 103L);
    String motivo = "Totales no cuadran";
    Long coordinatorId = 996L;

    // Act
    Acta result = useCase.execute(acta.getId(), coordinatorId, motivo, "192.168.1.4");

    // Assert
    assertEquals(EstadoActa.EN_DISPUTA, result.getEstado());
    assertEquals(motivo, result.getMotivoRechazo());
  }

  @Test
  @Transactional
  void marcarEnDisputaCreaNotificacionAlObserver() {
    // Setup
    Long observerId = 500L;
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 5L, observerId);
    String motivo = "Firma ilegible";
    Long coordinatorId = 995L;

    // Act
    useCase.execute(acta.getId(), coordinatorId, motivo, "192.168.1.5");

    // Assert: check that a notification was created for the observer
    Object result =
        entityManager
            .createNativeQuery(
                "select count(*) from notificacion"
                    + " where destinatario_id = :observerId and tipo = 'ACTA_EN_DISPUTA' and acta_id = :actaId")
            .setParameter("observerId", observerId)
            .setParameter("actaId", acta.getId())
            .getSingleResult();
    assertEquals(1, ((Number) result).longValue());
  }

  @Test
  @Transactional
  void marcarEnDisputaMotivoValidaLongitud() {
    // Setup
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 6L, 104L);
    // Create a 500 char string (max allowed)
    String motivo = "a".repeat(500);
    Long coordinatorId = 994L;

    // Act & Assert: should not throw
    Acta result = useCase.execute(acta.getId(), coordinatorId, motivo, "192.168.1.6");
    assertEquals(motivo, result.getMotivoRechazo());
  }

  @Test
  @Transactional
  void marcarEnDisputaMotivoExcedeLongitudFalla() {
    // Setup
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 7L, 105L);
    // Create a 501 char string (exceeds max)
    String motivo = "a".repeat(501);
    Long coordinatorId = 993L;

    // Act & Assert
    assertThrows(
        ActaInvalidaException.class,
        () -> useCase.execute(acta.getId(), coordinatorId, motivo, "192.168.1.7"));
  }

  /** Helper: create or get an acta with a specific estado. */
  protected Acta obtenerOCrearActaConEstado(EstadoActa estado, Long jrvId, Long observerId) {
    Acta acta = new Acta();
    acta.setJrvId(jrvId);
    acta.setCargoId(1L);
    acta.setRegistradaPorId(observerId);
    acta.setEstado(estado);
    acta.setTotalSufragantes(100);
    acta.setVotosBlancos(5);
    acta.setVotosNulos(3);
    acta.persist();
    return acta;
  }
}
