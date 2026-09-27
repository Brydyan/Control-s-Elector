package ec.twintailcs.electoral.acta.web.dto;

import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de un acta para vistas de COORDINATOR y OBSERVER.
 *
 * <p>Extiende {@link ActaResponseDto} con colecciones que el listado plano no carga (per tasks.md
 * "que NO hacer": no incluir colecciones en el listado para evitar N+1).
 */
@Schema(description = "Detalle completo de un acta electoral con votos por candidato y evidencia")
public record ActaDetalleCompletoDto(
    // campos base heredados de ActaResponseDto
    @Schema(description = "ID interno del acta") Long id,
    @Schema(description = "ID de la JRV") Long jrvId,
    @Schema(description = "ID del cargo electoral") Long cargoId,
    @Schema(description = "ID del usuario que registro el acta") Long registradaPorId,
    @Schema(description = "Estado actual del acta") EstadoActa estado,
    @Schema(description = "Total de sufragantes en la JRV") Integer totalSufragantes,
    @Schema(description = "Total de votos en blanco") Integer votosBlancos,
    @Schema(description = "Total de votos nulos") Integer votosNulos,
    @Schema(description = "Fecha y hora de registro") Instant fechaRegistro,
    @Schema(description = "Motivo de anulacion (solo si estado=ANULADA)") String motivoAnulacion,
    @Schema(description = "Nombre del cargo electoral", example = "Alcalde/sa") String nombreCargo,
    @Schema(description = "Numero de la JRV", example = "0023") String numeroJrv,
    @Schema(description = "Suma de votos de todos los candidatos") Long totalVotosCandidatos,
    // campos adicionales del detalle completo
    @Schema(description = "Votos por candidato") List<ActaDetalleDto> detalles,
    @Schema(description = "UUID publico de la evidencia fotografica") UUID evidenciaPublicId,
    @Schema(description = "URL prefirmada de la evidencia (S3)") String urlEvidencia,
    @Schema(description = "Motivo de la disputa (si estado=EN_DISPUTA)") String motivoDisputa) {}
