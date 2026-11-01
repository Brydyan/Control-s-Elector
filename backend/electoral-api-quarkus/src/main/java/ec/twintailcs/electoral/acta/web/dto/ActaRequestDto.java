package ec.twintailcs.electoral.acta.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Datos para registrar o corregir un acta electoral.
 *
 * <p>Mismo contrato que el {@code ActaRequestDto} del backend Spring: mismos campos, mismas
 * validaciones y mismos mensajes (el frontend y la suite de contrato los afirman).
 */
@RegisterForReflection
public record ActaRequestDto(
    @NotNull(message = "jrvId es obligatorio") Long jrvId,
    @NotNull(message = "cargoId es obligatorio") Long cargoId,
    @NotNull(message = "totalSufragantes es obligatorio")
        @Min(value = 0, message = "totalSufragantes no puede ser negativo")
        Integer totalSufragantes,
    @NotNull(message = "votosBlancos es obligatorio")
        @Min(value = 0, message = "votosBlancos no puede ser negativo")
        Integer votosBlancos,
    @NotNull(message = "votosNulos es obligatorio")
        @Min(value = 0, message = "votosNulos no puede ser negativo")
        Integer votosNulos,
    @NotEmpty(message = "Debe haber al menos un candidato") List<@Valid ActaDetalleDto> detalles) {}
