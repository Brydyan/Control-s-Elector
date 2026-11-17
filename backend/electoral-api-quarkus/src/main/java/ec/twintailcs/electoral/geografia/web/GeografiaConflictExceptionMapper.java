package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Conflicto de geografia → 409 CONFLICT con el shape estandar de error.
 *
 * <p>Los datos extra del conflicto (por ejemplo cuantos hijos bloquean el borrado) van en el bloque
 * {@code fields}, que es parte del shape estandar que exige el contrato.
 */
@Provider
public class GeografiaConflictExceptionMapper
    implements ExceptionMapper<GeografiaConflictException> {

  @Override
  public Response toResponse(GeografiaConflictException ex) {
    return Response.status(409)
        .type("application/json")
        .entity(ApiErrorResponse.body(409, "Conflict", ex.getMessage(), ex.getCampos()))
        .build();
  }
}
