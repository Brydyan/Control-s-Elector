package ec.twintailcs.electoral.dashboard.service;

import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.dashboard.web.dto.NovedadDto;
import ec.twintailcs.electoral.dashboard.web.dto.ProgresoCantonDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResultadoCargoDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResultadoCandidatoDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResumenActasDto;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.CantonRepository;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de agregación del dashboard (sólo lectura — no audita, no muta).
 *
 * <p>Reglas duras:
 *
 * <ul>
 *   <li>Toda query excluye {@code ANULADA} directamente (ver {@code design.md} D1). Este service
 *       <b>nunca</b> filtra ANULADA "por si acaso" — si lo hiciera, las mutaciones de las pruebas
 *       no detectarían la regresión.
 *   <li>{@code minutosTranscurridos} se calcula contra {@link Instant#now()} en UTC, sin pasar por
 *       {@code ZoneId.systemDefault()} (ver {@code design.md} D4).
 * </ul>
 */
@Service
public class DashboardService {

  private final ActaRepository actaRepository;
  private final ActaDetalleRepository actaDetalleRepository;
  private final JrvRepository jrvRepository;
  private final CantonRepository cantonRepository;

  public DashboardService(
      ActaRepository actaRepository,
      ActaDetalleRepository actaDetalleRepository,
      JrvRepository jrvRepository,
      CantonRepository cantonRepository) {
    this.actaRepository = actaRepository;
    this.actaDetalleRepository = actaDetalleRepository;
    this.jrvRepository = jrvRepository;
    this.cantonRepository = cantonRepository;
  }

  // ---------------------------------------------------------------------
  // RF-DASH-03: /resumen
  // ---------------------------------------------------------------------

  /**
   * Conteos por estado no-anulado. ANULADA no aparece en ningún contador ni en {@code
   * totalActivas}.
   */
  @Transactional(readOnly = true)
  public ResumenActasDto resumen() {
    long enviadas = actaRepository.countByEstado(EstadoActa.ENVIADA);
    long enDisputa = actaRepository.countByEstado(EstadoActa.EN_DISPUTA);
    long corregidas = actaRepository.countByEstado(EstadoActa.CORREGIDA);
    long aprobadas = actaRepository.countByEstado(EstadoActa.APROBADA);
    long totalActivas = enviadas + enDisputa + corregidas + aprobadas;
    return new ResumenActasDto(enviadas, enDisputa, corregidas, aprobadas, totalActivas);
  }

  // ---------------------------------------------------------------------
  // RF-DASH-04: /resultados
  // ---------------------------------------------------------------------

  /**
   * Resultados por candidato, agrupados por cargo, con actas ANULADAS excluidas en query.
   *
   * <p>Reorganiza filas planas ({@code ActaDetalleRepository.votosTotalesPorCandidato()}) en bloques
   * por cargo. La query ya ordena por cargo ascendente y votos descendente; el service sólo
   * mantiene el agrupamiento y reordena los candidatos por votos desc (defensa en profundidad).
   */
  @Transactional(readOnly = true)
  public List<ResultadoCargoDto> resultados() {
    List<Object[]> rows = actaDetalleRepository.votosTotalesPorCandidato();
    Map<Long, List<Object[]>> byCargo = new LinkedHashMap<>();
    List<Long> cargoOrder = new ArrayList<>();
    for (Object[] row : rows) {
      Long cargoId = (Long) row[0];
      byCargo.computeIfAbsent(
          cargoId,
          k -> {
            cargoOrder.add(k);
            return new ArrayList<>();
          });
      byCargo.get(cargoId).add(row);
    }

    List<ResultadoCargoDto> out = new ArrayList<>(cargoOrder.size());
    for (Long cargoId : cargoOrder) {
      List<Object[]> cs = byCargo.get(cargoId);
      List<ResultadoCandidatoDto> candidatos = new ArrayList<>(cs.size());
      for (Object[] row : cs) {
        // row = [cargoId, cargoNombre, candidatoId, candidatoNombre, partidoNombre, votos]
        candidatos.add(
            new ResultadoCandidatoDto(
                (Long) row[2],
                (String) row[3],
                (String) row[4],
                ((Number) row[5]).longValue()));
      }
      candidatos.sort(Comparator.comparingLong(ResultadoCandidatoDto::votos).reversed());
      out.add(new ResultadoCargoDto((Long) cs.get(0)[0], (String) cs.get(0)[1], candidatos));
    }
    return out;
  }

  // ---------------------------------------------------------------------
  // RF-DASH-05: /progreso
  // ---------------------------------------------------------------------

  /**
   * Progreso por cantón: total de JRVs y JRVs con al menos un acta no anulada (DISTINCT).
   *
   * <p>{@code porcentaje} se calcula con {@code 100.0} (no {@code 100}) para no perder la
   * fracción. Si {@code totalJrvs == 0} devuelve {@code 0.0} — sin división por cero ni {@code NaN}.
   *
   * <p>Una JRV con actas de tres cargos cuenta una sola vez ({@code COUNT(DISTINCT)} en la query).
   */
  @Transactional(readOnly = true)
  public List<ProgresoCantonDto> progreso() {
    Map<Long, Long> jrvsPorCanton = new LinkedHashMap<>();
    for (Object[] row : jrvRepository.countJrvsGroupedByCanton()) {
      jrvsPorCanton.put((Long) row[0], ((Number) row[1]).longValue());
    }
    Map<Long, Long> jrvsConActaPorCanton = new LinkedHashMap<>();
    for (Object[] row : actaRepository.countDistinctJrvsWithActaByCanton()) {
      jrvsConActaPorCanton.put((Long) row[0], ((Number) row[1]).longValue());
    }

    List<Canton> cantones = cantonRepository.findAll();
    List<ProgresoCantonDto> out = new ArrayList<>(cantones.size());
    for (Canton c : cantones) {
      long totalJrvs = jrvsPorCanton.getOrDefault(c.getId(), 0L);
      long jrvsConActa = jrvsConActaPorCanton.getOrDefault(c.getId(), 0L);
      double porcentaje = totalJrvs == 0 ? 0.0 : (jrvsConActa * 100.0) / totalJrvs;
      out.add(
          new ProgresoCantonDto(c.getId(), c.getNombre(), totalJrvs, jrvsConActa, porcentaje));
    }
    out.sort(Comparator.comparing(ProgresoCantonDto::cantonNombre, Comparator.nullsLast(Comparator.naturalOrder())));
    return out;
  }

  // ---------------------------------------------------------------------
  // RF-DASH-06 + RF-DASH-07: /novedades
  // ---------------------------------------------------------------------

  /**
   * Lista actas EN_DISPUTA con JRV, cargo, OBSERVER y {@code minutosTranscurridos}.
   *
   * <p>Las actas en otros estados no aparecen.
   *
   * <p>El campo {@code motivo} está hoy siempre en {@code null}: {@code Acta} no tiene columna
   * {@code motivo_disputa} (ver {@code design.md} D1 y el {@code apply-progress.md} de T-BE-13).
   */
  @Transactional(readOnly = true)
  public List<NovedadDto> novedades() {
    List<Object[]> rows = actaRepository.findEnDisputaParaDashboard();
    List<NovedadDto> out = new ArrayList<>(rows.size());
    Instant ahora = Instant.now();
    for (Object[] row : rows) {
      // row = [actaPublicId, jrvId, jrvNumero, cargoId, cargoNombre,
      //        observerPublicId, observerNombre, observerApellido, fechaRegistro]
      Instant fechaRegistro = (Instant) row[8];
      long minutos = Math.max(0L, Duration.between(fechaRegistro, ahora).toMinutes());
      String observerNombre =
          (row[6] != null || row[7] != null)
              ? ((row[6] == null ? "" : (String) row[6])
                  + " "
                  + (row[7] == null ? "" : (String) row[7])).trim()
              : null;
      out.add(
          new NovedadDto(
              (java.util.UUID) row[0],
              "ACTA_EN_DISPUTA",
              (Long) row[1],
              (String) row[2],
              (Long) row[3],
              (String) row[4],
              (java.util.UUID) row[5],
              observerNombre,
              null,
              minutos));
    }
    return out;
  }
}
