package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.application.ActaAuditService;
import ec.twintailcs.electoral.acta.application.ActaNotificacionService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;

/** Transicion ENVIADA/CORREGIDA → APROBADA: el coordinator aprueba el acta. */
@ApplicationScoped
public class AprobarActaUseCase {

  @Inject ActaRepository repository;
  @Inject ActaAuditService audit;
  @Inject ActaNotificacionService notificacion;
  @Inject RlsContexto rls;

  /**
   * Aprueba el acta.
   *
   * @param actaId id interno del acta
   * @param coordinatorId id interno del coordinator que aprueba
   * @param ipAddress IP del cliente
   */
  @Transactional
  public Acta execute(Long actaId, Long coordinatorId, String ipAddress) {
    Acta acta =
        repository
            .buscarPorIdConLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.ENVIADA && acta.getEstado() != EstadoActa.CORREGIDA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden aprobar actas ENVIADAS o CORREGIDAS. Estado actual: " + acta.getEstado());
    }

    acta.setEstado(EstadoActa.APROBADA);
    acta.setAprobadaPorId(coordinatorId);
    acta.setFechaAprobacion(Instant.now());

    // Notificar al observer que su acta fue aprobada
    rls.activar(coordinatorId);
    notificacion.notificar(
        acta.getRegistradaPorId(),
        "ACTA_APROBADA",
        actaId,
        "Tu acta (JRV " + acta.getJrvId() + ") ha sido aprobada");

    audit.registrarConIp(
        coordinatorId,
        "ACTA_APROBADA",
        "acta",
        actaId,
        "Acta aprobada por coordinator " + coordinatorId,
        ipAddress);
    return acta;
  }
}
