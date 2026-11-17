package ec.twintailcs.electoral.geografia.application;

import ec.twintailcs.electoral.geografia.application.usecase.ActualizarCantonUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.CrearCantonUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.EliminarCantonUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.ListarGeografiaUseCase;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.web.dto.CantonRequest;
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
  @Inject CrearCantonUseCase crearCanton;
  @Inject ActualizarCantonUseCase actualizarCanton;
  @Inject EliminarCantonUseCase eliminarCanton;

  /** Arbol geografico completo. */
  public GeografiaTreeDto listarArbol() {
    return listarGeografia.execute();
  }

  /** Alta de canton. */
  public Canton crearCanton(CantonRequest dto, Long masterId, String ipAddress) {
    return crearCanton.execute(dto, masterId, ipAddress);
  }

  /** Edicion de canton. */
  public Canton actualizarCanton(Long id, CantonRequest dto, Long masterId, String ipAddress) {
    return actualizarCanton.execute(id, dto, masterId, ipAddress);
  }

  /** Baja de canton. */
  public void eliminarCanton(Long id, Long masterId, String ipAddress) {
    eliminarCanton.execute(id, masterId, ipAddress);
  }
}
