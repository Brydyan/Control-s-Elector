package ec.twintailcs.electoral.legal.web;

import ec.twintailcs.electoral.auth.application.AuthException;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.common.web.RateLimited;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.legal.application.LegalService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Acciones legales del usuario autenticado: aceptar terminos y exportar sus datos.
 *
 * <p>Las dos rutas cuelgan de la sesion: cada una opera <b>solo</b> sobre los datos del usuario del
 * token. No hay parametro de usuario en ninguna, asi que no hay forma de pedir los datos de otro
 * (spec de export, escenario 3: un {@code ?userId=} ajeno se ignora porque el parametro no existe).
 */
@Path("/api/v1/user")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UserLegalResource {

  @Inject LegalService legalService;

  @Inject CurrentUser currentUser;

  @Inject ContextoSolicitud contexto;

  /** Version vigente de los terminos. Cambiarla obliga a re-aceptar. */
  @ConfigProperty(name = "legal.terms-version", defaultValue = "1.0")
  String versionTerminos;

  /**
   * Registra la aceptacion de los terminos y de la politica de privacidad.
   *
   * <p>El version que se acepta es el que dice el servidor ({@code legal.terms-version}), no el que
   * manda el cliente: si el cliente eligiera la version, podria aceptar "1.0" mientras el servidor
   * ya publico "1.1", y quedaria accepting un documento que no es el vigente.
   */
  @POST
  @Path("/accept-terms")
  @Transactional
  public Response aceptarTerminos(
      @HeaderParam("X-Forwarded-For") String xForwardedFor,
      @HeaderParam("User-Agent") String userAgent,
      @HeaderParam("Accept-Language") String acceptLanguage) {

    Usuario usuario = usuarioActual();

    legalService.aceptarTerminos(
        usuario,
        contexto.ip(xForwardedFor),
        userAgent,
        acceptLanguage,
        versionTerminos,
        versionTerminos);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "accepted");
    body.put("version", versionTerminos);
    body.put("accepted_at", usuario.getAcceptedTermsAt());
    return Response.ok(body).build();
  }

  /**
   * Exporta los datos personales del usuario (GDPR Art. 15).
   *
   * <p>El limite de una exportacion cada 24 horas sale de la tabla {@code user_export_log}: asi
   * sobrevive a un reinicio del backend, que es justo cuando un contador en memoria dejaria pasar
   * una exportacion de mas.
   */
  @RateLimited(limit = 1, windowSeconds = 86400, identifier = "user", endpoint = "export-data")
  @GET
  @Path("/export-data")
  @Transactional
  public Response exportarDatos(@HeaderParam("X-Forwarded-For") String xForwardedFor) {

    Usuario usuario = usuarioActual();
    legalService.exigirVentanaExport(usuario.getId());

    Map<String, Object> datos = legalService.exportarDatos(usuario);

    String exportId = String.valueOf(datos.get("export_request_id"));
    int recordCount =
        contar(datos.get("actas_registradas")) + contar(datos.get("dispute_submissions"));
    legalService.registrarExport(
        usuario.getId(), exportId, contexto.ip(xForwardedFor), recordCount);

    return Response.ok(datos).build();
  }

  /**
   * Usuario del token.
   *
   * <p>Si el token es valido pero el usuario no existe en la base, es un token de una cuenta
   * borrada: se responde 401 en vez de 500, porque el problema es de credencial y no del servidor.
   */
  private Usuario usuarioActual() {
    Usuario usuario = Usuario.find("email", currentUser.email()).firstResult();
    if (usuario == null) {
      throw new AuthException("Usuario no existe");
    }
    return usuario;
  }

  @SuppressWarnings("unchecked")
  private static int contar(Object lista) {
    return lista instanceof List<?> items ? items.size() : 0;
  }
}
