package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.domain.UsuarioInvalidoException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Violacion de invariantes de usuario → 400 BAD REQUEST con el shape estandar de error.
 *
 * <p>Equivalente del handler de {@code UsuarioInvalidoException} del backend Spring.
 */
@Provider
public class UsuarioInvalidoExceptionMapper implements ExceptionMapper<UsuarioInvalidoException> {

  @Override
  public Response toResponse(UsuarioInvalidoException ex) {
    return Response.status(400)
        .type("application/json")
        .entity(ApiErrorResponse.body(400, "Bad Request", ex.getMessage()))
        .build();
  }
}
