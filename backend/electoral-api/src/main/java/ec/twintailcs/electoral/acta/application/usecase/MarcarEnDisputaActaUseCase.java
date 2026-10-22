package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.application.ActaAssociations;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.notificacion.application.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarcarEnDisputaActaUseCase {

  private final ActaRepository actaRepository;
  private final NotificacionService notificacionService;
  private final AuditLogService auditLogService;

  public MarcarEnDisputaActaUseCase(
      ActaRepository actaRepository,
      NotificacionService notificacionService,
      AuditLogService auditLogService) {
    this.actaRepository = actaRepository;
    this.notificacionService = notificacionService;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Acta execute(Long actaId, Long coordinatorId, String motivo, String ipAddress) {
    Acta acta =
        actaRepository
            .findByIdWithLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));
    // Materializa jrv/cargo/registradaPor: el controller mapea el DTO fuera de esta transacción.
    ActaAssociations.inicializar(acta);

    if (acta.getEstado() != EstadoActa.ENVIADA && acta.getEstado() != EstadoActa.CORREGIDA) {
      throw new ActaEstadoInvalidoException(
          "No se puede marcar en disputa. Estado actual: " + acta.getEstado());
    }

    acta.setEstado(EstadoActa.EN_DISPUTA);
    Acta actaActualizada = actaRepository.save(acta);

    notificacionService.notificar(
        acta.getRegistradaPor(),
        "ACTA_EN_DISPUTA",
        acta,
        "Tu acta (JRV " + acta.getJrv().getId() + ") fue marcada en disputa. Motivo: " + motivo);

    auditLogService.registrarConIp(
        coordinatorId, "ACTA_EN_DISPUTA", "acta", actaId, "Motivo: " + motivo, ipAddress);
    return actaActualizada;
  }
}
