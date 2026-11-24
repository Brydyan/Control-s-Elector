package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.UserInvitationService;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.auth.security.InvitationRateLimiter;
import ec.twintailcs.electoral.auth.web.dto.InviteUserRequest;
import ec.twintailcs.electoral.auth.web.dto.ResendInvitationResponse;
import ec.twintailcs.electoral.auth.web.dto.UserInvitationResponse;
import ec.twintailcs.electoral.common.web.ClientAddressResolver;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * Invitacion de una persona concreta.
 *
 * <p>La ruta es {@code /api/v1/users/invite}. El envio por lotes de cuentas que ya existen tiene su
 * propio endpoint ({@code /api/v1/auth/invitaciones}); este es el de "traer a alguien nuevo".
 *
 * <p><b>Matriz de roles.</b> Exige {@code usuario:create} o el rol {@code MASTER}. Un rol sin
 * permiso recibe 403. El limite de una invitacion cada 5 segundos por IP se cuenta DESPUES de la
 * autorizacion: a un llamador sin permiso se le responde 403 y no 429, que es lo que corresponde —
 * el estado del limitador no le dice nada a quien no puede invitar.
 */
@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UserInvitationResource {

  @Inject Autorizador autorizador;
  @Inject UserInvitationService invitationService;
  @Inject InvitationRateLimiter rateLimiter;
  @Inject ContextoSolicitud contexto;
  @Inject ClientAddressResolver clientAddress;

  /**
   * Invita a una persona: crea la cuenta y manda el email de activacion.
   *
   * @param pedido datos de la persona a invitar
   * @param xForwardedFor IP del cliente, puesta por el proxy
   * @param userAgent agente de usuario del cliente
   */
  @POST
  @Path("/invite")
  public Response invitar(
      @Valid InviteUserRequest pedido,
      @HeaderParam("X-Forwarded-For") String xForwardedFor,
      @HeaderParam("User-Agent") String userAgent) {

    autorizador.exigir("usuario:create", "MASTER");

    String ip = contexto.ip(xForwardedFor);
    // Solo con IP confiable: sobre la IP del proxy todos compartirian contador.
    clientAddress.trustedIp().ifPresent(rateLimiter::registrar);

    UserInvitationResponse response = invitationService.invitar(pedido, ip, userAgent);
    return Response.ok(response).build();
  }

  /**
   * Reenvia la invitacion de una cuenta que todavia no se activo (ODD #504, D y E).
   *
   * <p>El identificador es el id PUBLICO del usuario (UUID), no el interno: es el que viaja en la
   * tabla del panel y el que no dice nada del orden de la base.
   *
   * @param publicId id publico del usuario invitado
   * @param xForwardedFor IP del cliente, puesta por el proxy
   * @param userAgent agente de usuario del cliente
   */
  @POST
  @Path("/{publicId}/resend-invitation")
  public Response reenviar(
      @PathParam("publicId") UUID publicId,
      @HeaderParam("X-Forwarded-For") String xForwardedFor,
      @HeaderParam("User-Agent") String userAgent) {

    autorizador.exigir("usuario:create", "MASTER");

    String ip = contexto.ip(xForwardedFor);
    // Ventana propia: invitar y reenviar seguido es un flujo legitimo (se
    // acaba de crear la cuenta y hay que reenviarle el correo), y un limite
    // compartido lo dejaria trabado a mitad de camino.
    clientAddress.trustedIp().ifPresent(trusted -> rateLimiter.registrar("resend:" + trusted));

    ResendInvitationResponse response = invitationService.reenviar(publicId, ip, userAgent);
    return Response.ok(response).build();
  }
}
