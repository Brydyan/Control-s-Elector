package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Transicion de estado invalida → 409 CONFLICT.
 *
 * <p>Es el codigo que afirma la suite de contrato cuando la transicion pedida no existe desde el
 * estado actual (disputar un acta ya en disputa, aprobar un acta que no esta ENVIADA ni CORREGIDA).
 */
@Provider
public class ActaEstadoInvalidoExceptionMapper
    implements ExceptionMapper<ActaEstadoInvalidoException> {

  @Override
  public Response toResponse(ActaEstadoInvalidoException ex) {
    return Response.status(409)
        .type("application/json")
        .entity(ApiErrorResponse.body(409, "Conflict", ex.getMessage()))
        .build();
  }
}
