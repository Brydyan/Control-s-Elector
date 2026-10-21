package ec.twintailcs.electoral.auth.web.dto.mapper;

import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse;
import ec.twintailcs.electoral.auth.web.dto.SesionResponseDto;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapeo entre las entidades Panache y los DTOs que cruzan el cable.
 *
 * <p>En el backend Spring esto lo hace MapStruct. Aca el mapper es a mano: el modulo de Quarkus
 * no trae el processor de MapStruct y agregar una dependencia de build solo para tres metodos no
 * vale la pena. La forma de los DTO es la misma que en Spring.
 *
 * <p>Los permisos atomicos NO viajan en el body: quedan en el claim {@code perms} del JWT.
 */
@ApplicationScoped
public class UsuarioMapper {

  /** Datos minimos del usuario autenticado. */
  public AuthResponse.UsuarioActual toDto(Usuario entity) {
    if (entity == null) {
      return null;
    }
    return new AuthResponse.UsuarioActual(
        entity.getPublicId(), entity.getEmail(), entity.getRolNombre());
  }

  /** Datos publicos de un usuario para el panel de administracion. */
  public UsuarioResponseDto toAdminDto(Usuario entity) {
    if (entity == null) {
      return null;
    }
    return new UsuarioResponseDto(
        entity.getPublicId(),
        entity.getEmail(),
        entity.getNombre(),
        entity.getApellido(),
        entity.getRolNombre(),
        entity.isHabilitado(),
        entity.getCreadoEn());
  }

  /** Detalle de una sesion, con el email del usuario propietario. */
  public SesionResponseDto toSesionDto(Sesion entity, String usuarioEmail) {
    if (entity == null) {
      return null;
    }
    return new SesionResponseDto(
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
