package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import jakarta.validation.constraints.NotNull;

/**
 * Body para PATCH /evidencias/{id}/estado.
 * Solo se acepta APROBADA o RECHAZADA (no se vuelve a EN_REVISION desde el FE;
 * eso es un caso raro manejado por un endpoint master separado).
 */
public record CambiarEstadoRequest(@NotNull EstadoEvidencia estado) {}
