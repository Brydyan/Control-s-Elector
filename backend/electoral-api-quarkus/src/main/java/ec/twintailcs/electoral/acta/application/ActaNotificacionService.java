package ec.twintailcs.electoral.acta.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Aviso al observador de que su acta cambio de estado (disputa abierta o acta anulada).
 *
 * <p><b>Por que escribe con SQL nativo.</b> El modulo de notificaciones todavia no esta portado: la
 * tabla {@code notificacion} es de ese modulo y mapear una segunda entidad sobre ella desde el
 * modulo de actas obligaria a mantener dos mapeos en paralelo. Con un INSERT nativo la fila que
 * queda es exactamente la que escribe el backend Spring ({@code destinatario_id}, {@code tipo},
 * {@code acta_id}, {@code mensaje}, {@code leida}, {@code creado_en}), asi que el historial de
 * notificaciones es identico entre las dos implementaciones.
 *
 * <p>La tabla tiene RLS activada pero sin politica de insercion: Postgres permite la escritura
 * cuando ninguna politica aplica a la tabla. Es el mismo comportamiento que tiene el backend
 * Spring, que escribe la misma fila.
 */
@ApplicationScoped
public class ActaNotificacionService {

  @Inject EntityManager entityManager;

  /**
   * Notifica al destinatario sobre un cambio en el acta.
   *
   * @param destinatarioId id interno del usuario que recibe el aviso
   * @param tipo tipo de aviso ({@code ACTA_EN_DISPUTA}, {@code ACTA_ANULADA})
   * @param actaId id interno del acta
   * @param mensaje mensaje legible del aviso
   */
  public void notificar(Long destinatarioId, String tipo, Long actaId, String mensaje) {
    entityManager
        .createNativeQuery(
            "insert into notificacion (destinatario_id, tipo, acta_id, mensaje, leida, creado_en)"
                + " values (:destinatarioId, :tipo, :actaId, :mensaje, false, now())")
        .setParameter("destinatarioId", destinatarioId)
        .setParameter("tipo", tipo)
        .setParameter("actaId", actaId)
        .setParameter("mensaje", mensaje)
        .executeUpdate();
  }
}
