package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

/**
 * Representacion publica de una observacion marcada sobre una evidencia.
 *
 * <p>Se usa en {@link EvidenciaResponse} para exponer la lista de observaciones a los roles de
 * revision (COORDINATOR, MASTER, ADMIN).
 */
@RegisterForReflection
public record ObservacionDto(TipoObservacion tipo, Instant creadoEn) {}
