package ec.twintailcs.electoral.common.web;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aplica una politica de rate limiting a un endpoint.
 *
 * <pre>
 * &#64;RateLimited("observer-actas")
 * &#64;POST
 * public Response registrarActa(...)
 * </pre>
 *
 * <p>La politica (limite, ventana, clave, modo de falla) se define por nombre en {@code
 * app.ratelimit.policies.*}. Solo admite politicas con {@code key=user} o {@code key=ip}; las de
 * {@code key=custom} las aplica el caso de uso con el puerto {@code RateLimiter}.
 *
 * <p>{@link #value()} es {@link Nonbinding}: sin eso, CDI exige que los valores coincidan con los
 * del interceptor y el interceptor no se aplica a ningun metodo (el defecto de la version
 * anterior).
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

  /** Nombre de la politica en {@code app.ratelimit.policies}. */
  @Nonbinding
  String value() default "";
}
