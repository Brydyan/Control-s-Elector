package ec.twintailcs.electoral.electoral.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta publica de la API para un Partido. */
@Schema(description = "Partido politico o movimiento electoral")
public record PartidoResponse(
    @Schema(description = "ID del partido", example = "1") Long id,
    @Schema(description = "Nombre del partido", example = "Movimiento Alianza PAIS") String nombre,
    @Schema(description = "Siglas del partido", example = "AP") String siglas,
    @Schema(description = "Numero de lista en el tarjeton", example = "35") Integer numeroLista) {}
