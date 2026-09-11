package ec.twintailcs.electoral.evidencia.web.dto.mapper;

import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper entre {@link Evidencia} (entidad) y {@link EvidenciaResponse} (DTO). El unico
 * mapeo no-trivial es {@code publicId → id} ya que el resto de campos (jrvId, cargoId, usuarioId,
 * s3Key, sizeBytes, contentType, estado, etc.) machean exactamente por nombre entre la entidad y el
 * record. Bean registrado en MapperConfig en lugar de @Component auto.
 */
@Mapper
public interface EvidenciaMapper {

  @Mapping(target = "id", source = "publicId")
  EvidenciaResponse toDto(Evidencia entity);
}
