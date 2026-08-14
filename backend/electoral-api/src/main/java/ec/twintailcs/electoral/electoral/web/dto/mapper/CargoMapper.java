package ec.twintailcs.electoral.electoral.web.dto.mapper;

import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.web.dto.CargoResponse;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper entre {@link Cargo} (entidad) y {@link CargoResponse} (DTO).
 * Bean registrado en MapperConfig en lugar de @Component auto (ver EvidenciaMapper).
 */
@Mapper
public interface CargoMapper {

    CargoResponse toDto(Cargo entity);
}
