package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.CancelDeletionUseCase;
import ec.twintailcs.electoral.auth.application.RequestDeletionUseCase;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.auth.web.dto.RequestDeletionRequest;
import ec.twintailcs.electoral.auth.web.dto.RequestDeletionResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * Endpoints para eliminación de cuenta (T-COMPLIANCE-04).
 *
 * <p>Implementa:
 *
 * <ul>
 *   <li>POST /api/v1/user/request-deletion — solicita eliminación con soft-delete (30d grace)
 *   <li>POST /api/v1/auth/cancel-deletion/{token} — cancela dentro de 7d
 * </ul>
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class DeleteAccountResource {

  private static final Logger log = Logger.getLogger(DeleteAccountResource.class);

  @Inject CurrentUser currentUser;
  @Inject RequestDeletionUseCase requestDeletionUseCase;
  @Inject CancelDeletionUseCase cancelDeletionUseCase;

  /**
   * Solicita eliminación de cuenta (soft-delete con 30 días grace period).
   *
   * <p>Requiere autenticación (JWT). El usuario debe confirmar su contraseña. Enviamos email con
   * link de recuperación válido 7 días.
   *
   * <p>Responde 200 con:
   *
   * <pre>
   * {
   *   "recoveryDeadline": "2026-12-05T10:00:00Z",
   *   "hardDeleteDeadline": "2027-01-04T10:00:00Z"
   * }
   * </pre>
   *
   * @param request debe contener password en claro del usuario
   * @return respuesta con deadlines de recuperación (7d) y hard-delete (30d)
   * @throws AuthException si la contraseña no coincide
   */
  @POST
  @Path("/user/request-deletion")
  public Response requestDeletion(@Valid RequestDeletionRequest request) {
    // CurrentUser está inyectado y contiene el usuario autenticado
    // Extrae el usuarioId del JWT (pero necesitamos acceso a CurrentUser.userId())
    // Por ahora pasamos el email y buscamos el usuario
    log.info("Delete request from user: " + currentUser.email());

    // En producción, CurrentUser debería exponer el usuarioId interno
    // Por ahora haremos un lookup por email
    ec.twintailcs.electoral.auth.domain.Usuario usuario =
        ec.twintailcs.electoral.auth.domain.Usuario.find("email", currentUser.email())
            .firstResult();

    if (usuario == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    RequestDeletionResponse response =
        requestDeletionUseCase.execute(usuario.getId(), request.getPassword());
    return Response.ok(response).build();
  }

  /**
   * Cancela eliminación de cuenta (dentro de 7 días desde soft-delete).
   *
   * <p>Público: el usuario que aún no ha iniciado sesión puede cancelar clickeando el link en el
   * email. El token es de recuperación, válido máximo 7 días.
   *
   * <p>Responde 204 No Content si la cancelación fue exitosa.
   *
   * @param token token de recuperación del email
   * @return 204 No Content si éxito, 400 si inválido, 409 si expiró
   */
  @POST
  @Path("/auth/cancel-deletion/{token}")
  public Response cancelDeletion(@PathParam("token") String token) {
    log.info(
        "Cancel deletion request with token: "
            + token.substring(0, Math.min(10, token.length()))
            + "...");

    cancelDeletionUseCase.execute(token);
    return Response.noContent().build();
  }
}
