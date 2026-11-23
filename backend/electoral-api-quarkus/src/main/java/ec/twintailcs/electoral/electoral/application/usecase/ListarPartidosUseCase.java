package ec.twintailcs.electoral.electoral.application.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.shared.cache.CacheDeLectura;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/** Listado de partidos politicos. */
@ApplicationScoped
public class ListarPartidosUseCase {

  private static final TypeReference<List<Partido>> TIPO_PARTIDOS = new TypeReference<>() {};

  @Inject CacheDeLectura cache;

  /**
   * Todos los partidos, sin filtros. Cacheado con TTL de 2 minutos.
   *
   * <p>El acceso a la cache va explicito (y no con {@code @CacheResult}) para que el hit ratio
   * quede medido: ver {@link CacheDeLectura}.
   */
  @Transactional
  public List<Partido> execute() {
    Optional<List<Partido>> cacheado = cache.leer("partidos", "todos", TIPO_PARTIDOS);
    if (cacheado.isPresent()) {
      return cacheado.get();
    }
    List<Partido> partidos = Partido.listAll();
    cache.guardar("partidos", "todos", partidos);
    return partidos;
  }
}
