package ec.twintailcs.electoral.electoral.application.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.shared.cache.CacheDeLectura;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
   *
   * <p><b>Sin transaccion.</b> Es una lectura: no escribe en la base. Y la transaccion de JTA era
   * un problema, no una ayuda: la llamada a la cache va dentro del caso de uso, y con la
   * transaccion abierta un backend de cache lento (el contenedor del CI que aun no esta listo)
   * hacia esperar hasta que Narayana cancelaba la transaccion a los 60 s; el commit fallaba con
   * {@code RollbackException} y el listado respondia 500 en vez de degradarse a "se sirve desde la
   * base". Sin transaccion, la cache es una degradacion y punto.
   */
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
