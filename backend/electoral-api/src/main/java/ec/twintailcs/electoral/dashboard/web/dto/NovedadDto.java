package ec.twintailcs.electoral.dashboard.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Novedad para la bandeja {@code GET /api/v1/dashboard/novedades}. RF-DASH-06.
 *
 * <p>El campo {@code tipo} existe hoy siempre con el valor {@code "ACTA_EN_DISPUTA"} para que
 * añadir luego evidencias con observación (T-BE-16) sea aditivo — {@code "EVIDENCIA_CON_OBS"} — sin
 * romper el contrato del frontend. Ver {@code design.md} D3.
 *
 * <p>{@code motivo} está pensado para poblarse con el motivo de la disputa. Hoy está siempre en
 * {@code null}: la entidad {@code Acta} no tiene columna {@code motivo_disputa} (sólo aparece en el
 * doc de referencia {@code infra/sql/01-schema.sql}, no en el changelog Liquibase). Esto es un
 * discovery a cerrar en otro change — aquí dejamos el campo en el DTO para que añadir la fuente sea
 * aditivo. Ver {@code apply-progress.md}.
 *
 * <p>{@code actaPublicId} usa el UUID público de {@code Acta} (no el id interno Long) por
 * consistencia con el resto de la regla del proyecto: «DTOs exponen {@code publicId}». Jrv, Cargo,
 * Candidato y Partido aún no tienen {@code publicId} en la entidad, así que sus ids van como Long.
 */
@Schema(description = "Novedad de la bandeja del dashboard: acta en disputa pendiente de atencion")
public record NovedadDto(
    @Schema(description = "UUID publico del acta") UUID actaPublicId,
    @Schema(description = "Tipo de novedad", example = "ACTA_EN_DISPUTA") String tipo,
    @Schema(description = "ID de la JRV") Long jrvId,
    @Schema(description = "Numero de la JRV", example = "0023") String jrvNumero,
    @Schema(description = "ID del cargo") Long cargoId,
    @Schema(description = "Nombre del cargo", example = "Alcalde/sa") String cargoNombre,
    @Schema(description = "UUID publico del veedor que registro el acta") UUID observerPublicId,
    @Schema(description = "Nombre completo del veedor", example = "Juan Perez") String observerNombre,
    @Schema(description = "Motivo de la disputa (puede ser null)") String motivo,
    @Schema(description = "Minutos transcurridos desde el registro del acta", example = "35")
        long minutosTranscurridos) {}
