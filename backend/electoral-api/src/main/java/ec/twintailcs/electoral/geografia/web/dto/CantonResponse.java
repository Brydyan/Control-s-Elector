package ec.twintailcs.electoral.geografia.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Canton de la Provincia de Santa Elena")
public record CantonResponse(
    @Schema(description = "ID interno del canton", example = "1") Long id,
    @Schema(description = "Nombre del canton", example = "Santa Elena") String nombre,
    @Schema(description = "Codigo INEC del canton", example = "2401") String codigo) {}
