package ec.twintailcs.electoral.electoral.web.dto;

import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta publica de la API para un Cargo. */
@Schema(description = "Cargo electoral (Prefecto, Alcalde, Concejal, etc.)")
public record CargoResponse(
    @Schema(description = "ID del cargo", example = "1") Long id,
    @Schema(description = "Nombre del cargo", example = "Prefecto/a Provincial") String nombre,
    @Schema(description = "Nivel geografico: PROVINCIA, CANTON, PARROQUIA, NACIONAL")
        NivelCargo nivel,
    @Schema(description = "Numero de puestos a elegir", example = "1") Integer puestos) {}
