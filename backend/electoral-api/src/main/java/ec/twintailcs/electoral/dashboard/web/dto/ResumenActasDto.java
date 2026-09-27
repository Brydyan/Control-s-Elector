package ec.twintailcs.electoral.dashboard.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resumen global para {@code GET /api/v1/dashboard/resumen}. Conteos por estado, **excluyendo
 * ANULADA** — ver {@code design.md} D1: la regla «sin ANULADA» vive en la query, no aquí.
 *
 * <p>{@code totalActivas} es la suma de los cuatro estados activos. ANULADA queda deliberadamente
 * fuera para no contaminar la métrica con actas que ya no cuentan para el resultado final.
 */
@Schema(description = "Conteo de actas activas por estado (excluye ANULADA)")
public record ResumenActasDto(
    @Schema(description = "Actas en estado ENVIADA") long enviadas,
    @Schema(description = "Actas en estado EN_DISPUTA") long enDisputa,
    @Schema(description = "Actas en estado CORREGIDA") long corregidas,
    @Schema(description = "Actas en estado APROBADA") long aprobadas,
    @Schema(description = "Suma de los cuatro estados activos") long totalActivas) {}
