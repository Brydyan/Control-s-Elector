package ec.twintailcs.electoral.acta.web.dto.mapper;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper entre {@link Acta} (entidad) y {@link ActaResponseDto} (DTO).
 * Bean registrado en MapperConfig en lugar de @Component auto (ver EvidenciaMapper).
 */
@Mapper
public interface ActaMapper {

    ActaResponseDto toDto(Acta entity);
}
