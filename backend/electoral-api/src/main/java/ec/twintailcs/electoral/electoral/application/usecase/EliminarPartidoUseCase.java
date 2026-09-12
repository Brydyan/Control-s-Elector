package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EliminarPartidoUseCase {

  private final PartidoRepository partidoRepository;
  private final AuditLogService auditLogService;

  public EliminarPartidoUseCase(
      PartidoRepository partidoRepository, AuditLogService auditLogService) {
    this.partidoRepository = partidoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public void execute(Long id, Long masterId, HttpServletRequest request) {
    Partido partido =
        partidoRepository
            .findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Partido no encontrado: " + id));

    partidoRepository.delete(partido);

    auditLogService.registrar(
        masterId,
        "ELIMINAR_PARTIDO",
        "partido",
        id,
        "Partido eliminado: " + partido.getSiglas(),
        request);
  }
}
