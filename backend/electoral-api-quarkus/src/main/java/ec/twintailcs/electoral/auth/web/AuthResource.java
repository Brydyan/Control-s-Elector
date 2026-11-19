package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.AuthException;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.LoginUseCase;
import ec.twintailcs.electoral.auth.application.LogoutUseCase;
import ec.twintailcs.electoral.auth.application.PasswordChangeException;
import ec.twintailcs.electoral.auth.application.PasswordChangeService;
import ec.twintailcs.electoral.auth.application.RefreshTokenUseCase;
import ec.twintailcs.electoral.auth.application.RegistrarUsuarioUseCase;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.auth.security.PasswordChangeRateLimiter;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse;
import ec.twintailcs.electoral.auth.web.dto.ChangePasswordRequest;
import ec.twintailcs.electoral.auth.web.dto.LoginRequest;
import ec.twintailcs.electoral.auth.web.dto.RefreshRequest;
import ec.twintailcs.electoral.auth.web.dto.RegisterRequest;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import ec.twintailcs.electoral.common.web.RateLimited;
import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de autenticacion. Mismo contrato que el {@code AuthController} del backend Spring.
 *
 * <p>Convenciones:
 *
 * <ul>
 *   <li>access token: viaja solo en {@code Authorization: Bearer <token>} en cada request
 *       subsecuente.
 *   <li>refresh token: viaja en la cookie httpOnly {@code refresh_token} para que no sea accesible
 *       desde JS. Tambien puede venir en el body para clientes no-browser (app movil). Misma logica
 *       para ambos: se prueba primero la cookie y despues el body.
 * </ul>
 */
@ApplicationScoped
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

  /** Nombre canonico de la cookie que transporta el refresh token. */
  public static final String REFRESH_COOKIE = "refresh_token";

  /** TTL de la cookie refresh en segundos; debe matchear refreshTtl. */
  public static final long REFRESH_COOKIE_MAX_AGE_SEC = 7 * 24 * 3600L;

  @Inject LoginUseCase loginUseCase;

  @Inject RefreshTokenUseCase refreshTokenUseCase;

  @Inject LogoutUseCase logoutUseCase;

  @Inject RegistrarUsuarioUseCase registrarUsuarioUseCase;

  @Inject PasswordChangeService passwordChangeService;

  @Inject PasswordChangeRateLimiter rateLimiter;

  @Inject JwtService jwt;

  @Inject CurrentUser currentUser;

  @Inject Autorizador autorizador;

  @Inject UsuarioMapper mapper;

  @RateLimited(limit = 5, windowSeconds = 300, identifier = "ip", endpoint = "login")
  @POST
  @Path("/login")
  public Response login(
      @Valid LoginRequest body, @HeaderParam(HttpHeaders.USER_AGENT) String userAgent) {
    JwtService.TokenPair pair =
        loginUseCase.execute(body.email(), body.password(), ipDe(), userAgent);
    Usuario usuario = Usuario.find("email", body.email().trim().toLowerCase()).firstResult();

    AuthResponse payload =
        new AuthResponse(
            pair.access(),
            "Bearer",
            pair.accessTtl().toSeconds(),
            pair.sesionId(),
            mapper.toDto(usuario));
    return Response.ok()
        .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(pair.refresh()))
        .entity(payload)
        .build();
  }

  @POST
  @Path("/refresh")
  public Response refresh(
      RefreshRequest body, @CookieParam(REFRESH_COOKIE) String refreshEnCookie) {
    String refresh = readRefresh(refreshEnCookie, body);
    JwtService.TokenPair pair = refreshTokenUseCase.execute(refresh);

    // Necesitamos los datos del usuario para construir la respuesta.
    UUID publicId = UUID.fromString(jwt.parseAccess(pair.access()).subject());
    Usuario u = Usuario.find("publicId", publicId).firstResult();

    AuthResponse payload =
        new AuthResponse(
            pair.access(),
            "Bearer",
            pair.accessTtl().toSeconds(),
            pair.sesionId(),
            mapper.toDto(u));
    return Response.ok()
        .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(pair.refresh()))
        .entity(payload)
        .build();
  }

  @POST
  @Path("/logout")
  public Response logout(RefreshRequest body, @CookieParam(REFRESH_COOKIE) String refreshEnCookie) {
    String refresh = readRefresh(refreshEnCookie, body);
    logoutUseCase.execute(refresh);
    // Borrar la cookie (Max-Age=0)
    return Response.noContent()
        .header(
            HttpHeaders.SET_COOKIE,
            REFRESH_COOKIE + "=; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=0")
        .build();
  }

  /** Informacion del usuario actual (parseado del access token del request). */
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
    return mapper.toDto(u);
  }

  /**
   * Solo MASTER (o el permiso {@code usuario:create}) puede crear usuarios. Para esta version es
   * asi: el FE tiene un panel admin que lista usuarios. Cuando existan auto-registros via invite,
   * se cambia esta firma.
   *
   * <p>El email se genera automaticamente a partir de nombre, apellido y rol.
   *
   * <p>El requisito de permisos vive en el caso de uso ({@code RegistrarUsuarioUseCase}), que es
   * donde el interceptor CDI que evalua {@code @Requiere} puede correr: Quarkus REST invoca los
   * metodos del recurso directamente y no les aplica interceptores.
   */
  @POST
  @Path("/register")
  public Response register(@Valid RegisterRequest body) {
    autorizador.exigir("usuario:create", "MASTER");
    Usuario u =
        registrarUsuarioUseCase.execute(
            body.nombre(), body.apellido(), body.password(), body.rol());
    return Response.status(201).entity(mapper.toDto(u)).build();
  }

  /**
   * Cambiar contraseña del usuario actual.
   *
   * <p>Requiere JWT válido. Rate limited a 1 cambio por hora por usuario+IP (design D3).
   * Validaciones:
   * - Contraseña actual debe ser correcta
   * - Nueva contraseña debe cumplir requisitos (8+ chars, upper, lower, number, special)
   * - Nueva no puede ser igual a la anterior
   * - Ambas nuevas deben coincidir
   */
  @POST
  @Path("/change-password")
  public Response changePassword(@Valid ChangePasswordRequest body) {
    String email = currentUser.email();
    if (email == null) {
      throw new AuthException("No autenticado");
    }

    Usuario usuario = Usuario.find("email", email).firstResult();
    if (usuario == null) {
      throw new AuthException("No autenticado");
    }

    // Rate limit: 1 cambio por hora
    rateLimiter.registerAttempt(usuario.getId(), ipDe());

    // Realizar cambio de contraseña
    try {
      passwordChangeService.changePassword(
          usuario.getPublicId(),
          body.currentPassword(),
          body.newPassword(),
          body.passwordConfirm());
    } catch (PasswordChangeException e) {
      return Response.status(e.getStatusCode())
          .entity(Map.of("error", e.getMessage(), "code", e.getErrorCode()))
          .build();
    }

    return Response.ok(Map.of("message", "Password updated successfully")).build();
  }

  // --------- helpers ---------

  /**
   * Lee el refresh token de la cookie o del body. La cookie tiene prioridad: es el camino que usan
   * los navegadores.
   */
  private static String readRefresh(String refreshEnCookie, RefreshRequest body) {
    if (refreshEnCookie != null && !refreshEnCookie.isBlank()) {
      return refreshEnCookie;
    }
    if (body != null && body.refreshToken() != null) {
      return body.refreshToken();
    }
    throw new AuthException("No se encontro refresh token");
  }

  /**
   * Cookie httpOnly + Secure + SameSite=Strict. El header se arma a mano y no con {@code NewCookie}
   * para que sea byte a byte igual al del backend Spring (JAX-RS no expone SameSite de forma
   * uniforme entre implementaciones).
   */
  public static String buildRefreshCookie(String value) {
    return REFRESH_COOKIE
        + "="
        + value
        + "; HttpOnly; Secure; SameSite=Strict; Path=/"
        + "; Max-Age="
        + REFRESH_COOKIE_MAX_AGE_SEC;
  }

  /**
   * IP del cliente. El backend Spring toma {@code X-Forwarded-For} y si no, la direccion remota del
   * servlet; aca se usa el header que deja el proxy, con "sistema" como fallback. Cuando el modulo
   * este completo se engancha la direccion remota de Vert.x.
   */
  private static String ipDe() {
    String xForwardedFor = System.getProperty("x-forwarded-for", "sistema");
    return xForwardedFor.isBlank() ? "sistema" : xForwardedFor;
  }
}
