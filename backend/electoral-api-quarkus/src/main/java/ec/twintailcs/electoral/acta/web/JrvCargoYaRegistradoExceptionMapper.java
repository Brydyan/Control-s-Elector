package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.domain.JrvCargoYaRegistradoException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Ya existe un acta activa para el par (JRV, cargo) → 409 CONFLICT.
 *
 * <p>Es la traduccion del choque con el indice unico {@code uk_acta_activa}: dos observers que
 * registran el mismo par a la vez, el segundo recibe 409 y no un error de servidor.
 */
@Provider
public class JrvCargoYaRegistradoExceptionMapper
    implements ExceptionMapper<JrvCargoYaRegistradoException> {

  @Override
  public Response toResponse(JrvCargoYaRegistradoException ex) {
    return Response.status(409)
        .type("application/json")
        .entity(ApiErrorResponse.body(409, "Conflict", ex.getMessage()))
        .build();
  }
}
