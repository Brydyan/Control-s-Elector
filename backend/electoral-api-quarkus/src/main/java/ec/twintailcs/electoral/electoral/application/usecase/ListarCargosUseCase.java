package ec.twintailcs.electoral.electoral.application.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.shared.cache.CacheDeLectura;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
   *
   * <p><b>Sin transaccion.</b> Es una lectura: no escribe en la base. Y la transaccion de JTA era
   * un problema, no una ayuda: la llamada a la cache va dentro del caso de uso, y con la
   * transaccion abierta un backend de cache lento (el contenedor del CI que aun no esta listo)
   * hacia esperar hasta que Narayana cancelaba la transaccion a los 60 s; el commit fallaba con
   * {@code RollbackException} y el listado respondia 500 en vez de degradarse a "se sirve desde la
   * base". Sin transaccion, la cache es una degradacion y punto.
   */
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
