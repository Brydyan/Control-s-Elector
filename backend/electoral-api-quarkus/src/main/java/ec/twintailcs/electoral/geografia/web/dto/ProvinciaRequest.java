package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para crear/actualizar una Provincia. Usado en POST/PUT /api/v1/master/provincias.
 *
 * <p>El codigo es opcional (una provincia puede no tener codigo), pero cuando viene es unico en
 * toda la tabla: lo garantiza el indice {@code ix_provincia_codigo}.
 */
@RegisterForReflection
public record ProvinciaRequest(
    @NotBlank(message = "El nombre de la provincia es obligatorio")
        @Size(max = 100, message = "El nombre de la provincia no puede pasar de 100 caracteres")
        String nombre,
    @Size(max = 10, message = "El codigo de la provincia no puede pasar de 10 caracteres")
        String codigo) {}
