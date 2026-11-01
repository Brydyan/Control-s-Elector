package ec.twintailcs.electoral.dashboard.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Linea de progreso por canton, en {@code GET /api/v1/dashboard/progreso}.
 *
 * <ul>
 *   <li>{@code totalJrvs}: JRVs totales del canton.
 *   <li>{@code jrvsConActa}: JRVs con al menos un acta no anulada (DISTINCT: una JRV con tres
 *       cargos cuenta una vez).
 *   <li>{@code porcentaje}: 0..100 calculado sobre {@code totalJrvs}. Si {@code totalJrvs == 0}, el
 *       service devuelve {@code 0.0} (sin division por cero, sin NaN).
 * </ul>
 */
@RegisterForReflection
public record ProgresoCantonDto(
    Long cantonId, String cantonNombre, long totalJrvs, long jrvsConActa, double porcentaje) {}
