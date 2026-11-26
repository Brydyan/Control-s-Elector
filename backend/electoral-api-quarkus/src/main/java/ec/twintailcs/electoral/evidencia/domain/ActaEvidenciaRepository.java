package ec.twintailcs.electoral.evidencia.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/**
 * Consulta del acta que el modulo de evidencias necesita para validar una subida.
 *
 * <p>La tabla {@code acta} tiene RLS activada: la consulta respeta las politicas (un OBSERVER solo
 * ve las actas que registro, COORDINATOR/MASTER/ADMIN ven todas), igual que en el backend Spring.
 * Por eso una acta ajena a un OBSERVER se ve como inexistente, no como ajena.
 */
@ApplicationScoped
public class ActaEvidenciaRepository {

  @Inject EntityManager entityManager;

  /** Busca el acta por su id interno. Vacio si no existe o si el RLS la oculta. */
  public Optional<ActaEvidencia> buscarPorId(Long id) {
    List<Object[]> filas =
        entityManager
            .createNativeQuery(
                "select id, jrv_id, cargo_id, estado, registrada_por from acta where id = :id")
            .setParameter("id", id)
            .getResultList();

    if (filas.isEmpty()) {
      return Optional.empty();
    }
    Object[] fila = filas.get(0);
    if (fila[1] == null || fila[2] == null) {
      throw new IllegalStateException(
          "Acta inválida: jrv_id o cargo_id nulo. id=" + fila[0]);
    }
    return Optional.of(
        new ActaEvidencia(
            ((Number) fila[0]).longValue(),
            ((Number) fila[1]).longValue(),
            ((Number) fila[2]).longValue(),
            (String) fila[3],
            fila[4] == null ? null : ((Number) fila[4]).longValue()));
  }
}
