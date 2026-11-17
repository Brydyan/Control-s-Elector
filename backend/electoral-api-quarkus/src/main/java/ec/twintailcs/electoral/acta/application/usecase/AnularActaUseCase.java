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

/** Transicion APROBADA → ANULADA: el MASTER anula el acta. Es irreversible. */
@ApplicationScoped
public class AnularActaUseCase {

  @Inject ActaRepository repository;
  @Inject ActaNotificacionService notificacion;
  @Inject ActaAuditService audit;
  @Inject RlsContexto rls;

  /**
   * Anula el acta.
   *
   * @param actaId id interno del acta
   * @param masterId id interno del MASTER que anula
   * @param motivo motivo de la anulacion
   * @param ipAddress IP del cliente
   */
  @Transactional
  public Acta execute(Long actaId, Long masterId, String motivo, String ipAddress) {
    Acta acta =
        repository
            .buscarPorIdConLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.APROBADA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden anular actas APROBADAS. Estado actual: " + acta.getEstado());
    }

    acta.setEstado(EstadoActa.ANULADA);
    acta.setMotivoAnulacion(motivo);

    // El aviso va a una tabla con RLS: sin el contexto de la sesion, la politica
    // de insercion no puede evaluarse y la escritura se deniega.
    rls.activar(masterId);

    notificacion.notificar(
        acta.getRegistradaPorId(),
        "ACTA_ANULADA",
        actaId,
        "Tu acta (JRV " + acta.getJrvId() + ") fue anulada. Motivo: " + motivo);

    audit.registrarConIp(masterId, "ACTA_ANULADA", "acta", actaId, "Motivo: " + motivo, ipAddress);
    return acta;
  }
}
