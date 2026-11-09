package ec.twintailcs.electoral.common.web;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para aplicar rate limiting a un endpoint.
 *
 * <p>El interceptor {@link RateLimitingInterceptor} procesa esta anotación y realiza el chequeo
 * antes de ejecutar el método. Si el límite se excede, devuelve 429 Too Many Requests.
 *
 * <p>Ejemplos:
 *
 * <pre>{@code
 * @RateLimited(limit = 5, windowSeconds = 300, identifier = "ip")
 * @POST
 * @Path("/login")
 * public Response login(...) { ... }
 *
 * @RateLimited(limit = 1, windowSeconds = 86400, identifier = "user")
 * @GET
 * @Path("/export-data")
 * public Response exportData(...) { ... }
 * }</pre>
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

  /**
   * Máximo número de requests en la ventana de tiempo.
   *
   * <p>Valores por defecto según endpoint: - login: 5 (brute force protection) - export-data: 1
   * (GDPR download per 24h) - contact-dpo: 1 (spam prevention per 30s)
   */
  long limit();

  /**
   * Duración de la ventana de tiempo en segundos.
   *
   * <p>Valores típicos: - 300 (5 minutos) para login - 86400 (24 horas) para export - 30 segundos
   * para contact-dpo
   */
  long windowSeconds();

  /**
   * Identificador para construir la clave del bucket.
   *
   * <p>Valores posibles: - "ip": límite por dirección IP del cliente - "user": límite por usuario
   * autenticado (userId del token) - "combined": combina IP + usuario (más restrictivo)
   *
   * <p>Para endpoints públicos (sin autenticación), usar "ip". Para endpoints autenticados, usar
   * "user".
   */
  String identifier() default "ip";

  /**
   * Nombre del endpoint para debugging y logs (opcional).
   *
   * <p>Si no se proporciona, se construye automáticamente a partir del nombre del método.
   */
  String endpoint() default "";
}
