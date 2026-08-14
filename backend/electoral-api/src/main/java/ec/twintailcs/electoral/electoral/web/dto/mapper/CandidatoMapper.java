package ec.twintailcs.electoral.electoral.web.dto.mapper;

import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper entre {@link Candidato} (entidad) y {@link CandidatoResponse} (DTO).
 * cargoId/partidoId se resuelven desde las relaciones @ManyToOne.
 * Bean registrado en MapperConfig en lugar de @Component auto (ver EvidenciaMapper).
 */
@Mapper
public interface CandidatoMapper {

    @Mapping(target = "cargoId", source = "cargo.id")
    @Mapping(target = "partidoId", source = "partido.id")
    CandidatoResponse toDto(Candidato entity);
}
