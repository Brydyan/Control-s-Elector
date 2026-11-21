package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.domain.Candidato;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Listado de candidatos, con filtros opcionales por cargo y por partido.
 *
 * <p>Las relaciones se traen con {@code join fetch}: el DTO de respuesta necesita el id del cargo y
 * el id del partido, y asi el mapeo nunca toca un proxy perezoso fuera de la transaccion.
 */
@ApplicationScoped
public class ListarCandidatosUseCase {

  /** Candidatos del cargo y del partido pedidos. Cacheado con TTL de 10 minutos. */
  @Transactional
  @CacheResult(cacheName = "candidatos")
  public List<Candidato> execute(Long cargoId, Long partidoId) {
    if (cargoId != null && partidoId != null) {
      return Candidato.getEntityManager()
          .createQuery(
              "select c from Candidato c join fetch c.cargo join fetch c.partido"
                  + " where c.cargo.id = :cargoId and c.partido.id = :partidoId",
              Candidato.class)
          .setParameter("cargoId", cargoId)
          .setParameter("partidoId", partidoId)
          .getResultList();
    }
    if (cargoId != null) {
      return Candidato.getEntityManager()
          .createQuery(
              "select c from Candidato c join fetch c.cargo join fetch c.partido"
                  + " where c.cargo.id = :cargoId",
              Candidato.class)
          .setParameter("cargoId", cargoId)
          .getResultList();
    }
    if (partidoId != null) {
      return Candidato.getEntityManager()
          .createQuery(
              "select c from Candidato c join fetch c.cargo join fetch c.partido"
                  + " where c.partido.id = :partidoId",
              Candidato.class)
          .setParameter("partidoId", partidoId)
          .getResultList();
    }
    return Candidato.getEntityManager()
        .createQuery(
            "select c from Candidato c join fetch c.cargo join fetch c.partido", Candidato.class)
        .getResultList();
  }
}
