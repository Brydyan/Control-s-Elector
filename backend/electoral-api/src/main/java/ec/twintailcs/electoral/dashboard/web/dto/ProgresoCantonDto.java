package ec.twintailcs.electoral.dashboard.web.dto;

/**
 * Línea de progreso por cantón, en {@code GET /api/v1/dashboard/progreso}.
 *
 * <p>RF-DASH-05:
 *
 * <ul>
 *   <li>{@code totalJrvs}: JRVs totales del cantón.
 *   <li>{@code jrvsConActa}: JRVs con al menos un acta no anulada (DISTINCT — una JRV con tres
 *       cargos cuenta una vez).
 *   <li>{@code porcentaje}: 0..100 calculado sobre {@code totalJrvs}. Si {@code totalJrvs == 0}, el
 *       service devuelve {@code 0.0} (sin división por cero, sin NaN).
 * </ul>
 */
public record ProgresoCantonDto(
    Long cantonId, String cantonNombre, long totalJrvs, long jrvsConActa, double porcentaje) {}
