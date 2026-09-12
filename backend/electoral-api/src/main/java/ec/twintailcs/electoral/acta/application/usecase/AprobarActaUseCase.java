package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AprobarActaUseCase {

  private final ActaRepository actaRepository;
  private final AuditLogService auditLogService;

  public AprobarActaUseCase(ActaRepository actaRepository, AuditLogService auditLogService) {
    this.actaRepository = actaRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Acta execute(Long actaId, Long coordinatorId, String ipAddress) {
    Acta acta =
        actaRepository
            .findByIdWithLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.ENVIADA && acta.getEstado() != EstadoActa.CORREGIDA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden aprobar actas ENVIADAS o CORREGIDAS. Estado actual: " + acta.getEstado());
    }

    acta.setEstado(EstadoActa.APROBADA);
    Acta actaActualizada = actaRepository.save(acta);

    auditLogService.registrarConIp(
        coordinatorId,
        "ACTA_APROBADA",
        "acta",
        actaId,
        "Acta aprobada por coordinator " + coordinatorId,
        ipAddress);
    return actaActualizada;
  }
}
