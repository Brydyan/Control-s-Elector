package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body para {@code PATCH /api/v1/evidencias/{id}/observaciones}.
 *
 * <p>Lista de categorías a marcar. Vacía = borrar todas. Máximo 2 (validado en servicio).
 */
public record MarcarObservacionesRequest(
    @Size(max = 10, message = "lista no debe exceder 10 elementos (servicio valida RF-OBS-02)")
        List<TipoObservacion> tipos) {}
