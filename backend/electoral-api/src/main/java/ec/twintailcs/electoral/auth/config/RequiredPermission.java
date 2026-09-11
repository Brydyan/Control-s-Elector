package ec.twintailcs.electoral.auth.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reserved for future AOP-based declarative permission enforcement.
 *
 * <p>Marca un endpoint que requiere AL MENOS UNO de los permisos especificados.
 *
 * <p>Uso:
 *
 * <pre>{@code
 * @RequiredPermission("evidencia:upload")
 * @PostMapping(...)
 * public ResponseEntity<> upload(...) { ... }
 *
 * @RequiredPermission({"acta:aprobar", "acta:rechazar"})
 * @PatchMapping(...)
 * public ResponseEntity<> cambiarEstado(...) { ... }
 * }</pre>
 *
 * Si el usuario no tiene NINGUNO de los permisos, se lanza AccessDeniedException y SecurityConfig
 * lo mapea a 403 FORBIDDEN.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredPermission {

  /** Uno o mas permisos; con que el usuario tenga UNO, pasa. */
  String[] value();
}
