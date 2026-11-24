package ec.twintailcs.electoral.common.web;

import ec.twintailcs.electoral.common.application.ratelimit.RateLimitDecision;
import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.core.Response;

/** Respuesta 429 comun al interceptor, al filtro global y a los casos de uso. */
final class RateLimitResponses {

  static final int TOO_MANY_REQUESTS = 429;

  private RateLimitResponses() {}

  static Response tooManyRequests(RateLimitDecision decision) {
    return Response.status(TOO_MANY_REQUESTS)
        .type("application/json")
        .header("Retry-After", decision.retryAfterSeconds())
        .header("RateLimit-Limit", decision.limit())
        .header("RateLimit-Remaining", decision.remaining())
        .entity(
            ApiErrorResponse.body(
                TOO_MANY_REQUESTS,
                "Too Many Requests",
                "Demasiadas solicitudes. Reintente en "
                    + decision.retryAfterSeconds()
                    + " segundos."))
        .build();
  }
}
