package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActualizarCargoUseCase {

  private final CargoRepository cargoRepository;
  private final AuditLogService auditLogService;

  public ActualizarCargoUseCase(CargoRepository cargoRepository, AuditLogService auditLogService) {
    this.cargoRepository = cargoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Cargo execute(Long id, CargoRequest dto, Long masterId, HttpServletRequest request) {
    Cargo cargo =
        cargoRepository
            .findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Cargo no encontrado: " + id));

    cargo.setNombre(dto.nombre());
    cargo.setNivel(dto.nivel());
    cargo.setPuestos(dto.puestos());
    Cargo actualizado = cargoRepository.save(cargo);

    auditLogService.registrar(
        masterId, "ACTUALIZAR_CARGO", "cargo", id, "Cargo actualizado: " + dto.nombre(), request);
    return actualizado;
  }
}
