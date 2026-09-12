package ec.twintailcs.electoral.acta.application.usecase;

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
public class AnularActaUseCase {

  private final ActaRepository actaRepository;
  private final NotificacionService notificacionService;
  private final AuditLogService auditLogService;

  public AnularActaUseCase(
      ActaRepository actaRepository,
      NotificacionService notificacionService,
      AuditLogService auditLogService) {
    this.actaRepository = actaRepository;
    this.notificacionService = notificacionService;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Acta execute(Long actaId, Long masterId, String motivo, String ipAddress) {
    Acta acta =
        actaRepository
            .findByIdWithLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.APROBADA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden anular actas APROBADAS. Estado actual: " + acta.getEstado());
    }

    acta.setEstado(EstadoActa.ANULADA);
    acta.setMotivoAnulacion(motivo);
    Acta actaActualizada = actaRepository.save(acta);

    notificacionService.notificar(
        acta.getRegistradaPor(),
        "ACTA_ANULADA",
        acta,
        "Tu acta (JRV " + acta.getJrv().getId() + ") fue anulada. Motivo: " + motivo);

    auditLogService.registrarConIp(
        masterId, "ACTA_ANULADA", "acta", actaId, "Motivo: " + motivo, ipAddress);
    return actaActualizada;
  }
}
