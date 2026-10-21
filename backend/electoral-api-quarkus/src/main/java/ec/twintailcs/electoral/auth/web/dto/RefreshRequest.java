package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Body para POST /auth/refresh y POST /auth/logout.
 *
 * <p>El refresh token viaja en la cookie httpOnly por seguridad; este body es el camino
 * alternativo para clientes que no usan cookies (por ejemplo la app movil). El endpoint prueba
 * primero la cookie y despues el body.
 */
// En native image Jackson necesita metadatos de reflexion para deserializar el
// record del body de la request.
@RegisterForReflection
public record RefreshRequest(String refreshToken) {}
