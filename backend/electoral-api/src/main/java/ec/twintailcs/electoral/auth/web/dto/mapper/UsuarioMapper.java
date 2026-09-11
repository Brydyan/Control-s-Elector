package ec.twintailcs.electoral.auth.web.dto.mapper;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse.UsuarioActual;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper entre {@link Usuario} (entidad) y {@link UsuarioActual} (DTO). Resuelve la
 * relacion {@code usuario.rol → rol.nombre} para que el DTO reciba el nombre del rol como String en
 * lugar de la entidad completa. Bean registrado en MapperConfig en lugar de @Component auto.
 */
@Mapper
public interface UsuarioMapper {

  @Mapping(target = "id", source = "publicId")
  @Mapping(target = "rol", source = "rol.nombre")
  UsuarioActual toDto(Usuario entity);
}
