package ec.twintailcs.electoral.geografia.application;

import ec.twintailcs.electoral.geografia.application.usecase.ListarGeografiaUseCase;
import ec.twintailcs.electoral.geografia.web.dto.GeografiaTreeDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fachada de la geografia electoral.
 *
 * <p>Igual que {@code ConfiguracionService}: la facade no hace trabajo, delega en los casos de uso.
 * Asi el resource no toca dominio y cada operacion tiene su propio caso de uso testeable.
 */
@ApplicationScoped
public class GeografiaService {

  @Inject ListarGeografiaUseCase listarGeografia;

  /** Arbol geografico completo. */
  public GeografiaTreeDto listarArbol() {
    return listarGeografia.execute();
  }
}
