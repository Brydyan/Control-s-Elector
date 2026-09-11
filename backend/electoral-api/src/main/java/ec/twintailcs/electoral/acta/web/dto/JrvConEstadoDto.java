package ec.twintailcs.electoral.acta.web.dto;

/**
 * JRV con el resumen de cargos pendientes/registrados (global por JRV; no filtra por OBSERVER).
 * Usado para que el OBSERVER elija libremente en qué JRV registrar un acta.
 */
public record JrvConEstadoDto(
    Long id, String numero, String parroquia, int cargosPendientes, int cargosRegistrados) {}
