package ec.twintailcs.electoral.auth.interfaces.dto;

import java.util.List;

/**
 * Respuesta publica de /auth/login y /auth/refresh.
 *
 * El access token tambien viaja en header Authorization (Bearer).
 * El refresh token viaja SOLO en cookie httpOnly (no se serializa aca
 * desde login para que no quede en logs/proxies). En refresh, va en el
 * request (cookie o body, configurable).
 */
public record AuthResponse(
        String accessToken,
        String tokenType,        // "Bearer"
        long expiresInSeconds,   // TTL del access
        String sesionId,
        UsuarioActual usuario
) {
    public record UsuarioActual(
            Long id,
            String email,
            String rol,
            List<String> permisos
    ) {}
}
