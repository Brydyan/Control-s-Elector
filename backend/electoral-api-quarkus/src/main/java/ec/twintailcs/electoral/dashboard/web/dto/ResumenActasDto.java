package ec.twintailcs.electoral.dashboard.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Resumen global para {@code GET /api/v1/dashboard/resumen}. Conteos por estado, <b>excluyendo
 * ANULADA</b>: la regla «sin ANULADA» vive en la query, no aqui.
 *
 * <p>{@code totalActivas} es la suma de los cuatro estados activos. ANULADA queda deliberadamente
 * fuera para no contaminar la metrica con actas que ya no cuentan para el resultado final.
 */
@RegisterForReflection
public record ResumenActasDto(
    long enviadas, long enDisputa, long corregidas, long aprobadas, long totalActivas) {}
