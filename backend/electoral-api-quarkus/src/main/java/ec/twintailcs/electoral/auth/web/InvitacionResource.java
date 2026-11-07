package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.InvitacionService;
import ec.twintailcs.electoral.auth.application.InvitationTokenService;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.auth.web.dto.EnviarInvitacionResponse;
import ec.twintailcs.electoral.auth.web.dto.SendInvitacionesRequest;
import ec.twintailcs.electoral.auth.web.dto.ValidateTokenResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Invitaciones de usuario por email.
 *
 * <p>El envio exige MASTER (o el permiso {@code usuario:create}), igual que el alta de usuarios del
 * panel. No es una operacion de ADMIN: ADMIN no escribe en este dominio, y crear o invitar cuentas
 * es una escritura.
 *
 * <p>La validacion del token es publica porque la usa la pantalla de activacion antes de que el
 * usuario tenga sesion. No dice si el usuario existe: eso permitiria enumerar cuentas.
 */
@Path("/api/v1/auth/invitaciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class InvitacionResource {

  @Inject Autorizador autorizador;
  @Inject InvitacionService invitacionService;
  @Inject InvitationTokenService tokenService;

  /** Envia invitaciones a varios usuarios. Responde 202: los emails salen despues. */
  @POST
  public Response enviar(@Valid SendInvitacionesRequest request) {
    autorizador.exigir("usuario:create", "MASTER");

    EnviarInvitacionResponse response = invitacionService.enviarInvitaciones(request.usuarioIds());
    return Response.accepted(response).build();
  }

  /**
   * Dice si un token de activacion sirve, sin revelar si el usuario existe.
   *
   * <p>Publico a proposito: lo consulta la pantalla de activacion, que todavia no tiene sesion.
   */
  @GET
  @Path("/validate-token/{token}")
  public ValidateTokenResponse validarToken(@PathParam("token") String token) {
    try {
      // Lo que llega por la URL es el token envuelto; se deshace para validar el JWT.
      String jwt = tokenService.desenvolverDeLink(token);
      var publicId = tokenService.validateToken(jwt);
      return new ValidateTokenResponse(true, publicId.toString(), tokenService.expiresAt(jwt));
    } catch (RuntimeException e) {
      return ValidateTokenResponse.invalido();
    }
  }
}
