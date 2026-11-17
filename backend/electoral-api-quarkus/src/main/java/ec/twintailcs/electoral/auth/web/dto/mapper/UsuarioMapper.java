package ec.twintailcs.electoral.auth.web.dto.mapper;

import ec.twintailcs.electoral.auth.domain.EstadoInvitacion;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.UserInvitation;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse;
import ec.twintailcs.electoral.auth.web.dto.SesionResponseDto;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;

/**
 * Mapeo entre las entidades Panache y los DTOs que cruzan el cable.
 *
 * <p>En el backend Spring esto lo hace MapStruct. Aca el mapper es a mano: el modulo de Quarkus no
 * trae el processor de MapStruct y agregar una dependencia de build solo para tres metodos no vale
 * la pena. La forma de los DTO es la misma que en Spring.
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

  /**
   * Datos publicos de un usuario para el panel de administracion.
   *
   * <p>Incluye el estado de activacion y la fecha del ultimo envio de invitacion: el panel los usa
   * para decidir a quien se puede invitar y a quien hay que reenviarle el link.
   */
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
        entity.getCreadoEn(),
        null,
        entity.getEstadoUsuario(),
        entity.getInvitationSentAt(),
        estadoInvitacion(entity),
        vencimientoInvitacion(entity));
  }

  /**
   * Estado de la invitacion de una cuenta (ODD #504, C).
   *
   * <p>Se deriva de las columnas que ya tiene el usuario y no de la tabla de invitaciones: leer esa
   * tabla en el listado exigiria abrirle la RLS a quien solo puede leer los usuarios, y el dato que
   * hace falta ya esta en {@code usuario}.
   *
   * <p>El orden importa:
   *
   * <ol>
   *   <li>Una cuenta activa no espera invitacion.
   *   <li>Una cuenta deshabilitada se muestra como tal: ofrecerle "reenviar invitacion" seria
   *       invitar a alguien que no puede entrar.
   *   <li>Una invitacion que ya vencio se muestra vencida.
   *   <li>Una cuenta a la que nunca se le invito esta CREATED: existe pero no hay nada que
   *       reenviar, todavia no.
   * </ol>
   */
  private String estadoInvitacion(Usuario entity) {
    if (entity.estaActivo()) {
      return EstadoInvitacion.ACTIVE.valor();
    }
    if (!entity.isHabilitado()) {
      return EstadoInvitacion.DISABLED.valor();
    }
    if (entity.getInvitationSentAt() == null) {
      return EstadoInvitacion.CREATED.valor();
    }
    Instant vencimiento = vencimientoInvitacion(entity);
    if (vencimiento != null && Instant.now().isAfter(vencimiento)) {
      return EstadoInvitacion.EXPIRED.valor();
    }
    return EstadoInvitacion.INVITED.valor();
  }

  /**
   * Momento en que vence la invitacion vigente, si la hay.
   *
   * <p>Es el ultimo envio mas la ventana de la invitacion (24 horas): el mismo plazo que aplica el
   * token de activacion.
   */
  private Instant vencimientoInvitacion(Usuario entity) {
    if (entity.getInvitationSentAt() == null) {
      return null;
    }
    return entity.getInvitationSentAt().plus(Duration.ofHours(UserInvitation.HORAS_VIGENCIA));
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
