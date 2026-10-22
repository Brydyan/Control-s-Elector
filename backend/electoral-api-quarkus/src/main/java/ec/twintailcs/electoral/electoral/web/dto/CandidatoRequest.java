package ec.twintailcs.electoral.electoral.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload para crear/actualizar un Candidato. Usado en POST/PUT /api/v1/configuracion/candidatos.
 *
 * <p>Mismas validaciones y mismos mensajes que el backend Spring.
 */
@RegisterForReflection
public record CandidatoRequest(
    @NotBlank(message = "El nombre del candidato es obligatorio") String nombre,
    @NotNull(message = "El orden en la papeleta es obligatorio") Integer orden,
    @NotNull(message = "El cargoId es obligatorio") Long cargoId,
    @NotNull(message = "El partidoId es obligatorio") Long partidoId) {}
