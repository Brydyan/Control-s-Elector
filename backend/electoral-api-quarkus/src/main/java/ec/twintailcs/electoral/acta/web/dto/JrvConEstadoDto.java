package ec.twintailcs.electoral.acta.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * JRV con el resumen de cargos pendientes/registrados (global por JRV; no filtra por OBSERVER).
 *
 * <p>Se usa para que el OBSERVER elija libremente en que JRV registrar un acta.
 */
@RegisterForReflection
public record JrvConEstadoDto(
    Long id, String numero, String parroquia, int cargosPendientes, int cargosRegistrados) {}
