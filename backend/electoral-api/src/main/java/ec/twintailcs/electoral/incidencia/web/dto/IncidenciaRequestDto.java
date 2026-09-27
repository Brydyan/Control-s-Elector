package ec.twintailcs.electoral.incidencia.web.dto;

import ec.twintailcs.electoral.incidencia.domain.TipoIncidenciaJrv;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para registrar una incidencia en una JRV")
public record IncidenciaRequestDto(
    @Schema(description = "ID de la JRV afectada") @NotNull(message = "jrvId es obligatorio")
        Long jrvId,
    @Schema(description = "Tipo de incidencia") @NotNull(message = "tipo es obligatorio")
        TipoIncidenciaJrv tipo,
    @Schema(description = "Descripcion de la incidencia (max 5000 chars)")
        @NotBlank(message = "descripcion es obligatoria")
        @Size(max = 5000, message = "descripcion demasiado larga (max 5000 chars)")
        String descripcion,
    @Schema(description = "URL de evidencia adjunta (opcional, max 500 chars)")
        @Size(max = 500, message = "evidenciaUrl demasiado larga (max 500 chars)")
        String evidenciaUrl) {}
