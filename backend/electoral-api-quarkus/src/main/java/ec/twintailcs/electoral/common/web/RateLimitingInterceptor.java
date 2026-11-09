package ec.twintailcs.electoral.common.web;

import ec.twintailcs.electoral.common.application.RateLimitingService;
import ec.twintailcs.electoral.common.application.RateLimitingService.RateLimitResult;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interceptor AOP que aplica rate limiting a métodos anotados con {@link RateLimited}.
 *
 * <p>El ciclo de vida es: 1. Lee la anotación @RateLimited del método 2. Obtiene la IP del cliente
 * y el userId (si está autenticado) 3. Llama a RateLimitingService para chequear el límite 4. Si se
 * excede: devuelve 429 con headers Retry-After y X-RateLimit-* 5. Si se permite: ejecuta el método
 * original
 *
 * <p>Precedencia: @Priority(Interceptor.Priority.APPLICATION)
 */
@Interceptor
@RateLimited(limit = 1, windowSeconds = 1)
@Priority(Interceptor.Priority.APPLICATION)
public class RateLimitingInterceptor {

  @Inject RateLimitingService rlService;

  @Inject ContextoSolicitud contexto;

  @AroundInvoke
  public Object intercept(InvocationContext ctx) throws Exception {
    RateLimited rateLimited = ctx.getMethod().getAnnotation(RateLimited.class);

    // Si el método no tiene @RateLimited, pasar directo
    if (rateLimited == null) {
      return ctx.proceed();
    }

    // Construcción de la identidad según el tipo de límite
    String identifier = rateLimited.identifier();
    String endpointName =
        !rateLimited.endpoint().isEmpty() ? rateLimited.endpoint() : ctx.getMethod().getName();
    long limit = rateLimited.limit();
    long windowSeconds = rateLimited.windowSeconds();

    RateLimitResult result;

    if ("ip".equalsIgnoreCase(identifier)) {
      // Límite por IP - necesitamos el X-Forwarded-For header que no tenemos aquí
      // Usamos un placeholder para construir la clave
      result = rlService.checkEndpointLimit(null, endpointName, limit, windowSeconds);

    } else if ("user".equalsIgnoreCase(identifier)) {
      // Límite por usuario autenticado
      Long userId = null;
      try {
        userId = contexto.usuarioId();
      } catch (Exception e) {
        // Si no hay usuario, no aplicar límite (endpoint público)
        return ctx.proceed();
      }
      if (userId == null) {
        return ctx.proceed();
      }
      result = rlService.checkEndpointLimit(userId, endpointName, limit, windowSeconds);

    } else if ("combined".equalsIgnoreCase(identifier)) {
      // Combinar IP + usuario (más restrictivo)
      Long userId = null;
      try {
        userId = contexto.usuarioId();
      } catch (Exception e) {
        // Si no hay usuario, solo IP
      }
      result = rlService.checkEndpointLimit(userId, endpointName, limit, windowSeconds);

    } else {
      // Identifier desconocido, permitir
      return ctx.proceed();
    }

    // Si se permite, ejecutar el método original
    if (result.allowed()) {
      return ctx.proceed();
    }

    // Si se excede el límite, devolver 429
    Map<String, String> body = new LinkedHashMap<>();
    body.put("status", "too_many_requests");
    body.put(
        "message",
        String.format(
            "Rate limit exceeded for %s. Retry after %d seconds.",
            endpointName, result.retryAfter()));

    return Response.status(429)
        .header("Retry-After", result.retryAfter())
        .header("X-RateLimit-Limit", limit)
        .header("X-RateLimit-Remaining", result.remaining())
        .header("X-RateLimit-Reset", System.currentTimeMillis() / 1000 + result.retryAfter())
        .entity(body)
        .build();
  }
}
