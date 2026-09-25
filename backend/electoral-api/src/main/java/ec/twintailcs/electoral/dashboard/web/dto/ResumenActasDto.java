package ec.twintailcs.electoral.dashboard.web.dto;

/**
 * Resumen global para {@code GET /api/v1/dashboard/resumen}. Conteos por estado, **excluyendo
 * ANULADA** — ver {@code design.md} D1: la regla «sin ANULADA» vive en la query, no aquí.
 *
 * <p>{@code totalActivas} es la suma de los cuatro estados activos. ANULADA queda deliberadamente
 * fuera para no contaminar la métrica con actas que ya no cuentan para el resultado final.
 */
public record ResumenActasDto(
    long enviadas, long enDisputa, long corregidas, long aprobadas, long totalActivas) {}
