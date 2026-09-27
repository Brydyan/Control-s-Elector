package ec.twintailcs.electoral.dashboard.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Progreso de registro de actas por canton")
public record ProgresoCantonDto(
    @Schema(description = "ID del canton") Long cantonId,
    @Schema(description = "Nombre del canton", example = "Santa Elena") String cantonNombre,
    @Schema(description = "Total de JRVs del canton") long totalJrvs,
    @Schema(description = "JRVs con al menos un acta no anulada") long jrvsConActa,
    @Schema(description = "Porcentaje de avance (0.0 a 100.0)", example = "67.5") double porcentaje) {}
