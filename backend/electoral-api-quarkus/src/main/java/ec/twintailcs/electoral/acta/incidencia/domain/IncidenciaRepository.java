package ec.twintailcs.electoral.acta.incidencia.domain;

import ec.twintailcs.electoral.acta.incidencia.web.dto.IncidenciaResponseDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultas del modulo de incidencias operativas de JRV.
 *
 * <p>La tabla {@code incidencia_jrv} no tiene RLS (a diferencia de {@code acta} y {@code
 * evidencia}), asi que el listado es el mismo para todos los roles que pueden entrar al modulo de
 * coordinacion.
 */
@ApplicationScoped
public class IncidenciaRepository {

  @Inject EntityManager entityManager;

  /** Indica si la JRV existe. */
  public boolean existeJrv(Long jrvId) {
    Object total =
        entityManager
            .createNativeQuery("select count(*) from jrv where id = :id")
            .setParameter("id", jrvId)
            .getSingleResult();
    return ((Number) total).longValue() > 0;
  }

  /** Listado de incidencias, de la mas reciente a la mas antigua. */
  public List<IncidenciaResponseDto> listar(Long jrvId) {
    String sql =
        "select id, jrv_id, tipo, descripcion, evidencia_url, reportada_por_id, fecha_registro"
            + " from incidencia_jrv";
    if (jrvId != null) {
      sql = sql + " where jrv_id = :jrvId";
    }
    sql = sql + " order by fecha_registro desc";

    Query query = entityManager.createNativeQuery(sql, Object[].class);
    if (jrvId != null) {
      query.setParameter("jrvId", jrvId);
    }
    List<Object[]> filas = query.getResultList();
    List<IncidenciaResponseDto> out = new ArrayList<>();
    for (Object[] fila : filas) {
      out.add(
          new IncidenciaResponseDto(
              ((Number) fila[0]).longValue(),
              ((Number) fila[1]).longValue(),
              TipoIncidenciaJrv.valueOf((String) fila[2]),
              (String) fila[3],
              (String) fila[4],
              ((Number) fila[5]).longValue(),
              (Instant) fila[6]));
    }
    return out;
  }
}
