package ec.twintailcs.electoral.evidencia.web;

import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Evidencia inexistente → 404 NOT FOUND con el shape estandar de error.
 *
 * <p>Es el equivalente del handler de {@code EvidenciaNotFoundException} del backend Spring.
 */
@Provider
public class EvidenciaNotFoundExceptionMapper
    implements ExceptionMapper<EvidenciaNotFoundException> {

  @Override
  public Response toResponse(EvidenciaNotFoundException ex) {
    return Response.status(404)
        .type("application/json")
        .entity(ApiErrorResponse.body(404, "Not Found", ex.getMessage()))
        .build();
  }
}
