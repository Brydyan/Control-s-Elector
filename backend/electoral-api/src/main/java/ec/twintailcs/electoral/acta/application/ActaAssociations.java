package ec.twintailcs.electoral.acta.application;

import ec.twintailcs.electoral.acta.domain.Acta;
import org.hibernate.Hibernate;

/**
 * Materializa las asociaciones LAZY de un {@link Acta} dentro de la transacción activa.
 *
 * <p>Con {@code spring.jpa.open-in-view=false} la sesión de Hibernate se cierra al salir del
 * {@code @Transactional} que envuelve al caso de uso, pero el controller mapea el DTO de respuesta
 * DESPUÉS de ese límite. Las transiciones de estado cargan el acta con {@code
 * findByIdWithLock}, que no trae fetch join, así que {@code jrv}, {@code cargo} y {@code
 * registradaPor} quedan como proxies LAZY: el mapper los toca y revienta con {@code
 * LazyInitializationException}, de modo que el endpoint responde 500 aunque la escritura ya se
 * haya commiteado (el frontend reintenta y duplica).
 *
 * <p>El fix va del lado de la carga, no del lado de la respuesta: se inicializan las asociaciones
 * dentro de la transacción del caso de uso. NO se resuelve con un fetch join en {@code
 * findByIdWithLock} porque eso extendería el {@code FOR UPDATE} a las tablas de {@code jrv} y {@code
 * cargo} (follow-on locking) y cambiaría el invariante de concurrencia del módulo de actas, que
 * serializa los cambios concurrentes sobre la fila de {@code acta}.
 *
 * <p>Es una utilidad estática y no un bean de Spring a propósito: los casos de uso la invocan desde
 * su propia transacción y así ninguno tiene que cambiar su constructor.
 */
public final class ActaAssociations {

  private ActaAssociations() {}

  /**
   * Inicializa {@code jrv}, {@code cargo} y {@code registradaPor} de la acta.
   *
   * <p>No hace nada si la asociación no es un proxy (entidad ya materializada, por ejemplo el alta
   * directa o el borrador) o si la acta es {@code null}. Debe llamarse con la sesión abierta: fuera
   * de la transacción Hibernate no puede materializar el proxy.
   */
  public static void inicializar(Acta acta) {
    if (acta == null) {
      return;
    }
    Hibernate.initialize(acta.getJrv());
    Hibernate.initialize(acta.getCargo());
    Hibernate.initialize(acta.getRegistradaPor());
  }
}
