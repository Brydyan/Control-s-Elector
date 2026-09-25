package ec.twintailcs.electoral.dashboard.web.dto;

import java.util.UUID;

/**
 * Novedad para la bandeja {@code GET /api/v1/dashboard/novedades}. RF-DASH-06.
 *
 * <p>El campo {@code tipo} existe hoy siempre con el valor {@code "ACTA_EN_DISPUTA"} para que
 * añadir luego evidencias con observación (T-BE-16) sea aditivo — {@code "EVIDENCIA_CON_OBS"} —
 * sin romper el contrato del frontend. Ver {@code design.md} D3.
 *
 * <p>{@code motivo} está pensado para poblarse con el motivo de la disputa. Hoy está siempre en
 * {@code null}: la entidad {@code Acta} no tiene columna {@code motivo_disputa} (sólo aparece en el
 * doc de referencia {@code infra/sql/01-schema.sql}, no en el changelog Liquibase). Esto es un
 * discovery a cerrar en otro change — aquí dejamos el campo en el DTO para que añadir la fuente
 * sea aditivo. Ver {@code apply-progress.md}.
 *
 * <p>{@code actaPublicId} usa el UUID público de {@code Acta} (no el id interno Long) por
 * consistencia con el resto de la regla del proyecto: «DTOs exponen {@code publicId}». Jrv, Cargo,
 * Candidato y Partido aún no tienen {@code publicId} en la entidad, así que sus ids van como Long.
 */
public record NovedadDto(
    UUID actaPublicId,
    String tipo,
    Long jrvId,
    String jrvNumero,
    Long cargoId,
    String cargoNombre,
    UUID observerPublicId,
    String observerNombre,
    String motivo,
    long minutosTranscurridos) {}
