package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

/**
 * Body para {@code PATCH /api/v1/evidencias/{id}/estado}. Solo se acepta APROBADA o RECHAZADA (no
 * se vuelve a EN_REVISION desde el frontend; eso es un caso raro manejado por un endpoint master
 * separado).
 */
@RegisterForReflection
public record CambiarEstadoRequest(
    @NotNull(message = "El estado es obligatorio") EstadoEvidencia estado) {}
