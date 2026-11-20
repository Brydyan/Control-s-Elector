package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload para crear/actualizar un Recinto.
 *
 * <p>Un Recinto pertenece a una Parroquia y es gestionado por un Coordinador. El nombre debe ser
 * único dentro de la parroquia.
 */
@RegisterForReflection
public record RecintoRequest(
    @NotBlank(message = "El nombre del recinto es obligatorio")
        @Size(min = 1, max = 255, message = "El nombre debe tener entre 1 y 255 caracteres")
        String nombre,
    @NotNull(message = "El ID de la parroquia es obligatorio") Long parroquiaId,
    Long coordinatorId,
    @Size(max = 20, message = "El estado no puede exceder 20 caracteres") String estado) {}
