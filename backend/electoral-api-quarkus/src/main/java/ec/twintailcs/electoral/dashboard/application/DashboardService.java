package ec.twintailcs.electoral.dashboard.application;

import ec.twintailcs.electoral.dashboard.web.dto.NovedadDto;
import ec.twintailcs.electoral.dashboard.web.dto.ProgresoCantonDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResultadoCandidatoDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResultadoCargoDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResumenActasDto;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import ec.twintailcs.electoral.geografia.domain.Canton;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Logica de agregacion del dashboard (solo lectura — no audita, no muta).
 *
 * <p>Reglas duras:
 *
 * <ul>
 *   <li>Toda query excluye {@code ANULADA} directamente. Este service <b>nunca</b> filtra
 *       ANULADA «por si acaso»: si lo hiciera, las mutaciones de las pruebas no detectarian la
 *       regresion.
 *   <li>{@code minutosTranscurridos} se calcula contra {@link Instant#now()} en UTC, sin pasar por
 *       la zona horaria del sistema.
 * </ul>
 *
 * <p><b>Por que consultas nativas y no JPQL.</b> Las tablas que agrega el dashboard ({@code
 * acta}, {@code acta_detalle}, {@code jrv}, {@code parroquia}) son de los modulos de actas y
 * geografia, que todavia no estan portados: mapearlas aca crearia una segunda entidad sobre la
 * misma tabla que habria que mantener en paralelo con la del modulo propietario. La consulta nativa
 * lee exactamente las mismas filas que leeria la JPQL del backend Spring (mismos joins, mismos
 * filtros, mismo orden) y no deja entidad duplicada.
 */
@ApplicationScoped
public class DashboardService {

  @Inject EntityManager entityManager;
  @Inject ContextoSolicitud contexto;
  @Inject RlsContexto rls;

  // ---------------------------------------------------------------------
  // /resumen
  // ---------------------------------------------------------------------

  /**
   * Conteos por estado no-anulado. ANULADA no aparece en ningun contador ni en {@code
   * totalActivas}.
   */
  @Transactional
  public ResumenActasDto resumen() {
    activarContexto();

    Object[] fila =
        (Object[])
            entityManager
                .createNativeQuery(
                    "select count(*) filter (where estado = 'ENVIADA'), "
                        + "count(*) filter (where estado = 'EN_DISPUTA'), "
                        + "count(*) filter (where estado = 'CORREGIDA'), "
                        + "count(*) filter (where estado = 'APROBADA') "
                        + "from acta")
                .getSingleResult();

    long enviadas = ((Number) fila[0]).longValue();
    long enDisputa = ((Number) fila[1]).longValue();
    long corregidas = ((Number) fila[2]).longValue();
    long aprobadas = ((Number) fila[3]).longValue();
    long totalActivas = enviadas + enDisputa + corregidas + aprobadas;

    return new ResumenActasDto(enviadas, enDisputa, corregidas, aprobadas, totalActivas);
  }

  // ---------------------------------------------------------------------
  // /resultados
  // ---------------------------------------------------------------------

  /**
   * Resultados por candidato, agrupados por cargo, con actas ANULADAS excluidas en la query.
   *
   * <p>La query ya ordena por cargo ascendente y votos descendente; el service mantiene el
   * agrupamiento y reordena los candidatos por votos desc (defensa en profundidad).
   */
  @Transactional
  public List<ResultadoCargoDto> resultados() {
    activarContexto();

    List<Object[]> filas =
        entityManager
            .createNativeQuery(
                "select c.id, c.nombre, cand.id, cand.nombre, p.nombre, sum(ad.votos) "
                    + "from acta_detalle ad "
                    + "join acta a on a.id = ad.acta_id "
                    + "join candidato cand on cand.id = ad.candidato_id "
                    + "join cargo c on c.id = cand.cargo_id "
                    + "join partido p on p.id = cand.partido_id "
                    + "where a.estado <> 'ANULADA' "
                    + "group by c.id, c.nombre, cand.id, cand.nombre, p.nombre "
                    + "order by c.nombre asc, sum(ad.votos) desc",
                Object[].class)
            .getResultList();

    Map<Long, List<Object[]>> porCargo = new LinkedHashMap<>();
    for (Object[] fila : filas) {
      long cargoId = ((Number) fila[0]).longValue();
      porCargo.computeIfAbsent(cargoId, k -> new ArrayList<>()).add(fila);
    }

    List<ResultadoCargoDto> out = new ArrayList<>(porCargo.size());
    for (Map.Entry<Long, List<Object[]>> entry : porCargo.entrySet()) {
      List<Object[]> delCargo = entry.getValue();
      List<ResultadoCandidatoDto> candidatos = new ArrayList<>(delCargo.size());
      for (Object[] fila : delCargo) {
        candidatos.add(
            new ResultadoCandidatoDto(
                ((Number) fila[2]).longValue(),
                (String) fila[3],
                (String) fila[4],
                ((Number) fila[5]).longValue()));
      }
      candidatos.sort(Comparator.comparingLong(ResultadoCandidatoDto::votos).reversed());
      out.add(new ResultadoCargoDto(entry.getKey(), (String) delCargo.get(0)[1], candidatos));
    }
    return out;
  }

  // ---------------------------------------------------------------------
  // /progreso
  // ---------------------------------------------------------------------

  /**
   * Progreso por canton: total de JRVs y JRVs con al menos un acta no anulada (DISTINCT).
   *
   * <p>{@code porcentaje} se calcula con {@code 100.0} (no {@code 100}) para no perder la fraccion.
   * Si {@code totalJrvs == 0} devuelve {@code 0.0}: sin division por cero ni {@code NaN}.
   *
   * <p>Una JRV con actas de tres cargos cuenta una sola vez ({@code COUNT(DISTINCT)}).
   */
  @Transactional
  public List<ProgresoCantonDto> progreso() {
    activarContexto();

    List<Object[]> jrvsPorCantonFilas =
        entityManager
            .createNativeQuery(
                "select pa.canton_id, count(*) "
                    + "from jrv j join parroquia pa on pa.id = j.parroquia_id "
                    + "group by pa.canton_id",
                Object[].class)
            .getResultList();
    Map<Long, Long> jrvsPorCanton = new HashMap<>();
    for (Object[] fila : jrvsPorCantonFilas) {
      jrvsPorCanton.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
    }

    List<Object[]> jrvsConActaFilas =
        entityManager
            .createNativeQuery(
                "select pa.canton_id, count(distinct a.jrv_id) "
                    + "from acta a "
                    + "join jrv j on j.id = a.jrv_id "
                    + "join parroquia pa on pa.id = j.parroquia_id "
                    + "where a.estado <> 'ANULADA' "
                    + "group by pa.canton_id",
                Object[].class)
            .getResultList();
    Map<Long, Long> jrvsConActaPorCanton = new HashMap<>();
    for (Object[] fila : jrvsConActaFilas) {
      jrvsConActaPorCanton.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
    }

    List<Canton> cantones = Canton.listAll();
    List<ProgresoCantonDto> out = new ArrayList<>(cantones.size());
    for (Canton canton : cantones) {
      long totalJrvs = jrvsPorCanton.getOrDefault(canton.getId(), 0L);
      long jrvsConActa = jrvsConActaPorCanton.getOrDefault(canton.getId(), 0L);
      double porcentaje = totalJrvs == 0 ? 0.0 : (jrvsConActa * 100.0) / totalJrvs;
      out.add(
          new ProgresoCantonDto(
              canton.getId(), canton.getNombre(), totalJrvs, jrvsConActa, porcentaje));
    }
    out.sort(
        Comparator.comparing(
            ProgresoCantonDto::cantonNombre, Comparator.nullsLast(Comparator.naturalOrder())));
    return out;
  }

  // ---------------------------------------------------------------------
  // /novedades
  // ---------------------------------------------------------------------

  /**
   * Lista actas EN_DISPUTA con JRV, cargo, OBSERVER y {@code minutosTranscurridos}.
   *
   * <p>Las actas en otros estados no aparecen.
   */
  @Transactional
  public List<NovedadDto> novedades() {
    activarContexto();

    List<Object[]> filas =
        entityManager
            .createNativeQuery(
                "select a.public_id, a.jrv_id, j.numero, a.cargo_id, c.nombre, "
                    + "u.public_id, u.nombre, u.apellido, a.fecha_registro "
                    + "from acta a "
                    + "join jrv j on j.id = a.jrv_id "
                    + "join cargo c on c.id = a.cargo_id "
                    + "join usuario u on u.id = a.registrada_por "
                    + "where a.estado = 'EN_DISPUTA' "
                    + "order by a.fecha_registro asc",
                Object[].class)
            .getResultList();

    Instant ahora = Instant.now();
    List<NovedadDto> out = new ArrayList<>(filas.size());
    for (Object[] fila : filas) {
      Instant fechaRegistro = aInstant(fila[8]);
      long minutos =
          fechaRegistro == null
              ? 0L
              : Math.max(0L, Duration.between(fechaRegistro, ahora).toMinutes());
      String observerNombre =
          (fila[6] != null || fila[7] != null)
              ? ((fila[6] == null ? "" : (String) fila[6])
                      + " "
                      + (fila[7] == null ? "" : (String) fila[7]))
                  .trim()
              : null;
      out.add(
          new NovedadDto(
              (UUID) fila[0],
              "ACTA_EN_DISPUTA",
              ((Number) fila[1]).longValue(),
              (String) fila[2],
              ((Number) fila[3]).longValue(),
              (String) fila[4],
              (UUID) fila[5],
              observerNombre,
              null,
              minutos));
    }
    return out;
  }

  /**
   * Activa el contexto de RLS del usuario autenticado para la transaccion en curso.
   *
   * <p>Las tablas {@code acta} y {@code acta_detalle} tienen RLS: sin el contexto, las agregaciones
   * devolverian cero filas.
   */
  private void activarContexto() {
    rls.activar(contexto.usuarioId());
  }

  /** Convierte el valor de una columna {@code timestamptz} a instante UTC. */
  private Instant aInstant(Object valor) {
    if (valor instanceof Instant instante) {
      return instante;
    }
    if (valor instanceof OffsetDateTime fechaConZona) {
      return fechaConZona.toInstant();
    }
    if (valor instanceof java.sql.Timestamp timestamp) {
      return timestamp.toInstant();
    }
    return null;
  }
}
