package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EliminarCargoUseCase {

  private final CargoRepository cargoRepository;
  private final AuditLogService auditLogService;

  public EliminarCargoUseCase(CargoRepository cargoRepository, AuditLogService auditLogService) {
    this.cargoRepository = cargoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public void execute(Long id, Long masterId, HttpServletRequest request) {
    Cargo cargo =
        cargoRepository
            .findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Cargo no encontrado: " + id));

    cargoRepository.delete(cargo);

    auditLogService.registrar(
        masterId, "ELIMINAR_CARGO", "cargo", id, "Cargo eliminado: " + cargo.getNombre(), request);
  }
}
