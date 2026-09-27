package ec.twintailcs.electoral.incidencia.web.dto;

import ec.twintailcs.electoral.incidencia.domain.TipoIncidenciaJrv;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Incidencia registrada en una JRV")
public record IncidenciaResponseDto(
    @Schema(description = "ID de la incidencia") Long id,
    @Schema(description = "ID de la JRV afectada") Long jrvId,
    @Schema(description = "Tipo de incidencia") TipoIncidenciaJrv tipo,
    @Schema(description = "Descripcion de la incidencia") String descripcion,
    @Schema(description = "URL de evidencia adjunta (opcional)") String evidenciaUrl,
    @Schema(description = "ID del usuario que reporto la incidencia") Long reportadaPorId,
    @Schema(description = "Fecha y hora de registro") Instant fechaRegistro) {}
