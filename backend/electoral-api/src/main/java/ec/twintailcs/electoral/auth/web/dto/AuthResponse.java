package ec.twintailcs.electoral.auth.web.dto;

/**
 * Respuesta publica de /auth/login y /auth/refresh.
 *
 * <p>El access token tambien viaja en header Authorization (Bearer). El refresh token viaja SOLO en
 * cookie httpOnly (no se serializa aca desde login para que no quede en logs/proxies). En refresh,
 * va en el request (cookie o body, configurable).
 *
 * <p>Decisiones sobre que NO exponer al frontend: - Permisos atomicos (recurso:accion) NO viajan en
 * el body. Se mantienen server-side (claim "perms" del JWT + DB) y se usan para @PreAuthorize en
 * cada endpoint. Si el FE los necesita, debe llamar a un endpoint admin futuro (no implementado en
 * esta version). - En su lugar, el FE llama a GET /api/v1/menus/me para obtener el sidebar derivado
 * de los permisos del usuario.
 */
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Respuesta de autenticacion con access token y datos del usuario")
public record AuthResponse(
    @Schema(description = "JWT de acceso (15 min)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
    @Schema(description = "Tipo de token", example = "Bearer") String tokenType,
    @Schema(description = "TTL del access token en segundos", example = "900")
        long expiresInSeconds,
    @Schema(description = "Identificador opaco de la sesion activa", example = "a1b2c3d4...")
        String sesionId,
    @Schema(description = "Datos basicos del usuario autenticado") UsuarioActual usuario) {

  @Schema(description = "Identificacion minima del usuario autenticado")
  public record UsuarioActual(
      @Schema(description = "UUID publico del usuario (UUIDv7)") UUID id,
      @Schema(description = "Email institucional", example = "obs.juan.perez@control.ec")
          String email,
      @Schema(description = "Rol asignado", example = "OBSERVER") String rol) {}
}
