package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Baja de un partido politico. */
@ApplicationScoped
public class EliminarPartidoUseCase {

  @Inject ConfiguracionAuditService audit;

  @Inject InvalidadorDeCache invalidador;

  /** Elimina el partido. Si no existe, la operacion se rechaza con 404. */
  @Transactional
  public void execute(Long id, Long masterId, String ipAddress) {
    Partido partido = Partido.findById(id);
    if (partido == null) {
      throw new ConfiguracionNotFoundException("Partido no encontrado: " + id);
    }

    partido.delete();

    audit.registrarConIp(
        masterId,
        "ELIMINAR_PARTIDO",
        "partido",
        id,
        "Partido eliminado: " + partido.getSiglas(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("partidos");
  }
}
