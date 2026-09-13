package ec.twintailcs.electoral.auth.web.dto.mapper;

import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse.UsuarioActual;
import ec.twintailcs.electoral.auth.web.dto.SesionResponseDto;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
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

  @Mapping(target = "rolNombre", source = "rol.nombre")
  @Mapping(target = "passwordTemporal", ignore = true)
  UsuarioResponseDto toAdminDto(Usuario entity);

  default SesionResponseDto toSesionDto(Sesion entity, String usuarioEmail) {
    if (entity == null) {
      return null;
    }
    return new SesionResponseDto(
        entity.getId(),
        entity.getSesionId(),
        entity.getUsuarioId(),
        usuarioEmail,
        entity.getIpCliente(),
        entity.getUserAgent(),
        entity.getCreadoEn(),
        entity.getUltimoUsoEn(),
        entity.getExpiraEn());
  }
}
