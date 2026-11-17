package ec.twintailcs.electoral.legal.web;

import ec.twintailcs.electoral.legal.domain.LegalException;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Error del flujo legal → status con su {@code Retry-After}.
 *
 * <p>El mapper de {@code InvitacionException} ya cubre el 429 del flujo de activacion, pero no
 * emite {@code Retry-After}: ahi el tope es de 60 segundos y el cliente puede reintentar solo. En
 * el export el tope es de 24 horas, asi que el header es la unica informacion util del error.
 */
@Provider
public class LegalExceptionMapper implements ExceptionMapper<LegalException> {

  @Override
  public Response toResponse(LegalException ex) {
    String razon =
        switch (ex.status()) {
          case 400 -> "Bad Request";
          case 429 -> "Too Many Requests";
          default -> "Error";
        };

    Response.ResponseBuilder builder =
        Response.status(ex.status())
            .type("application/json")
            .entity(ApiErrorResponse.body(ex.status(), razon, ex.getMessage()));

    if (ex.retryAfterSeconds() > 0) {
      builder.header("Retry-After", String.valueOf(ex.retryAfterSeconds()));
    }
    return builder.build();
  }
}
