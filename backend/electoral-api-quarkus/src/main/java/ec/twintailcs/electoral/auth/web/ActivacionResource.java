package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.ActivarUsuarioPorTokenUseCase;
import ec.twintailcs.electoral.auth.security.ActivationRateLimiter;
import ec.twintailcs.electoral.auth.web.dto.ActivarUsuarioResponse;
import ec.twintailcs.electoral.auth.web.dto.ActivateUserRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * Activacion de cuenta por token.
 *
 * <p>Publico: es el endpoint al que llega el usuario que acaba de recibir el email y todavia no
 * tiene sesion. Por eso esta limitado por IP ({@link ActivationRateLimiter}): sin ese limite, el
 * endpoint es un oracle para adivinar tokens.
 */
@Path("/api/v1/auth/activate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ActivacionResource {

  private static final Logger log = Logger.getLogger(ActivacionResource.class);

  @Inject ActivarUsuarioPorTokenUseCase activacionUseCase;
  @Inject ActivationRateLimiter rateLimiter;

  /** Activa la cuenta con el token recibido y la contrasena elegida. */
  @POST
  public Response activar(@Valid ActivateUserRequest request, @Context HttpHeaders headers) {
    rateLimiter.registrarIntento(ipCliente(headers));

    ActivarUsuarioResponse response =
        activacionUseCase.execute(request.token(), request.password(), request.passwordConfirm());
    return Response.ok(response).build();
  }

  /** Direccion del cliente, con el header del proxy como fuente principal. */
  private String ipCliente(HttpHeaders headers) {
    if (headers == null) {
      return "desconocida";
    }
    String reenviada = headers.getHeaderString("X-Forwarded-For");
    if (reenviada != null && !reenviada.isBlank()) {
      return reenviada.split(",")[0].trim();
    }
    return "desconocida";
  }
}
