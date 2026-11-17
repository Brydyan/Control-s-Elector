package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload para crear/actualizar una Parroquia. Usado en POST/PUT /api/v1/master/parroquias.
 *
 * <p>La parroquia cuelga de un canton: {@code cantonId} es obligatorio y tiene que existir. El
 * nombre tiene que ser unico dentro de ese canton.
 */
@RegisterForReflection
public record ParroquiaRequest(
    @NotBlank(message = "El nombre de la parroquia es obligatorio")
        @Size(max = 100, message = "El nombre de la parroquia no puede pasar de 100 caracteres")
        String nombre,
    @NotNull(message = "El indicador de parroquia urbana es obligatorio") Boolean esUrbana,
    @NotNull(message = "El canton de la parroquia es obligatorio") Long cantonId) {}
