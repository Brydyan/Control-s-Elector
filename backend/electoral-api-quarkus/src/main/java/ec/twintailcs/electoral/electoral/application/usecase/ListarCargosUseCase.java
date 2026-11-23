package ec.twintailcs.electoral.electoral.application.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.shared.cache.CacheDeLectura;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/** Listado de cargos electorales. */
@ApplicationScoped
public class ListarCargosUseCase {

  private static final TypeReference<List<Cargo>> TIPO_CARGOS = new TypeReference<>() {};

  @Inject CacheDeLectura cache;

  /**
   * Todos los cargos, sin filtros. Cacheado con TTL de 2 minutos.
   *
   * <p>El acceso a la cache va explicito (y no con {@code @CacheResult}) para que el hit ratio
   * quede medido: ver {@link CacheDeLectura}.
   */
  @Transactional
  public List<Cargo> execute() {
    Optional<List<Cargo>> cacheado = cache.leer("cargos", "todos", TIPO_CARGOS);
    if (cacheado.isPresent()) {
      return cacheado.get();
    }
    List<Cargo> cargos = Cargo.listAll();
    cache.guardar("cargos", "todos", cargos);
    return cargos;
  }
}
