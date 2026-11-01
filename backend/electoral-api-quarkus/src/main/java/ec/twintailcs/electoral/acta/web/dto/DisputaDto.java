package ec.twintailcs.electoral.acta.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

/** Datos para marcar un acta en disputa. */
@RegisterForReflection
public record DisputaDto(@NotBlank(message = "El motivo es obligatorio") String motivo) {}
