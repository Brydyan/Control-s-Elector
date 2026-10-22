package ec.twintailcs.electoral.acta.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

/** Datos de anulacion de un acta APROBADA (solo MASTER). */
@RegisterForReflection
public record AnulacionDto(
    @NotBlank(message = "El motivo de anulacion es obligatorio") String motivo) {}
