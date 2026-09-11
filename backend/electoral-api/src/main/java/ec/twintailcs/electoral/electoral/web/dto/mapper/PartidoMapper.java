package ec.twintailcs.electoral.electoral.web.dto.mapper;

import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.PartidoResponse;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper entre {@link Partido} (entidad) y {@link PartidoResponse} (DTO). Bean registrado
 * en MapperConfig en lugar de @Component auto (ver EvidenciaMapper).
 */
@Mapper
public interface PartidoMapper {

  PartidoResponse toDto(Partido entity);
}
