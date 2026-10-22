package ec.twintailcs.electoral.electoral.web.dto;

import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** Respuesta publica de la API para un Cargo. */
@RegisterForReflection
public record CargoResponse(Long id, String nombre, NivelCargo nivel, Integer puestos) {}
