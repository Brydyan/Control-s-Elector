package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Configuracion inexistente → 404 NOT FOUND con el shape estandar de error.
 *
 * <p>Mismo status y mismo mensaje que el {@code GlobalExceptionHandler} del backend Spring.
 */
@Provider
public class ConfiguracionNotFoundExceptionMapper
    implements ExceptionMapper<ConfiguracionNotFoundException> {

  @Override
  public Response toResponse(ConfiguracionNotFoundException ex) {
    return Response.status(404)
        .type("application/json")
        .entity(ApiErrorResponse.body(404, "Not Found", ex.getMessage()))
        .build();
  }
}
