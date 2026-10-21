package ec.twintailcs.electoral.dashboard.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

/**
 * Novedad para la bandeja {@code GET /api/v1/dashboard/novedades}.
 *
 * <p>El campo {@code tipo} existe hoy siempre con el valor {@code "ACTA_EN_DISPUTA"} para que
 * anadir luego evidencias con observacion sea aditivo ({@code "EVIDENCIA_CON_OBS"}) sin romper el
 * contrato del frontend.
 *
 * <p>{@code motivo} esta hoy siempre en {@code null}: la entidad {@code Acta} no tiene columna
 * {@code motivo_disputa}. El campo queda en el DTO para que anadir la fuente sea aditivo.
 *
 * <p>{@code actaPublicId} usa el UUID publico del acta (no el id interno) por consistencia con el
 * resto de la regla del proyecto: «los DTOs exponen publicId». Jrv, Cargo, Candidato y Partido aun
 * no tienen {@code publicId}, asi que sus ids van como Long.
 */
@RegisterForReflection
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
