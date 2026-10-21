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
 * Filtro de autenticacion.
 *
 * <p>Equivalente funcional del {@code JwtAuthFilter} del backend Spring: valida el access token
 * y deja el usuario en el {@link CurrentUser} del request. Lo que no hace es autorizar: eso lo
 * hace el {@link RequiereInterceptor}, que corre despues de la validacion del body para poder
 * reproducir el orden de Spring (400 de validacion antes que 403 de autorizacion).
 *
 * <p>La validacion del token se hace con Nimbus, igual que en Spring, para que los tokens de las
 * dos implementaciones sean intercambiables.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class JwtAuthFilter implements ContainerRequestFilter {

  /** Rutas que no exigen token. Misma lista que el permitAll del backend Spring. */
  private static final String[] PUBLICAS = {
    "/actuator/health",
    "/actuator/info",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    "/v3/api-docs",
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
    boolean publica = esPublica(path);

    String token = bearer(ctx.getHeaderString(HttpHeaders.AUTHORIZATION));
    if (token != null) {
      try {
        JwtService.ParsedJwt parsed = jwt.parseAccess(token);
        currentUser.set(parsed.email(), parsed.rol(), parsed.perms(), parsed.sesionId());
      } catch (RuntimeException e) {
        if (!publica) {
          abort(ctx, 401, "Unauthorized", "No autenticado o token invalido");
          return;
        }
      }
    } else if (!publica) {
      abort(ctx, 401, "Unauthorized", "No autenticado o token invalido");
      return;
    }

    if (publica) {
      return;
    }
    if (currentUser.email() == null) {
      abort(ctx, 401, "Unauthorized", "No autenticado o token invalido");
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

  private static void abort(ContainerRequestContext ctx, int status, String reason, String message) {
    ctx.abortWith(
        Response.status(status)
            .type("application/json")
            .entity(ApiErrorResponse.body(status, reason, message))
            .build());
  }
}
