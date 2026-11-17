package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.domain.RecursoNoEncontradoException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Recurso inexistente → 404 NOT FOUND con el shape estandar de error.
 *
 * <p>El mensaje es el que pasa el llamador ("Usuario no encontrado", "Sesion no encontrada"), como
 * en el backend Spring.
 */
@Provider
public class RecursoNoEncontradoExceptionMapper
    implements ExceptionMapper<RecursoNoEncontradoException> {

  @Override
  public Response toResponse(RecursoNoEncontradoException ex) {
    return Response.status(404)
        .type("application/json")
        .entity(ApiErrorResponse.body(404, "Not Found", ex.getMessage()))
        .build();
  }
}
