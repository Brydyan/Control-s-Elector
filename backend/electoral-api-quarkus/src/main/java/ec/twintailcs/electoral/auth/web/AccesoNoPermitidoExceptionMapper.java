package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.domain.AccesoNoPermitidoException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Permisos insuficientes → 403 FORBIDDEN con el shape estandar de error.
 *
 * <p>Es el equivalente del {@code accessDeniedHandler} del backend Spring, que responde 403 con
 * el mensaje "Permisos insuficientes".
 *
 * <p>Este mapper vive en el modulo de auth (y no en el {@code shared/web} compartido) porque es el
 * unico que produce esta excepcion: la lanza el interceptor que evalua la anotacion
 * {@code @Requiere}.
 */
@Provider
public class AccesoNoPermitidoExceptionMapper implements ExceptionMapper<AccesoNoPermitidoException> {

  @Override
  public Response toResponse(AccesoNoPermitidoException ex) {
    return Response.status(403)
        .type("application/json")
        .entity(ApiErrorResponse.body(403, "Forbidden", ex.getMessage()))
        .build();
  }
}
