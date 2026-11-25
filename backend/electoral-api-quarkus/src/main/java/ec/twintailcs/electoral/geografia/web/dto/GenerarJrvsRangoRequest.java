package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload para generar JRVs numeradas en un recinto. */
@RegisterForReflection
public record GenerarJrvsRangoRequest(
    @Min(value = 1, message = "El inicio del rango debe ser mayor que cero") int desde,
    @Min(value = 1, message = "El fin del rango debe ser mayor que cero") int hasta,
    @Size(max = 8, message = "El sufijo no puede exceder 8 caracteres")
        @Pattern(
            regexp = "[A-Za-z0-9]*",
            message = "El sufijo solo puede contener letras y números")
        String sufijoGenero,
    @Min(value = 1, message = "Los electores por mesa deben ser mayores que cero")
        int electoresPorMesa) {}
