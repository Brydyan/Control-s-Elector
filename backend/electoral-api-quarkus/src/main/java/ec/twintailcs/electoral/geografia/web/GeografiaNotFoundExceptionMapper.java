package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Geografia inexistente → 404 NOT_FOUND con el shape estandar de error. */
@Provider
public class GeografiaNotFoundExceptionMapper
    implements ExceptionMapper<GeografiaNotFoundException> {

  @Override
  public Response toResponse(GeografiaNotFoundException ex) {
    return Response.status(404)
        .type("application/json")
        .entity(ApiErrorResponse.body(404, "Not Found", ex.getMessage()))
        .build();
  }
}
