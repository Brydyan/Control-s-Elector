package ec.twintailcs.electoral.acta.incidencia.web.dto;

import ec.twintailcs.electoral.acta.incidencia.domain.TipoIncidenciaJrv;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos para registrar una incidencia en una JRV. */
@RegisterForReflection
public record IncidenciaRequestDto(
    @NotNull(message = "jrvId es obligatorio") Long jrvId,
    @NotNull(message = "tipo es obligatorio") TipoIncidenciaJrv tipo,
    @NotBlank(message = "descripcion es obligatoria")
        @Size(max = 5000, message = "descripcion demasiado larga (max 5000 chars)")
        String descripcion,
    @Size(max = 500, message = "evidenciaUrl demasiado larga (max 500 chars)")
        String evidenciaUrl) {}
