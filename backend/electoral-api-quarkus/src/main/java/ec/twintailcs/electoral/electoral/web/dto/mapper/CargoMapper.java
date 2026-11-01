package ec.twintailcs.electoral.electoral.web.dto.mapper;

import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.web.dto.CargoResponse;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapeo entre la entidad {@link Cargo} y el DTO {@link CargoResponse}.
 *
 * <p>En el backend Spring esto lo hace MapStruct. Aca el mapper es a mano, como en el modulo de
 * auth: la forma del DTO es la misma que en Spring.
 */
@ApplicationScoped
public class CargoMapper {

  /** DTO de un cargo. */
  public CargoResponse toDto(Cargo entity) {
    if (entity == null) {
      return null;
    }
    return new CargoResponse(
        entity.getId(), entity.getNombre(), entity.getNivel(), entity.getPuestos());
  }
}
