package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Alta de un cargo electoral. */
@ApplicationScoped
public class CrearCargoUseCase {

  @Inject ConfiguracionAuditService audit;

  @Inject InvalidadorDeCache invalidador;

  /** Crea el cargo y deja rastro en la auditoria. */
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
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("cargos");
    return cargo;
  }
}
