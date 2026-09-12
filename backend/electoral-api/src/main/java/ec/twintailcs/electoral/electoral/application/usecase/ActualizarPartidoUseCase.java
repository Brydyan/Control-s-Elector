package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActualizarPartidoUseCase {

  private final PartidoRepository partidoRepository;
  private final AuditLogService auditLogService;

  public ActualizarPartidoUseCase(
      PartidoRepository partidoRepository, AuditLogService auditLogService) {
    this.partidoRepository = partidoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Partido execute(Long id, PartidoRequest dto, Long masterId, String ipAddress) {
    Partido partido =
        partidoRepository
            .findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Partido no encontrado: " + id));

    partidoRepository
        .findByNumeroLista(dto.numeroLista())
        .filter(p -> !p.getId().equals(id))
        .ifPresent(
            p -> {
              throw new ConfiguracionConflictException(
                  "Ya existe un partido con numeroLista " + dto.numeroLista());
            });

    partido.setNombre(dto.nombre());
    partido.setSiglas(dto.siglas());
    partido.setNumeroLista(dto.numeroLista());
    Partido actualizado = partidoRepository.save(partido);

    auditLogService.registrarConIp(
        masterId,
        "ACTUALIZAR_PARTIDO",
        "partido",
        id,
        "Partido actualizado: " + dto.siglas(),
        ipAddress);
    return actualizado;
  }
}
