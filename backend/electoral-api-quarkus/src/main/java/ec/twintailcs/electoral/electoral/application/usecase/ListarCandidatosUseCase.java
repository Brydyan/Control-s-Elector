package ec.twintailcs.electoral.electoral.application.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.shared.cache.CacheDeLectura;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Listado de candidatos, con filtros opcionales por cargo y por partido.
 *
 * <p>Las relaciones se traen con {@code join fetch}: el DTO de respuesta necesita el id del cargo y
 * el id del partido, y asi el mapeo nunca toca un proxy perezoso fuera de la transaccion.
 */
@ApplicationScoped
public class ListarCandidatosUseCase {

  private static final TypeReference<List<Candidato>> TIPO_CANDIDATOS = new TypeReference<>() {};

  @Inject CacheDeLectura cache;

  /**
   * Candidatos del cargo y del partido pedidos. Cacheado con TTL de 2 minutos.
   *
   * <p>La clave lleva los filtros: el listado sin filtros y el de un cargo son datos distintos y no
   * pueden compartir entrada.
   */
  @Transactional
  public List<Candidato> execute(Long cargoId, Long partidoId) {
    String clave = "cargo:" + cargoId + "|partido:" + partidoId;
    Optional<List<Candidato>> cacheado = cache.leer("candidatos", clave, TIPO_CANDIDATOS);
    if (cacheado.isPresent()) {
      return cacheado.get();
    }
    List<Candidato> candidatos = buscar(cargoId, partidoId);
    cache.guardar("candidatos", clave, candidatos);
    return candidatos;
  }

  private List<Candidato> buscar(Long cargoId, Long partidoId) {
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
