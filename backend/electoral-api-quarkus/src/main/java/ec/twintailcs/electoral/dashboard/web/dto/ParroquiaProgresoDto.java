package ec.twintailcs.electoral.dashboard.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Linea de progreso por parroquia, dentro del desglose de un canton.
 *
 * <p>Es el nivel mas granular del panel "Desglose de Parroquias" del dashboard. Comparte la
 * semantica con {@link ProgresoCantonDto} y por eso sus dos contadores se leen igual:
 *
 * <ul>
 *   <li>{@code jrvsTotal}: JRVs de la parroquia.
 *   <li>{@code jrvsEscrutadas}: JRVs con al menos un acta no anulada (DISTINCT: una JRV con tres
 *       cargos cuenta una vez).
 *   <li>{@code avance}: 0..100 calculado sobre {@code jrvsTotal}. Si {@code jrvsTotal == 0}, el
 *       service devuelve {@code 0.0} (sin division por cero, sin NaN).
 * </ul>
 *
 * <p>Una parroquia sin JRV asignados aparece con {@code jrvsTotal == 0} y {@code avance == 0.0}:
 * "no hay JRV" es un dato distinto de "no hay avance", y hiding esa fila haria que el panel
 * mintiera sobre la cobertura territorial.
 *
 * <p>La diferencia de nomenclatura con el DTO cantonal es deliberada y documentada: el campo se
 * llama {@code jrvsEscrutadas} por el contrato que ya consume el frontend, pero cuenta JRVs con
 * acta <b>no anulada</b> (cualquier estado: ENVIADA, EN_DISPUTA, CORREGIDA o APROBADA), que es la
 * misma regla del canton. No cuenta solo las APROBADAS.
 */
@RegisterForReflection
public record ParroquiaProgresoDto(
    Long parroquiaId, String parroquiaNombre, long jrvsEscrutadas, long jrvsTotal, double avance) {}
