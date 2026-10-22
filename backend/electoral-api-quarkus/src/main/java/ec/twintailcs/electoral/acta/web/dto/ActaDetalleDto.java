package ec.twintailcs.electoral.acta.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Votos obtenidos por un candidato en el acta. */
@RegisterForReflection
public record ActaDetalleDto(
    @NotNull(message = "candidatoId es obligatorio") Long candidatoId,
    @NotNull(message = "votos es obligatorio")
        @Min(value = 0, message = "votos no puede ser negativo")
        Integer votos) {}
