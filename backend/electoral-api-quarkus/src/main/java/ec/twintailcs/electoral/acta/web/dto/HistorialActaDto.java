package ec.twintailcs.electoral.acta.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

/**
 * Entrada de historial de un acta: cada accion critica (registro, disputa, correccion, aprobacion,
 * anulacion) que quedo registrada en {@code audit_log}.
 */
@RegisterForReflection
public record HistorialActaDto(
    Long id,
    String accion,
    Instant timestamp,
    String registradoPorNombre,
    String registradoPorRol,
    String ipAddress,
    String detalle) {}
