package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.domain.InvitacionException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Error del flujo de invitacion → status que trae la excepcion, con el shape estandar de error.
 *
 * <p>El status no se decide en el mapper: lo decide quien detecta el problema, que es el unico que
 * sabe si es un pedido invalido (400), un conflicto de estado (409) o un limite de intentos (429).
 */
@Provider
public class InvitacionExceptionMapper implements ExceptionMapper<InvitacionException> {

  @Override
  public Response toResponse(InvitacionException ex) {
    String razon =
        switch (ex.status()) {
          case 400 -> "Bad Request";
          case 409 -> "Conflict";
          case 429 -> "Too Many Requests";
          default -> "Error";
        };
    return Response.status(ex.status())
        .type("application/json")
        .entity(ApiErrorResponse.body(ex.status(), razon, ex.getMessage()))
        .build();
  }
}
