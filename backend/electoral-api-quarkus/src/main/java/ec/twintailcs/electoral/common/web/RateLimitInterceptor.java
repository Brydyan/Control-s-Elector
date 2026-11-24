package ec.twintailcs.electoral.common.web;

import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitDecision;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitExceededException;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicies;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimiter;
import io.quarkus.arc.ArcInvocationContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Optional;

/**
 * Aplica la politica de {@link RateLimited} antes de ejecutar el endpoint.
 *
 * <p>Es un interceptor de ArC (y no un filtro HTTP que lea la anotacion por reflexion) porque la
 * API corre como imagen nativa: ArC resuelve los bindings al compilar, sin depender de reflexion en
 * runtime.
 *
 * <p>Corre antes de {@code @Transactional} ({@code PLATFORM_BEFORE + 200}): un request rechazado no
 * abre transaccion ni toca la base.
 */
@RateLimited
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class RateLimitInterceptor {

  @Inject RateLimiter rateLimiter;
  @Inject RateLimitPolicies policies;
  @Inject CurrentUser currentUser;
  @Inject ClientAddressResolver clientAddress;

  @AroundInvoke
  Object limit(InvocationContext ctx) throws Exception {
    RateLimited binding = binding(ctx);
    if (binding == null || binding.value().isBlank()) {
      throw new IllegalStateException(
          "@RateLimited sin nombre de politica en " + ctx.getMethod().getName());
    }
    RateLimitPolicy policy = policies.get(binding.value());
    Optional<String> identity = identity(policy);
    if (identity.isPresent()) {
      RateLimitDecision decision = rateLimiter.consume(policy, identity.get());
      if (!decision.allowed()) {
        throw new RateLimitExceededException(policy.name(), decision);
      }
    }
    return ctx.proceed();
  }

  private Optional<String> identity(RateLimitPolicy policy) {
    return switch (policy.key()) {
      case USER ->
          Optional.ofNullable(currentUser.email()).map(e -> e.trim().toLowerCase(Locale.ROOT));
      // Sin IP confiable la politica no se aplica: ver ClientAddressResolver#trustedIp.
      case IP -> clientAddress.trustedIp();
      case CUSTOM ->
          throw new IllegalStateException(
              "La politica '"
                  + policy.name()
                  + "' es key=custom: la aplica el caso de uso, no @RateLimited");
    };
  }

  private static RateLimited binding(InvocationContext ctx) {
    if (ctx instanceof ArcInvocationContext arc) {
      for (Annotation a : arc.getInterceptorBindings()) {
        if (a instanceof RateLimited rl) {
          return rl;
        }
      }
    }
    return null;
  }
}
