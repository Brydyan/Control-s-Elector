package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload para crear/actualizar un Canton. Usado en POST/PUT /api/v1/master/cantones.
 *
 * <p>El canton cuelga de una provincia: {@code provinciaId} es obligatorio y tiene que existir.
 */
@RegisterForReflection
public record CantonRequest(
    @NotBlank(message = "El nombre del canton es obligatorio")
        @Size(max = 100, message = "El nombre del canton no puede pasar de 100 caracteres")
        String nombre,
    @Size(max = 10, message = "El codigo del canton no puede pasar de 10 caracteres") String codigo,
    @NotNull(message = "La provincia del canton es obligatoria") Long provinciaId) {}
