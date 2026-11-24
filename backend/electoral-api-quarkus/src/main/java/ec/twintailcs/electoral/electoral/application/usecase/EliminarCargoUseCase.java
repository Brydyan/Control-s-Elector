package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Baja de un cargo electoral. */
@ApplicationScoped
public class EliminarCargoUseCase {

  @Inject ConfiguracionAuditService audit;

  @Inject InvalidadorDeCache invalidador;

  /** Elimina el cargo. Si no existe, la operacion se rechaza con 404. */
  @Transactional
  public void execute(Long id, Long masterId, String ipAddress) {
    Cargo cargo = Cargo.findById(id);
    if (cargo == null) {
      throw new ConfiguracionNotFoundException("Cargo no encontrado: " + id);
    }

    cargo.delete();

    audit.registrarConIp(
        masterId,
        "ELIMINAR_CARGO",
        "cargo",
        id,
        "Cargo eliminado: " + cargo.getNombre(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("cargos");
  }
}
