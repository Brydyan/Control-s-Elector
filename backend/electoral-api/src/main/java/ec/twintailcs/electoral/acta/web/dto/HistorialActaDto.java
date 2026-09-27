package ec.twintailcs.electoral.acta.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Entrada de historial de un acta: cada accion critica (registro, disputa, correccion, aprobacion,
 * anulacion) que quedo registrada en {@code audit_log}.
 */
@Schema(description = "Entrada de historial de transiciones del acta (desde audit_log)")
public record HistorialActaDto(
    @Schema(description = "ID de la entrada de audit_log") Long id,
    @Schema(description = "Accion registrada", example = "DISPUTA") String accion,
    @Schema(description = "Fecha y hora de la accion") Instant timestamp,
    @Schema(description = "Nombre completo del usuario que realizo la accion")
        String registradoPorNombre,
    @Schema(description = "Rol del usuario", example = "COORDINATOR") String registradoPorRol,
    @Schema(description = "Direccion IP del cliente") String ipAddress,
    @Schema(description = "Detalle adicional de la accion") String detalle) {}
