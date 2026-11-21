package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Edicion de un cargo electoral. */
@ApplicationScoped
public class ActualizarCargoUseCase {

  @Inject ConfiguracionAuditService audit;

  /** Actualiza el cargo. Si no existe, la operacion se rechaza con 404. */
  @CacheInvalidateAll(cacheName = "cargos")
  @Transactional
  public Cargo execute(Long id, CargoRequest dto, Long masterId, String ipAddress) {
    Cargo cargo = Cargo.findById(id);
    if (cargo == null) {
      throw new ConfiguracionNotFoundException("Cargo no encontrado: " + id);
    }

    cargo.setNombre(dto.nombre());
    cargo.setNivel(dto.nivel());
    cargo.setPuestos(dto.puestos());

    audit.registrarConIp(
        masterId, "ACTUALIZAR_CARGO", "cargo", id, "Cargo actualizado: " + dto.nombre(), ipAddress);
    return cargo;
  }
}
