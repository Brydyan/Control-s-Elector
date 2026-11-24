package ec.twintailcs.electoral.common.web;

import ec.twintailcs.electoral.common.application.ratelimit.RateLimitExceededException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Limite superado → 429 con {@code Retry-After} y cabeceras {@code RateLimit-Limit} / {@code
 * RateLimit-Remaining}, en el shape estandar de error.
 *
 * <p>El frontend trata el 429 como transitorio ({@code core/offline/fallos.ts}) y reintenta
 * respetando {@code Retry-After}.
 */
@Provider
public class RateLimitExceededExceptionMapper
    implements ExceptionMapper<RateLimitExceededException> {

  @Override
  public Response toResponse(RateLimitExceededException ex) {
    return RateLimitResponses.tooManyRequests(ex.decision());
  }
}
