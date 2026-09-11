package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
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
  public void execute(Long id, Long masterId, HttpServletRequest request) {
    Candidato candidato =
        candidatoRepository
            .findById(id)
            .orElseThrow(
                () -> new ConfiguracionNotFoundException("Candidato no encontrado: " + id));

    candidatoRepository.delete(candidato);

    auditLogService.registrar(
        masterId,
        "ELIMINAR_CANDIDATO",
        "candidato",
        id,
        "Candidato eliminado: " + candidato.getNombre(),
        request);
  }
}
