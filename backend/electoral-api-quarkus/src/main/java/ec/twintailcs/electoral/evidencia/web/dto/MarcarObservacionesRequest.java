package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body para {@code PATCH /api/v1/evidencias/{id}/observaciones}.
 *
 * <p>Lista de categorias a marcar. Vacia = borrar todas. Maximo 2 (validado en el caso de uso).
 */
@RegisterForReflection
public record MarcarObservacionesRequest(
    @Size(max = 10, message = "lista no debe exceder 10 elementos (servicio valida RF-OBS-02)")
        List<TipoObservacion> tipos) {}
