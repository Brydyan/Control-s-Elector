package ec.twintailcs.electoral.acta.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JRV con el resumen de cargos pendientes/registrados (global por JRV; no filtra por OBSERVER).
 * Usado para que el OBSERVER elija libremente en que JRV registrar un acta.
 */
@Schema(description = "JRV con resumen de cargos pendientes y registrados")
public record JrvConEstadoDto(
    @Schema(description = "ID de la JRV") Long id,
    @Schema(description = "Numero de la JRV", example = "0023") String numero,
    @Schema(description = "Nombre de la parroquia") String parroquia,
    @Schema(description = "Cargos sin acta activa") int cargosPendientes,
    @Schema(description = "Cargos con acta activa (no ANULADA)") int cargosRegistrados) {}
