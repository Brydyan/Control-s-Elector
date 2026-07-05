package ec.twintailcs.electoral.auth.web.dto;

/**
 * Respuesta publica de /auth/login y /auth/refresh.
 *
 * El access token tambien viaja en header Authorization (Bearer).
 * El refresh token viaja SOLO en cookie httpOnly (no se serializa aca
 * desde login para que no quede en logs/proxies). En refresh, va en el
 * request (cookie o body, configurable).
 *
 * Decisiones sobre que NO exponer al frontend:
 *   - Permisos atomicos (recurso:accion) NO viajan en el body. Se
 *     mantienen server-side (claim "perms" del JWT + DB) y se usan para
 *     @PreAuthorize en cada endpoint. Si el FE los necesita, debe llamar
 *     a un endpoint admin futuro (no implementado en esta version).
 *   - En su lugar, el FE llama a GET /api/v1/menus/me para obtener el
 *     sidebar derivado de los permisos del usuario.
 */
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,        // "Bearer"
        long expiresInSeconds,   // TTL del access
        String sesionId,
        UsuarioActual usuario
) {
    public record UsuarioActual(
            UUID id,
            String email,
            String rol
    ) {}
}