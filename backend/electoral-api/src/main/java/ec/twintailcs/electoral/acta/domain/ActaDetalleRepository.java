package ec.twintailcs.electoral.acta.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActaDetalleRepository extends JpaRepository<ActaDetalle, Long> {
  List<ActaDetalle> findByActaId(Long actaId);

  List<ActaDetalle> findByCandidatoId(Long candidatoId);

  List<ActaDetalle> findByActa(Acta acta);

  void deleteByActa(Acta acta);

  @Query("SELECT COALESCE(SUM(d.votos), 0) FROM ActaDetalle d WHERE d.acta = :acta")
  Integer sumarVotosByActa(@Param("acta") Acta acta);

  /**
   * Candidate-votes subtotal for a single acta, typed as {@link Long} (JPQL SUM over integers).
   * Used to enrich single-item coordinator responses without N+1.
   */
  @Query("SELECT COALESCE(SUM(d.votos), 0) FROM ActaDetalle d WHERE d.acta.id = :actaId")
  Long sumarVotosPorActaId(@Param("actaId") Long actaId);

  /**
   * Suma de votos por candidato, agrupada por candidato y por cargo, omitiendo actas ANULADAS.
   *
   * <p>Cada fila es un candidato dentro de un cargo, con sus votos totales en actas no anuladas. El
   * service agrupa filas por cargo para construir el DTO de respuesta (RF-DASH-04).
   *
   * <p>Filtro de ANULADA obligatorio en query — ver {@code design.md} D1. Sin este filtro, un acta
   * anulada con votos inflados sumaría al resultado publicado (riesgo electoral).
   *
   * @return rows of {@code [cargoId, cargoNombre, candidatoId, candidatoNombre, partidoNombre,
   *     totalVotos]} ordenadas por cargo ascendente y votos descendente.
   */
  @Query(
      "SELECT d.candidato.cargo.id, d.candidato.cargo.nombre, d.candidato.id, "
          + "d.candidato.nombre, d.candidato.partido.nombre, SUM(d.votos) "
          + "FROM ActaDetalle d "
          + "WHERE d.acta.estado <> 'ANULADA' "
          + "GROUP BY d.candidato.cargo.id, d.candidato.cargo.nombre, d.candidato.id, "
          + "d.candidato.nombre, d.candidato.partido.nombre "
          + "ORDER BY d.candidato.cargo.nombre ASC, SUM(d.votos) DESC")
  List<Object[]> votosTotalesPorCandidato();
}
