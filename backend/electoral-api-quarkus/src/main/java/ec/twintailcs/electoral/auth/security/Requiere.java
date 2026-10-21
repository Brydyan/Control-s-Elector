package ec.twintailcs.electoral.auth.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Autorizacion de un endpoint, en el mismo formato que los {@code @PreAuthorize} del backend
 * Spring para que el port sea mecanico.
 *
 * <p>Mapeo:
 *
 * <pre>
 *   hasAuthority('acta:anular')                    ->  @Requiere(permisos = "acta:anular")
 *   hasAnyAuthority('a','b')                        ->  @Requiere(permisos = {"a", "b"})
 *   hasAnyRole('COORDINATOR','MASTER')              ->  @Requiere(roles = {"COORDINATOR", "MASTER"})
 *   hasAuthority('sesion:list') or hasRole('MASTER')->  @Requiere(permisos = "sesion:list", roles = "MASTER")
 *   isAuthenticated()                               ->  @Requiere.autenticado()
 *   @authz.has('acta:upload')                      ->  @Requiere(permisos = "acta:upload")
 * </pre>
 *
 * <p>Semantica: dentro de cada grupo alcanza con UNO (OR, como en Spring). Entre los dos grupos
 * tambien es OR: con los dos grupos presente alcanza con cumplir cualquiera de los dos. Si no se
 * declara nada, el unico requisito es estar autenticado.
 *
 * <p>El usuario tiene como roles su rol ({@code MASTER}, {@code COORDINATOR}, ...) mas los
 * permisos atomicos del claim {@code perms} ({@code acta:aprobar}, ...).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Requiere {

  /** Permisos atomicos {@code recurso:accion}. Alcanza con uno. */
  String[] permisos() default {};

  /** Roles ({@code MASTER}, {@code COORDINATOR}, {@code ADMIN}, {@code OBSERVER}). Alcanza con uno. */
  String[] roles() default {};

  /** El unico requisito es estar autenticado. */
  boolean autenticado() default false;
}
