package ec.twintailcs.electoral.acta.application.usecase;

import static org.junit.jupiter.api.Assertions.*;

import ec.twintailcs.electoral.acta.application.ActaTestFixture;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AprobarActaUseCase.
 *
 * <p>Tests approval workflow and notification creation: - Aprobar acta ENVIADA → APROBADA - Aprobar
 * acta CORREGIDA → APROBADA - Validar que solo ENVIADA/CORREGIDA pueden aprobarse - Validar que se
 * guarda aprobada_por y fecha_aprobacion - Validar que se crea notificación para el observer
 */
@QuarkusTest
class AprobarActaUseCaseTest {

  @Inject AprobarActaUseCase useCase;
  @Inject EntityManager entityManager;

  @BeforeEach
  @Transactional
  void setUpTestData() {
    new ActaTestFixture(entityManager).setUpTestData();
  }

  @Test
  @Transactional
  void aprobarActaEnviadaExitosa() {
    // Setup: get an ENVIADA acta
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 1L, 100L);

    // Act
    Long coordinatorId = 999L;
    Acta result = useCase.execute(acta.getId(), coordinatorId, "192.168.1.1");

    // Assert
    assertEquals(EstadoActa.APROBADA, result.getEstado());
    assertEquals(coordinatorId, result.getAprobadaPorId());
    assertNotNull(result.getFechaAprobacion());
  }

  @Test
  @Transactional
  void aprobarActaCorregidaExitosa() {
    // Setup: get a CORREGIDA acta
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.CORREGIDA, 2L, 101L);

    // Act
    Long coordinatorId = 998L;
    Acta result = useCase.execute(acta.getId(), coordinatorId, "192.168.1.2");

    // Assert
    assertEquals(EstadoActa.APROBADA, result.getEstado());
    assertEquals(coordinatorId, result.getAprobadaPorId());
  }

  @Test
  @Transactional
  void aprobarActaBorradorFalla() {
    // Setup: get a BORRADOR acta
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.BORRADOR, 3L, 102L);

    // Act & Assert
    Long coordinatorId = 997L;
    assertThrows(
        ActaEstadoInvalidoException.class,
        () -> useCase.execute(acta.getId(), coordinatorId, "192.168.1.3"));
  }

  @Test
  @Transactional
  void aprobarActaAprobadaFalla() {
    // Setup: get an APROBADA acta
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.APROBADA, 4L, 103L);

    // Act & Assert
    Long coordinatorId = 996L;
    assertThrows(
        ActaEstadoInvalidoException.class,
        () -> useCase.execute(acta.getId(), coordinatorId, "192.168.1.4"));
  }

  @Test
  @Transactional
  void aprobarActaNoExistenteFalla() {
    // Act & Assert
    Long nonExistentId = 999999L;
    Long coordinatorId = 995L;
    assertThrows(
        ActaInvalidaException.class,
        () -> useCase.execute(nonExistentId, coordinatorId, "192.168.1.5"));
  }

  @Test
  @Transactional
  void aprobarActaCreaNotificacionAlObserver() {
    // Setup
    Long observerId = 500L;
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 5L, observerId);
    Long coordinatorId = 994L;

    // Act
    useCase.execute(acta.getId(), coordinatorId, "192.168.1.6");

    // Assert: check that a notification was created for the observer
    Object result =
        entityManager
            .createNativeQuery(
                "select count(*) from notificacion"
                    + " where destinatario_id = :observerId and tipo = 'ACTA_APROBADA' and acta_id = :actaId")
            .setParameter("observerId", observerId)
            .setParameter("actaId", acta.getId())
            .getSingleResult();
    assertEquals(1, ((Number) result).longValue());
  }

  @Test
  @Transactional
  void aprobarActaGuardaFechaAprobacion() {
    // Setup
    Acta acta = obtenerOCrearActaConEstado(EstadoActa.ENVIADA, 6L, 104L);
    Instant before = Instant.now();
    Long coordinatorId = 993L;

    // Act
    Acta result = useCase.execute(acta.getId(), coordinatorId, "192.168.1.7");
    Instant after = Instant.now();

    // Assert
    assertNotNull(result.getFechaAprobacion());
    assertTrue(
        result.getFechaAprobacion().isAfter(before.minusSeconds(1)),
        "Fecha de aprobación debe ser después del inicio del test");
    assertTrue(
        result.getFechaAprobacion().isBefore(after.plusSeconds(1)),
        "Fecha de aprobación debe ser antes del final del test");
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
