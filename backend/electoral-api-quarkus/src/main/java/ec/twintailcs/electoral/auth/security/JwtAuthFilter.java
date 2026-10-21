package ec.twintailcs.electoral.auth.security;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Filtro de autenticacion: valida el access token y deja el usuario en el
 * {@link CurrentUser} del request.
 *
 * <p>Es el equivalente funcional del {@code JwtAuthFilter} + {@code SecurityConfig} del backend
 * Spring, con la misma lista de rutas publicas y el mismo 401.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtAuthFilter implements ContainerRequestFilter {

  /** Rutas que no exigen token. Misma lista que el permitAll del backend Spring. */
  private static final String[] PUBLICAS = {
    "/actuator/health",
    "/actuator/info",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    "/v3/api-docs",
    "/v3/api-docs/",
    "/swagger-ui",
    "/swagger-ui.html",
    "/webjars",
    "/q"
  };

  @Inject JwtService jwt;

  @Inject CurrentUser currentUser;

  @Override
  public void filter(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath();
    if (esPublica(path)) {
      return;
    }
    String token = bearer(ctx.getHeaderString(HttpHeaders.AUTHORIZATION));
    if (token == null) {
      abort(ctx);
      return;
    }
    try {
      JwtService.ParsedJwt parsed = jwt.parseAccess(token);
      currentUser.set(parsed.email(), parsed.rol(), parsed.perms(), parsed.sesionId());
    } catch (RuntimeException e) {
      abort(ctx);
    }
  }

  private static boolean esPublica(String path) {
    if (path == null) {
      return false;
    }
    for (String publica : PUBLICAS) {
      if (path.equals(publica) || path.startsWith(publica + "/")) {
        return true;
      }
    }
    return false;
  }

  private static String bearer(String header) {
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return null;
    }
    String token = header.substring(7).trim();
    return token.isEmpty() ? null : token;
  }

  private static void abort(ContainerRequestContext ctx) {
    ctx.abortWith(
        Response.status(401)
            .type("application/json")
            .entity(
                ApiErrorResponse.body(
                    401, "Unauthorized", "No autenticado o token invalido"))
            .build());
  }
}
