package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.AuthException;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.LoginUseCase;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse;
import ec.twintailcs.electoral.auth.web.dto.LoginRequest;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoints de autenticacion. Mismo contrato que el {@code AuthController} del backend Spring.
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

  /** Nombre canonico de la cookie que transporta el refresh token. */
  public static final String REFRESH_COOKIE = "refresh_token";

  /** TTL de la cookie refresh en segundos; debe matchear refreshTtl. */
  public static final long REFRESH_COOKIE_MAX_AGE_SEC = 7 * 24 * 3600L;

  @Inject LoginUseCase loginUseCase;

  @Inject JwtService jwt;

  @Inject CurrentUser currentUser;

  @POST
  @Path("/login")
  public Response login(@Valid LoginRequest body, @HeaderParam(HttpHeaders.USER_AGENT) String userAgent) {
    JwtService.TokenPair pair = loginUseCase.execute(body.email(), body.password(), ipDe(), userAgent);
    Usuario usuario = Usuario.find("email", body.email().trim().toLowerCase()).firstResult();

    AuthResponse payload =
        new AuthResponse(
            pair.access(),
            "Bearer",
            pair.accessTtl().toSeconds(),
            pair.sesionId(),
            new AuthResponse.UsuarioActual(
                usuario.getPublicId(), usuario.getEmail(), usuario.getRolNombre()));

    return Response.ok()
        .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(pair.refresh()))
        .entity(payload)
        .build();
  }

  @GET
  @Path("/me")
  public AuthResponse.UsuarioActual me() {
    String email = currentUser.email();
    if (email == null) {
      throw new AuthException("No autenticado");
    }
    Usuario u = Usuario.find("email", email).firstResult();
    if (u == null) {
      throw new AuthException("No autenticado");
    }
    return new AuthResponse.UsuarioActual(u.getPublicId(), u.getEmail(), u.getRolNombre());
  }

  /**
   * Cookie httpOnly + Secure + SameSite=Strict. El header se arma a mano y no con
   * {@code NewCookie} para que sea byte a byte igual al del backend Spring
   * (JAX-RS no expone SameSite de forma uniforme entre implementaciones).
   */
  private static String buildRefreshCookie(String value) {
    return REFRESH_COOKIE
        + "="
        + value
        + "; HttpOnly; Secure; SameSite=Strict; Path=/"
        + "; Max-Age="
        + REFRESH_COOKIE_MAX_AGE_SEC;
  }

  /**
   * IP del cliente. El backend Spring toma {@code X-Forwarded-For} y si no, la direccion
   * remota del servlet; aca se usa el header que deja el proxy, con "sistema" como
   * fallback. Cuando el modulo este completo se engancha la direccion remota de Vert.x.
   */
  private static String ipDe() {
    String xForwardedFor = System.getProperty("x-forwarded-for", "sistema");
    return xForwardedFor.isBlank() ? "sistema" : xForwardedFor;
  }
}
