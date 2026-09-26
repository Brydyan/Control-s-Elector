package ec.twintailcs.electoral.incidencia.web.dto;

import ec.twintailcs.electoral.incidencia.domain.TipoIncidenciaJrv;
import java.time.Instant;

public record IncidenciaResponseDto(
    Long id,
    Long jrvId,
    TipoIncidenciaJrv tipo,
    String descripcion,
    String evidenciaUrl,
    Long reportadaPorId,
    Instant fechaRegistro) {}
