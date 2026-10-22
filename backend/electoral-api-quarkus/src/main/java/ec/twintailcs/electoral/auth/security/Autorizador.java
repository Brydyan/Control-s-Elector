package ec.twintailcs.electoral.auth.security;

import ec.twintailcs.electoral.auth.domain.AccesoNoPermitidoException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;

/**
 * Exige permisos o roles al usuario autenticado.
 *
 * <p>Equivalente del {@code @PreAuthorize} de Spring Security, con la misma semantica:
 *
 * <pre>
 *   hasAuthority('acta:anular')                     ->  exigir("acta:anular")
 *   hasAnyAuthority('a','b')                         ->  exigir("a", "b")
 *   hasAnyRole('COORDINATOR','MASTER')               ->  exigir("COORDINATOR", "MASTER")
 *   hasAuthority('x') or hasRole('MASTER')           ->  exigir("x", "MASTER")
 *   @authz.has('acta:upload')                        ->  exigir("acta:upload")
 *   isAuthenticated()                                  ->  (nada: el filtro ya exige token)
 * </pre>
 *
 * <p>Alcanza con CUMPLIR uno cualquiera de los indicators (OR). El conjunto efectivo del usuario es
 * su rol ({@code MASTER}, {@code COORDINATOR}, ...) mas los permisos atomicos del claim {@code perms}
 * ({@code acta:aprobar}, ...).
 *
 * <p><b>Por que una llamada explicita y no una anotacion.</b> La autorizacion tiene que evaluarse
 * DESPUES de la validacion del body: en Spring la validacion del bean corre antes que el
 * {@code @PreAuthorize}, asi que un body invalido con un rol sin permiso devuelve 400 y no 403. Un
 * filtro de request no puede reproducirlo (corre antes de que el body se deserialice) y un
 * interceptor CDI sobre el metodo del recurso tampoco: Quarkus REST invoca los metodos del recurso
 * directamente y no les aplica interceptores. La llamada va dentro del metodo, que es donde la
 * validacion ya corrio.
 */
@ApplicationScoped
public class Autorizador {

  @Inject CurrentUser currentUser;

  /**
   * Exige al menos uno de los authorities indicados.
   *
   * @throws AccesoNoPermitidoException si el usuario no cumple ninguno (mapea a 403)
   */
  public void exigir(String... authorities) {
    if (authorities == null || authorities.length == 0) {
      return;
    }
    Set<String> efectivos = currentUser.rolesEfectivos();
    for (String authority : authorities) {
      if (efectivos.contains(authority)) {
        return;
      }
    }
    throw new AccesoNoPermitidoException("Permisos insuficientes");
  }
}
