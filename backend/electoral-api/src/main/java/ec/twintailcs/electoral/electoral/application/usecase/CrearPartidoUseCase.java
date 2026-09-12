package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrearPartidoUseCase {

  private final PartidoRepository partidoRepository;
  private final AuditLogService auditLogService;

  public CrearPartidoUseCase(PartidoRepository partidoRepository, AuditLogService auditLogService) {
    this.partidoRepository = partidoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Partido execute(PartidoRequest dto, Long masterId, String ipAddress) {
    partidoRepository
        .findByNumeroLista(dto.numeroLista())
        .ifPresent(
            p -> {
              throw new ConfiguracionConflictException(
                  "Ya existe un partido con numeroLista " + dto.numeroLista());
            });

    Partido partido = new Partido(dto.nombre(), dto.siglas(), dto.numeroLista());
    Partido guardado = partidoRepository.save(partido);

    auditLogService.registrarConIp(
        masterId,
        "CREAR_PARTIDO",
        "partido",
        guardado.getId(),
        "Partido creado: " + dto.siglas(),
        ipAddress);
    return guardado;
  }
}
