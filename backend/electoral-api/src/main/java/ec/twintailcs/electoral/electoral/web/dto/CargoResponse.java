package ec.twintailcs.electoral.electoral.web.dto;

import ec.twintailcs.electoral.electoral.domain.NivelCargo;

/** Respuesta publica de la API para un Cargo. */
public record CargoResponse(Long id, String nombre, NivelCargo nivel, Integer puestos) {}
