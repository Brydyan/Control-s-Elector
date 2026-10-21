package ec.twintailcs.electoral.auth.security;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/**
 * Filtro de autenticacion y autorizacion.
 *
 * <p>Equivalente funcional del {@code JwtAuthFilter} + {@code SecurityConfig} del backend Spring:
 * valida el access token, deja el usuario en el {@link CurrentUser} del request y evalua la
 * anotacion {@link Requiere} del endpoint (o de la clase) para decidir si deja pasar la request.
 *
 * <p>La validacion del token y la autorizacion se hacen con Nimbus, igual que en Spring, para que
 * los tokens de las dos implementaciones sean intercambiables. La autorizacion, en cambio, es
 * propia: evalua la anotacion {@link Requiere} en vez de usar SpEL, porque SpEL es de Spring y
 * no existe aca. El mapeo entre ambas sintaxis esta documentado en {@link Requiere}.
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

  /** Metodo y clase del endpoint que se esta sirviendo: de ahi sale la anotacion Requiere. */
  @Context ResourceInfo resourceInfo;

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
      return;
    }
    if (!autorizado(ctx)) {
      abort(ctx, 403, "Forbidden", "Access Denied");
    }
  }

  /**
   * Evalua la anotacion {@link Requiere} del endpoint. Si el metodo no la tiene, se usa la de la
   * clase: el metodo reemplaza al de la clase, igual que en Spring Security.
   */
  private boolean autorizado(ContainerRequestContext ctx) {
    Requiere requiere = null;
    if (resourceInfo != null) {
      Method metodo = resourceInfo.getResourceMethod();
      if (metodo != null) {
        requiere = metodo.getAnnotation(Requiere.class);
      }
      if (requiere == null) {
        Class<?> clase = resourceInfo.getResourceClass();
        if (clase != null) {
          requiere = clase.getAnnotation(Requiere.class);
        }
      }
    }
    if (requiere == null) {
      // Sin anotacion, el unico requisito es estar autenticado (ya se verifico arriba).
      return true;
    }
    Set<String> roles = currentUser.rolesEfectivos();
    boolean cumplePermisos =
        Arrays.stream(requiere.permisos()).anyMatch(permiso -> roles.contains(permiso));
    boolean cumpleRoles = Arrays.stream(requiere.roles()).anyMatch(role -> roles.contains(role));
    if (requiere.permisos().length == 0 && requiere.roles().length == 0) {
      return requiere.autenticado() || currentUser.email() != null;
    }
    // Entre los dos grupos el criterio es OR, como en el "or" de Spring.
    return cumplePermisos || cumpleRoles;
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
