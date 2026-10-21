package ec.twintailcs.electoral.electoral.web.dto;

import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload para crear/actualizar un Cargo. Usado en POST/PUT /api/v1/configuracion/cargos.
 *
 * <p>Mismas validaciones y mismos mensajes que el backend Spring.
 */
@RegisterForReflection
public record CargoRequest(
    @NotBlank(message = "El nombre del cargo es obligatorio") String nombre,
    @NotNull(message = "El nivel es obligatorio") NivelCargo nivel,
    @NotNull(message = "El numero de puestos es obligatorio")
        @Min(value = 1, message = "Debe haber al menos 1 puesto")
        Integer puestos) {}
