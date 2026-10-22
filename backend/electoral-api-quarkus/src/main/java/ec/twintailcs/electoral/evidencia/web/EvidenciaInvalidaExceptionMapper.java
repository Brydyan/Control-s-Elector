package ec.twintailcs.electoral.evidencia.web;

import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Evidencia invalida → 400 BAD REQUEST con el shape estandar de error.
 *
 * <p>Es el equivalente del handler de {@code EvidenciaInvalidaException} del backend Spring: son
 * entradas invalidas (archivo que no es una imagen, acta que no existe, estado que no es
 * transicionable, demasiadas observaciones), no fallos del servidor.
 */
@Provider
public class EvidenciaInvalidaExceptionMapper
    implements ExceptionMapper<EvidenciaInvalidaException> {

  @Override
  public Response toResponse(EvidenciaInvalidaException ex) {
    return Response.status(400)
        .type("application/json")
        .entity(ApiErrorResponse.body(400, "Bad Request", ex.getMessage()))
        .build();
  }
}
