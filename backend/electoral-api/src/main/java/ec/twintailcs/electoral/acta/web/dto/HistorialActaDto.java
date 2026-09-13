package ec.twintailcs.electoral.acta.web.dto;

import java.time.Instant;

/**
 * Entrada de historial de un acta: cada acción crítica (registro, disputa, corrección, aprobación,
 * anulación) que quedó registrada en {@code audit_log}.
 */
public record HistorialActaDto(
    Long id,
    String accion,
    Instant timestamp,
    String registradoPorNombre,
    String registradoPorRol,
    String ipAddress,
    String detalle) {}
