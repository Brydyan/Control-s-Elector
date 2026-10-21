package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Error de validacion del dominio de actas → 400 BAD REQUEST.
 *
 * <p>Contrato explicito del backend Spring: acta inexistente, votos que no cuadran, JRV/cargo/candidato
 * no encontrados y candidato que no pertenece al cargo llegan al cliente como 400, no como 500.
 */
@Provider
public class ActaInvalidaExceptionMapper implements ExceptionMapper<ActaInvalidaException> {

  @Override
  public Response toResponse(ActaInvalidaException ex) {
    return Response.status(400)
        .type("application/json")
        .entity(ApiErrorResponse.body(400, "Bad Request", ex.getMessage()))
        .build();
  }
}
