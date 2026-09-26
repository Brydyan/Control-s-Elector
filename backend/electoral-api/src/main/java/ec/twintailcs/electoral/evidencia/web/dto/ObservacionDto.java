package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import java.time.Instant;

/**
 * Representación pública de una observación marcada sobre una evidencia.
 *
 * <p>Usada en {@link EvidenciaResponse} para exponer la lista de observaciones a roles de revisión
 * (COORDINATOR, MASTER, ADMIN).
 */
public record ObservacionDto(TipoObservacion tipo, Instant creadoEn) {}
