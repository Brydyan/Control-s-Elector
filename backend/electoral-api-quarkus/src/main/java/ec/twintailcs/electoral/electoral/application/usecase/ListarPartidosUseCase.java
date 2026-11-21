package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.domain.Partido;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/** Listado de partidos politicos. */
@ApplicationScoped
public class ListarPartidosUseCase {

  /** Todos los partidos, sin filtros. Cacheado con TTL de 10 minutos. */
  @Transactional
  @CacheResult(cacheName = "partidos")
  public List<Partido> execute() {
    return Partido.listAll();
  }
}
