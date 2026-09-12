package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrearCargoUseCase {

  private final CargoRepository cargoRepository;
  private final AuditLogService auditLogService;

  public CrearCargoUseCase(CargoRepository cargoRepository, AuditLogService auditLogService) {
    this.cargoRepository = cargoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Cargo execute(CargoRequest dto, Long masterId, HttpServletRequest request) {
    Cargo cargo = new Cargo(dto.nombre(), dto.nivel(), dto.puestos());
    Cargo guardado = cargoRepository.save(cargo);

    auditLogService.registrar(
        masterId,
        "CREAR_CARGO",
        "cargo",
        guardado.getId(),
        "Cargo creado: " + dto.nombre(),
        request);
    return guardado;
  }
}
