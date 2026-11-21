package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Baja de un partido politico. */
@ApplicationScoped
public class EliminarPartidoUseCase {

  @Inject ConfiguracionAuditService audit;

  /** Elimina el partido. Si no existe, la operacion se rechaza con 404. */
  @CacheInvalidateAll(cacheName = "partidos")
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
  }
}
