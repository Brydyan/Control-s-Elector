package ec.twintailcs.electoral.acta.domain;

import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActaRepository extends JpaRepository<Acta, Long> {
  Optional<Acta> findByJrvIdAndCargoId(Long jrvId, Long cargoId);

  List<Acta> findByJrvId(Long jrvId);

  List<Acta> findByEstado(EstadoActa estado);

  List<Acta> findByRegistradaPorId(Long usuarioId);

  @Query("SELECT a FROM Acta a WHERE a.jrv = :jrv AND a.cargo = :cargo AND a.estado != 'ANULADA'")
  Optional<Acta> findActaActiva(@Param("jrv") Jrv jrv, @Param("cargo") Cargo cargo);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Acta a WHERE a.jrv = :jrv AND a.cargo = :cargo AND a.estado != 'ANULADA'")
  Optional<Acta> findActaActivaConLock(@Param("jrv") Jrv jrv, @Param("cargo") Cargo cargo);

  /**
   * Busca un acta no-anulada del observador para (jrv, cargo). Usado por guardarBorrador() para
   * reanudar en lugar de duplicar.
   */
  @Query(
      "SELECT a FROM Acta a WHERE a.jrv.id = :jrvId AND a.cargo.id = :cargoId "
          + "AND a.registradaPor.id = :observerId AND a.estado != 'ANULADA'")
  Optional<Acta> findActaActivaBy(
      @Param("jrvId") Long jrvId,
      @Param("cargoId") Long cargoId,
      @Param("observerId") Long observerId);

  /**
   * Lock pesimista por id. Usado en transiciones (disputar/corregir/ aprobar/anular) para
   * serializar cambios concurrentes sobre la misma fila.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Acta a WHERE a.id = :id")
  Optional<Acta> findByIdWithLock(@Param("id") Long id);

  @Query(
      """
        SELECT a FROM Acta a
        WHERE (:estado IS NULL OR a.estado = :estado)
          AND (:jrvId IS NULL OR a.jrv.id = :jrvId)
          AND (:cargoId IS NULL OR a.cargo.id = :cargoId)
          AND (:registradaPorId IS NULL OR a.registradaPor.id = :registradaPorId)
        ORDER BY a.fechaRegistro DESC
        """)
  List<Acta> findWithFilters(
      @Param("estado") EstadoActa estado,
      @Param("jrvId") Long jrvId,
      @Param("cargoId") Long cargoId,
      @Param("registradaPorId") Long registradaPorId);

  /**
   * Paginated variant of {@link #findWithFilters} for high-volume listings (coordinator inbox).
   *
   * <p>Returns {@link ec.twintailcs.electoral.acta.web.dto.ActaResponseDto} directly via a JPQL
   * constructor projection: cargo name and JRV number come from inner joins (both FKs are NOT NULL)
   * and the candidate-votes subtotal from a correlated subquery over {@code ActaDetalle}. Single
   * statement, no N+1.
   *
   * <p>Sorting comes from the {@link Pageable}; do not add a hardcoded ORDER BY here.
   *
   * <p>{@code pattern} is a precomputed LIKE pattern (already lowercased, wildcards escaped,
   * wrapped in '%...%') matched case-insensitively against the JRV number (via join on {@code
   * a.jrv.numero}) and against the acta id rendered as string. It must be passed as a plain bind
   * variable — never wrap a bind parameter in LOWER()/CONCAT() here, a NULL parameter makes
   * Postgres infer bytea and fail the whole statement. When {@code pattern} parses as a number, an
   * exact id match via {@code searchId} is included as well. Pass {@code null} for both to disable
   * the clause.
   */
  @Query(
      value =
          """
        SELECT new ec.twintailcs.electoral.acta.web.dto.ActaResponseDto(
          a.id, a.jrv.id, a.cargo.id, a.registradaPor.id, a.estado, a.storageUrl,
          a.totalSufragantes, a.votosBlancos, a.votosNulos, a.fechaRegistro, a.motivoAnulacion,
          c.nombre, j.numero,
          (SELECT COALESCE(SUM(d.votos), 0) FROM ActaDetalle d WHERE d.acta = a))
        FROM Acta a JOIN a.jrv j JOIN a.cargo c
        WHERE (:estado IS NULL OR a.estado = :estado)
          AND (:jrvId IS NULL OR a.jrv.id = :jrvId)
          AND (:cargoId IS NULL OR a.cargo.id = :cargoId)
          AND (:pattern IS NULL
            OR LOWER(j.numero) LIKE :pattern ESCAPE '\\'
            OR CAST(a.id AS string) LIKE :pattern ESCAPE '\\'
            OR (:searchId IS NOT NULL AND a.id = :searchId)
            OR (:estadoSearch IS NOT NULL AND a.estado = :estadoSearch))
        """,
      countQuery =
          """
        SELECT COUNT(a) FROM Acta a LEFT JOIN a.jrv j
        WHERE (:estado IS NULL OR a.estado = :estado)
          AND (:jrvId IS NULL OR a.jrv.id = :jrvId)
          AND (:cargoId IS NULL OR a.cargo.id = :cargoId)
          AND (:pattern IS NULL
            OR LOWER(j.numero) LIKE :pattern ESCAPE '\\'
            OR CAST(a.id AS string) LIKE :pattern ESCAPE '\\'
            OR (:searchId IS NOT NULL AND a.id = :searchId)
            OR (:estadoSearch IS NOT NULL AND a.estado = :estadoSearch))
        """)
  Page<ec.twintailcs.electoral.acta.web.dto.ActaResponseDto> findPageWithFilters(
      @Param("estado") EstadoActa estado,
      @Param("jrvId") Long jrvId,
      @Param("cargoId") Long cargoId,
      @Param("pattern") String pattern,
      @Param("searchId") Long searchId,
      @Param("estadoSearch") EstadoActa estadoSearch,
      Pageable pageable);

  long countByEstado(EstadoActa estado);
}
