package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload para crear/actualizar una JRV. Usado en POST/PUT /api/v1/master/jrvs.
 *
 * <p>La JRV cuelga de una parroquia: {@code parroquiaId} es obligatorio y tiene que existir. El
 * numero de JRV es unico en el sistema y el total de electores tiene que ser mayor que cero.
 */
@RegisterForReflection
public record JrvRequest(
    @NotBlank(message = "El numero de la JRV es obligatorio")
        @Size(max = 20, message = "El numero de la JRV no puede pasar de 20 caracteres")
        String numero,
    @NotBlank(message = "El recinto de la JRV es obligatorio")
        @Size(max = 200, message = "El recinto de la JRV no puede pasar de 200 caracteres")
        String recinto,
    @NotNull(message = "El total de electores de la JRV es obligatorio")
        @Min(value = 1, message = "La JRV tiene que tener al menos 1 elector")
        Integer totalElectores,
    @NotNull(message = "La parroquia de la JRV es obligatoria") Long parroquiaId) {}
