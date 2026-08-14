package ec.twintailcs.electoral.acta.web.dto;

/**
 * JRV con el resumen de cargos pendientes/registrados por el OBSERVER.
 * Usado para que el OBSERVER elija libremente en que JRV registrar un acta.
 */
public record JrvConEstadoDto(
        Long id,
        String numero,
        String parroquia,
        int cargosPendientes,
        int cargosRegistrados
) {
}
