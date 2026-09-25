package ec.twintailcs.electoral.geografia.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JrvRepository extends JpaRepository<Jrv, Long> {
  Optional<Jrv> findByNumero(String numero);

  List<Jrv> findByParroquia(Parroquia parroquia);

  /**
   * Cuenta JRVs cuya parroquia pertenece al cantón dado. Derivado por convención Spring Data. Usado
   * por el dashboard (/progreso) para calcular el denominador del porcentaje.
   */
  long countByParroquia_Canton(Canton canton);

  /**
   * Igual que {@link #countByParroquia_Canton(Canton)} pero evita cargar la entidad cuando sólo se
   * tiene el id. Spring Data traduce {@code Parroquia_Canton_Id} a {@code parroquia.canton.id}.
   */
  long countByParroquia_Canton_Id(Long cantonId);

  /**
   * Cuenta JRVs agrupadas por cantón en una sola consulta (rows: {@code [cantonId, total]}).
   * Pensado para el dashboard — evita N+1 sobre cantones. Cantones sin JRVs no aparecen en el
   * resultado y se manejan en el service.
   */
  @Query("SELECT j.parroquia.canton.id, COUNT(j) FROM Jrv j GROUP BY j.parroquia.canton.id")
  List<Object[]> countJrvsGroupedByCanton();
}
