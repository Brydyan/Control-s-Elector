package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EliminarCandidatoUseCase {

  private final CandidatoRepository candidatoRepository;
  private final AuditLogService auditLogService;

  public EliminarCandidatoUseCase(
      CandidatoRepository candidatoRepository, AuditLogService auditLogService) {
    this.candidatoRepository = candidatoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public void execute(Long id, Long masterId, String ipAddress) {
    Candidato candidato =
        candidatoRepository
            .findById(id)
            .orElseThrow(
                () -> new ConfiguracionNotFoundException("Candidato no encontrado: " + id));

    candidatoRepository.delete(candidato);

    auditLogService.registrarConIp(
        masterId,
        "ELIMINAR_CANDIDATO",
        "candidato",
        id,
        "Candidato eliminado: " + candidato.getNombre(),
        ipAddress);
  }
}
