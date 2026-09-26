package ec.twintailcs.electoral.incidencia.web.dto;

import ec.twintailcs.electoral.incidencia.domain.TipoIncidenciaJrv;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidenciaRequestDto(
    @NotNull(message = "jrvId es obligatorio") Long jrvId,
    @NotNull(message = "tipo es obligatorio") TipoIncidenciaJrv tipo,
    @NotBlank(message = "descripcion es obligatoria")
        @Size(max = 5000, message = "descripcion demasiado larga (máx 5000 chars)")
        String descripcion,
    @Size(max = 500, message = "evidenciaUrl demasiado larga (máx 500 chars)") String evidenciaUrl) {}
