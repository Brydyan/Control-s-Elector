package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.domain.Partido;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/** Listado de partidos politicos. */
@ApplicationScoped
public class ListarPartidosUseCase {

  /** Todos los partidos, sin filtros. */
  @Transactional
  public List<Partido> execute() {
    return Partido.listAll();
  }
}
