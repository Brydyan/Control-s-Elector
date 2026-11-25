package ec.twintailcs.electoral.evidencia.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Contexto de RLS (Row Level Security) de Postgres para la transaccion en curso.
 *
 * <p>Las tablas {@code acta}, {@code acta_detalle}, {@code evidencia}, {@code recinto}, {@code jrv}
 * y {@code jrv_cargo} aplican RLS: sin el contexto del usuario, Postgres no autoriza las escrituras
 * restringidas. El contexto se activa con {@code set_config('app.current_user_id', ..., true)}, que
 * es el equivalente parametrizado de {@code SET LOCAL}: el valor vive solo durante la transaccion,
 * asi que una conexion del pool no puede arrastrar la identidad de un request a otro.
 *
 * <p>El rol no se manda: la funcion {@code get_current_user_rol_name()} de la base lo deduce del id
 * del usuario. Es lo mismo que hace el backend Spring, que ademas fija {@code app.current_user_rol}
 * por compatibilidad.
 *
 * <p><b>Por que vive en el modulo de evidencias.</b> El paquete {@code shared/security} del backend
 * Spring (que es donde deberia estar) todavia no esta portado y esta fuera de la superficie de
 * edicion de este modulo. Evidencias es el primer modulo que ESCRIBE en una tabla con RLS (la
 * politica de insert exige {@code usuario_id = get_current_user_id()}), asi que es el que tiene el
 * requisito mas estricto. Cuando se porte {@code shared/security}, esta clase se muda ahi y los dos
 * modulos la siguen usando.
 */
@ApplicationScoped
public class RlsContexto {

  @Inject EntityManager entityManager;

  /**
   * Activa el contexto de RLS del usuario para la transaccion en curso.
   *
   * @param usuarioId id interno del usuario autenticado. Si viene {@code null}, no hace nada: sin
   *     identidad no hay contexto que activar.
   */
  public void activar(Long usuarioId) {
    if (usuarioId == null) {
      return;
    }
    entityManager
        .createNativeQuery("select set_config('app.current_user_id', :usuarioId, true)")
        .setParameter("usuarioId", String.valueOf(usuarioId))
        .getSingleResult();
  }
}
