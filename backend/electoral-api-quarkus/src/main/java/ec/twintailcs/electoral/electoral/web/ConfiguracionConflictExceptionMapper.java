package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.electoral.domain.ConfiguracionConflictException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Conflicto de configuracion → 409 CONFLICT con el shape estandar de error.
 *
 * <p>Mismo status y mismo mensaje que el {@code GlobalExceptionHandler} del backend Spring.
 */
@Provider
public class ConfiguracionConflictExceptionMapper
    implements ExceptionMapper<ConfiguracionConflictException> {

  @Override
  public Response toResponse(ConfiguracionConflictException ex) {
    return Response.status(409)
        .type("application/json")
        .entity(ApiErrorResponse.body(409, "Conflict", ex.getMessage()))
        .build();
  }
}
