package ec.twintailcs.electoral.acta.web.dto;

import ec.twintailcs.electoral.acta.domain.EstadoActa;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cargo aplicable a una JRV, indicando si ya existe un acta activa (no ANULADA) registrada para ese
 * JRV + cargo.
 */
@Schema(description = "Cargo electoral con estado de registro de acta para una JRV")
public record CargoConEstadoDto(
    @Schema(description = "ID del cargo") Long id,
    @Schema(description = "Nombre del cargo", example = "Alcalde/sa") String nombre,
    @Schema(description = "Nivel geografico del cargo", example = "CANTON") String nivel,
    @Schema(description = "Indica si ya existe un acta activa para este cargo en la JRV")
        boolean registrado,
    @Schema(description = "Estado del acta activa (null si no registrado)") EstadoActa estadoActa,
    @Schema(description = "ID del acta activa (null si no registrado)") Long actaId) {

  public static CargoConEstadoDto sinRegistrar(Long id, String nombre, String nivel) {
    return new CargoConEstadoDto(id, nombre, nivel, false, null, null);
  }

  public static CargoConEstadoDto registrado(
      Long id, String nombre, String nivel, EstadoActa estadoActa, Long actaId) {
    return new CargoConEstadoDto(id, nombre, nivel, true, estadoActa, actaId);
  }
}
