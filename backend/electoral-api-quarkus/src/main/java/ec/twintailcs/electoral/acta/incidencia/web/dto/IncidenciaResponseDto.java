package ec.twintailcs.electoral.acta.incidencia.web.dto;

import ec.twintailcs.electoral.acta.incidencia.domain.TipoIncidenciaJrv;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

/** Incidencia registrada en una JRV. */
@RegisterForReflection
public record IncidenciaResponseDto(
    Long id,
    Long jrvId,
    TipoIncidenciaJrv tipo,
    String descripcion,
    String evidenciaUrl,
    Long reportadaPorId,
    Instant fechaRegistro) {}
