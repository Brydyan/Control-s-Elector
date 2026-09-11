package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrearCandidatoUseCase {

  private final CandidatoRepository candidatoRepository;
  private final CargoRepository cargoRepository;
  private final PartidoRepository partidoRepository;
  private final AuditLogService auditLogService;

  public CrearCandidatoUseCase(
      CandidatoRepository candidatoRepository,
      CargoRepository cargoRepository,
      PartidoRepository partidoRepository,
      AuditLogService auditLogService) {
    this.candidatoRepository = candidatoRepository;
    this.cargoRepository = cargoRepository;
    this.partidoRepository = partidoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Candidato execute(CandidatoRequest dto, Long masterId, HttpServletRequest request) {
    Cargo cargo =
        cargoRepository
            .findById(dto.cargoId())
            .orElseThrow(
                () -> new ConfiguracionNotFoundException("Cargo no encontrado: " + dto.cargoId()));

    Partido partido =
        partidoRepository
            .findById(dto.partidoId())
            .orElseThrow(
                () ->
                    new ConfiguracionNotFoundException(
                        "Partido no encontrado: " + dto.partidoId()));

    Candidato candidato = new Candidato(dto.nombre(), dto.orden(), cargo, partido);
    Candidato guardado = candidatoRepository.save(candidato);

    auditLogService.registrar(
        masterId,
        "CREAR_CANDIDATO",
        "candidato",
        guardado.getId(),
        "Candidato: " + dto.nombre() + " para cargo " + cargo.getNombre(),
        request);
    return guardado;
  }
}
