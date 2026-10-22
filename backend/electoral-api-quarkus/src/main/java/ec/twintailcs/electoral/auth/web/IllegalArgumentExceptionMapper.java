package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Parametro invalido → 400 BAD REQUEST con el shape estandar de error.
 *
 * <p>Equivalente del handler de {@code IllegalArgumentException} del backend Spring: aca vive en
 * el modulo de auth porque el paquete {@code shared/web} compartido esta fuera de la superficie de
 * edicion de esta fase de la migracion.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

  @Override
  public Response toResponse(IllegalArgumentException ex) {
    return Response.status(400)
        .type("application/json")
        .entity(ApiErrorResponse.body(400, "Bad Request", ex.getMessage()))
        .build();
  }
}
