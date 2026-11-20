package ec.twintailcs.electoral.acta.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Service for Notificacion CRUD operations.
 *
 * <p>Notifications inform: - COORDINATOR: when new acta is ENVIADA (pending review) - OBSERVER:
 * when acta is APROBADA or RECHAZADA
 *
 * <p>Notification table uses native SQL for compatibility with backend Spring. Table schema:
 * notificacion(id, destinatario_id, tipo, acta_id, titulo, mensaje, leida, creado_en)
 */
@ApplicationScoped
public class NotificacionService {

  @Inject EntityManager entityManager;

  /**
   * Create a notification for a user.
   *
   * @param usuarioId recipient user id
   * @param tipo notification type (ACTA_ENVIADA, ACTA_APROBADA, ACTA_RECHAZADA)
   * @param titulo notification title (optional, may be null for backward compatibility)
   * @param mensaje notification message
   * @param actaId related acta id (optional)
   */
  @Transactional
  public void crearNotificacion(
      Long usuarioId, String tipo, String titulo, String mensaje, Long actaId) {
    entityManager
        .createNativeQuery(
            "insert into notificacion (destinatario_id, tipo, titulo, acta_id, mensaje, leida, creado_en)"
                + " values (:usuarioId, :tipo, :titulo, :actaId, :mensaje, false, now())")
        .setParameter("usuarioId", usuarioId)
        .setParameter("tipo", tipo)
        .setParameter("titulo", titulo)
        .setParameter("actaId", actaId)
        .setParameter("mensaje", mensaje)
        .executeUpdate();
  }

  /**
   * Create a notification without title (backward compatible).
   *
   * @param usuarioId recipient user id
   * @param tipo notification type
   * @param mensaje notification message
   * @param actaId related acta id (optional)
   */
  @Transactional
  public void crearNotificacion(Long usuarioId, String tipo, String mensaje, Long actaId) {
    crearNotificacion(usuarioId, tipo, null, mensaje, actaId);
  }

  /**
   * Get pending (unread) notifications for a user.
   *
   * @param usuarioId user id
   * @param limit max number of notifications (optional, default 10)
   * @return List of notification rows as Object arrays
   */
  @Transactional
  public List<Object[]> obtenerPendientes(Long usuarioId, int limit) {
    String sql =
        "select id, tipo, titulo, mensaje, acta_id, leida, creado_en"
            + " from notificacion"
            + " where destinatario_id = :usuarioId and leida = false"
            + " order by creado_en desc, id desc"
            + " limit :limit";
    return entityManager
        .createNativeQuery(sql, Object[].class)
        .setParameter("usuarioId", usuarioId)
        .setParameter("limit", limit)
        .getResultList();
  }

  /**
   * Mark a notification as read.
   *
   * @param notificacionId notification id
   */
  @Transactional
  public void marcarComoLeida(Long notificacionId) {
    entityManager
        .createNativeQuery("update notificacion set leida = true where id = :id")
        .setParameter("id", notificacionId)
        .executeUpdate();
  }

  /**
   * Count unread notifications for a user.
   *
   * @param usuarioId user id
   * @return count of unread notifications
   */
  @Transactional
  public long contarPendientes(Long usuarioId) {
    Object result =
        entityManager
            .createNativeQuery(
                "select count(*) from notificacion where destinatario_id = :usuarioId and leida = false")
            .setParameter("usuarioId", usuarioId)
            .getSingleResult();
    return ((Number) result).longValue();
  }
}
