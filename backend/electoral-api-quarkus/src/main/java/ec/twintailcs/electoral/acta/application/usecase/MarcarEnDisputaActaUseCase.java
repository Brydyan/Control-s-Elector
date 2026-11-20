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

/** Transicion ENVIADA/CORREGIDA → EN_DISPUTA: el coordinator abre una disputa. */
@ApplicationScoped
public class MarcarEnDisputaActaUseCase {

  @Inject ActaRepository repository;
  @Inject ActaNotificacionService notificacion;
  @Inject ActaAuditService audit;
  @Inject RlsContexto rls;

  /**
   * Marca el acta en disputa.
   *
   * @param actaId id interno del acta
   * @param coordinatorId id interno del coordinator que disputa
   * @param motivo motivo de la disputa
   * @param ipAddress IP del cliente
   */
  @Transactional
  public Acta execute(Long actaId, Long coordinatorId, String motivo, String ipAddress) {
    Acta acta =
        repository
            .buscarPorIdConLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.ENVIADA && acta.getEstado() != EstadoActa.CORREGIDA) {
      throw new ActaEstadoInvalidoException(
          "No se puede marcar en disputa. Estado actual: " + acta.getEstado());
    }

    if (motivo == null || motivo.trim().isEmpty()) {
      throw new ActaInvalidaException("El motivo del rechazo es requerido");
    }

    acta.setEstado(EstadoActa.EN_DISPUTA);
    acta.setMotivoRechazo(motivo);

    // El aviso va a una tabla con RLS: sin el contexto de la sesion, la politica
    // de insercion no puede evaluarse y la escritura se deniega.
    rls.activar(coordinatorId);

    notificacion.notificar(
        acta.getRegistradaPorId(),
        "ACTA_EN_DISPUTA",
        actaId,
        "Tu acta (JRV " + acta.getJrvId() + ") fue marcada en disputa. Motivo: " + motivo);

    audit.registrarConIp(
        coordinatorId, "ACTA_EN_DISPUTA", "acta", actaId, "Motivo: " + motivo, ipAddress);
    return acta;
  }
}
