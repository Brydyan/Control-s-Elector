package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.domain.Cargo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/** Listado de cargos electorales. */
@ApplicationScoped
public class ListarCargosUseCase {

  /** Todos los cargos, sin filtros. */
  @Transactional
  public List<Cargo> execute() {
    return Cargo.listAll();
  }
}
