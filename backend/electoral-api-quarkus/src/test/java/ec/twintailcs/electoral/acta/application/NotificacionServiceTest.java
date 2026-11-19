package ec.twintailcs.electoral.acta.application;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for NotificacionService.
 *
 * <p>Tests notification creation, retrieval, and read status: - Crear notificación - Obtener
 * notificaciones pendientes - Marcar como leída - Contar notificaciones sin leer
 */
@QuarkusTest
class NotificacionServiceTest {

  @Inject NotificacionService service;
  @Inject EntityManager entityManager;

  @BeforeEach
  @Transactional
  void setUpTestData() {
    new ActaTestFixture(entityManager).setUpTestData();
  }

  @Test
  @Transactional
  void crearNotificacionExitosa() {
    // Setup
    Long usuarioId = 1L;
    String tipo = "ACTA_ENVIADA";
    String titulo = "Nueva acta pendiente";
    String mensaje = "Se ha enviado un acta para revisión";
    Long actaId = 123L;

    // Act
    service.crearNotificacion(usuarioId, tipo, titulo, mensaje, actaId);

    // Assert: query the database to verify creation
    Object result =
        entityManager
            .createNativeQuery(
                "select count(*) from notificacion"
                    + " where destinatario_id = :usuarioId and tipo = :tipo")
            .setParameter("usuarioId", usuarioId)
            .setParameter("tipo", tipo)
            .getSingleResult();
    assertEquals(1, ((Number) result).longValue());
  }

  @Test
  @Transactional
  void crearNotificacionSinTitulo() {
    // Setup
    Long usuarioId = 2L;
    String tipo = "ACTA_APROBADA";
    String mensaje = "Tu acta ha sido aprobada";
    Long actaId = 124L;

    // Act
    service.crearNotificacion(usuarioId, tipo, mensaje, actaId);

    // Assert: verify creation with null titulo
    Object result =
        entityManager
            .createNativeQuery(
                "select count(*) from notificacion"
                    + " where destinatario_id = :usuarioId and tipo = :tipo and titulo is null")
            .setParameter("usuarioId", usuarioId)
            .setParameter("tipo", tipo)
            .getSingleResult();
    assertEquals(1, ((Number) result).longValue());
  }

  @Test
  @Transactional
  void obtenerPendientesDelUsuario() {
    // Setup: create multiple notifications
    Long usuarioId = 3L;
    service.crearNotificacion(usuarioId, "ACTA_ENVIADA", "Notif 1", 101L);
    service.crearNotificacion(usuarioId, "ACTA_APROBADA", "Notif 2", 102L);
    service.crearNotificacion(4L, "ACTA_ENVIADA", "Notif 3", 103L); // Different user

    // Act
    List<Object[]> pendientes = service.obtenerPendientes(usuarioId, 10);

    // Assert
    assertEquals(2, pendientes.size(), "Should return only notifications for the given user");
  }

  @Test
  @Transactional
  void marcarComoLeidaActualiza() {
    // Setup: create a notification
    Long usuarioId = 5L;
    service.crearNotificacion(usuarioId, "ACTA_ENVIADA", "Test", 105L);

    // Get notification id
    Long notifId =
        ((Number)
                entityManager
                    .createNativeQuery("select id from notificacion where destinatario_id = ?1")
                    .setParameter(1, usuarioId)
                    .getSingleResult())
            .longValue();

    // Act
    service.marcarComoLeida(notifId);

    // Assert
    Boolean leida =
        (Boolean)
            entityManager
                .createNativeQuery("select leida from notificacion where id = ?1")
                .setParameter(1, notifId)
                .getSingleResult();
    assertTrue(leida, "Notification should be marked as read");
  }

  @Test
  @Transactional
  void contarPendientesDelUsuario() {
    // Setup
    Long usuarioId = 6L;
    service.crearNotificacion(usuarioId, "ACTA_ENVIADA", "1", 106L);
    service.crearNotificacion(usuarioId, "ACTA_APROBADA", "2", 107L);
    service.crearNotificacion(usuarioId, "ACTA_ENVIADA", "3", 108L);

    // Act
    long count = service.contarPendientes(usuarioId);

    // Assert
    assertEquals(3, count, "Should count 3 pending notifications");
  }

  @Test
  @Transactional
  void obtenerPendientesRespetaLimite() {
    // Setup: create 15 notifications
    Long usuarioId = 7L;
    for (int i = 0; i < 15; i++) {
      service.crearNotificacion(usuarioId, "ACTA_ENVIADA", "Notif " + i, 110L + i);
    }

    // Act
    List<Object[]> pendientes = service.obtenerPendientes(usuarioId, 5);

    // Assert
    assertEquals(5, pendientes.size(), "Should respect the limit parameter");
  }

  @Test
  @Transactional
  void obtenerPendientesOrdenaDescendentePorFecha() {
    // Setup: create notifications
    Long usuarioId = 8L;
    service.crearNotificacion(usuarioId, "ACTA_ENVIADA", "Notif 1", 121L);
    Thread.yield(); // Small delay to ensure different timestamps
    service.crearNotificacion(usuarioId, "ACTA_APROBADA", "Notif 2", 122L);

    // Act
    List<Object[]> pendientes = service.obtenerPendientes(usuarioId, 10);

    // Assert: second notification should come first (most recent)
    assertEquals(2, pendientes.size());
    String tipo2 = (String) pendientes.get(0)[1];
    assertEquals("ACTA_APROBADA", tipo2, "Most recent notification should come first");
  }

  @Test
  @Transactional
  void contarPendientesSoloNoLeidasDelUsuario() {
    // Setup
    Long usuarioId = 9L;
    service.crearNotificacion(usuarioId, "ACTA_ENVIADA", "1", 123L);
    service.crearNotificacion(usuarioId, "ACTA_ENVIADA", "2", 124L);

    // Mark first one as read
    Long notifId =
        ((Number)
                entityManager
                    .createNativeQuery(
                        "select id from notificacion where destinatario_id = ?1 limit 1")
                    .setParameter(1, usuarioId)
                    .getSingleResult())
            .longValue();
    service.marcarComoLeida(notifId);

    // Act
    long count = service.contarPendientes(usuarioId);

    // Assert
    assertEquals(1, count, "Should count only unread notifications");
  }
}
