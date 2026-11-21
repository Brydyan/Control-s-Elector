package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.domain.Cargo;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/** Listado de cargos electorales. */
@ApplicationScoped
public class ListarCargosUseCase {

  /** Todos los cargos, sin filtros. Cacheado con TTL de 10 minutos. */
  @Transactional
  @CacheResult(cacheName = "cargos")
  public List<Cargo> execute() {
    return Cargo.listAll();
  }
}
