package ec.twintailcs.electoral.common.web;

import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitDecision;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicies;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimiter;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Limite global por usuario autenticado (politica {@code user-global}) para todo endpoint.
 *
 * <p>Es una red de seguridad contra clientes con un bucle (polling sin pausa, reintentos sin
 * espera), no un limite de negocio: un observador real usa 10-50 req/min y el limite es 1000.
 *
 * <p>Corre despues del matching y despues de {@code JwtAuthFilter} (pre-matching), asi que el
 * usuario ya esta resuelto. Sin {@code nonBlocking}, corre en el mismo hilo worker que el endpoint
 * bloqueante, no en el event loop.
 */
public class UserGlobalRateLimitFilter {

  static final String POLICY = "user-global";

  @Inject RateLimiter rateLimiter;
  @Inject RateLimitPolicies policies;
  @Inject CurrentUser currentUser;

  @ServerRequestFilter
  public Response limit() {
    String email = currentUser.email();
    if (email == null) {
      return null;
    }
    RateLimitDecision decision =
        rateLimiter.consume(policies.get(POLICY), email.trim().toLowerCase(Locale.ROOT));
    return decision.allowed() ? null : RateLimitResponses.tooManyRequests(decision);
  }
}
