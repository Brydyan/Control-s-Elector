package ec.twintailcs.electoral.electoral.web.dto.mapper;

import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.PartidoResponse;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapeo entre la entidad {@link Partido} y el DTO {@link PartidoResponse}.
 *
 * <p>En el backend Spring esto lo hace MapStruct. Aca el mapper es a mano, como en el modulo de
 * auth: la forma del DTO es la misma que en Spring.
 */
@ApplicationScoped
public class PartidoMapper {

  /** DTO de un partido. */
  public PartidoResponse toDto(Partido entity) {
    if (entity == null) {
      return null;
    }
    return new PartidoResponse(
        entity.getId(), entity.getNombre(), entity.getSiglas(), entity.getNumeroLista());
  }
}
