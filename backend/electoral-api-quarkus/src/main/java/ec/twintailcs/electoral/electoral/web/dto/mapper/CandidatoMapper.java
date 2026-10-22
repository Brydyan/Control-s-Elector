package ec.twintailcs.electoral.electoral.web.dto.mapper;

import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoResponse;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapeo entre la entidad {@link Candidato} y el DTO {@link CandidatoResponse}.
 *
 * <p>En el backend Spring esto lo hace MapStruct, que resuelve {@code cargoId} y {@code partidoId}
 * desde las relaciones {@code @ManyToOne}. Aca el mapper es a mano, como en el modulo de auth: la
 * forma del DTO es la misma que en Spring.
 */
@ApplicationScoped
public class CandidatoMapper {

  /** DTO de un candidato. */
  public CandidatoResponse toDto(Candidato entity) {
    if (entity == null) {
      return null;
    }
    return new CandidatoResponse(
        entity.getId(),
        entity.getNombre(),
        entity.getOrden(),
        entity.getCargo().getId(),
        entity.getPartido().getId());
  }
}
