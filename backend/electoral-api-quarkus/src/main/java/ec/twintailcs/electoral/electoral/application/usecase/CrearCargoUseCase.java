package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Alta de un cargo electoral. */
@ApplicationScoped
public class CrearCargoUseCase {

  @Inject ConfiguracionAuditService audit;

  /** Crea el cargo y deja rastro en la auditoria. */
  @CacheInvalidateAll(cacheName = "cargos")
  @Transactional
  public Cargo execute(CargoRequest dto, Long masterId, String ipAddress) {
    Cargo cargo = new Cargo(dto.nombre(), dto.nivel(), dto.puestos());
    cargo.persist();

    audit.registrarConIp(
        masterId,
        "CREAR_CARGO",
        "cargo",
        cargo.getId(),
        "Cargo creado: " + dto.nombre(),
        ipAddress);
    return cargo;
  }
}
