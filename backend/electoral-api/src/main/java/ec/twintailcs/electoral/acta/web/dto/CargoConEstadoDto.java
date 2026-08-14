package ec.twintailcs.electoral.acta.web.dto;

import ec.twintailcs.electoral.acta.domain.EstadoActa;

/**
 * Cargo aplicable a una JRV, indicando si ya existe un acta activa
 * (no ANULADA) registrada para ese JRV + cargo.
 */
public record CargoConEstadoDto(
        Long id,
        String nombre,
        String nivel,
        boolean registrado,
        EstadoActa estadoActa,
        Long actaId
) {

    public static CargoConEstadoDto sinRegistrar(Long id, String nombre, String nivel) {
        return new CargoConEstadoDto(id, nombre, nivel, false, null, null);
    }

    public static CargoConEstadoDto registrado(Long id, String nombre, String nivel,
                                                EstadoActa estadoActa, Long actaId) {
        return new CargoConEstadoDto(id, nombre, nivel, true, estadoActa, actaId);
    }
}
