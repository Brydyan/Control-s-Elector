package ec.twintailcs.electoral.electoral.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Payload para crear/actualizar un Partido. Usado en POST/PUT /api/v1/configuracion/partidos. */
public record PartidoRequest(
    @NotBlank(message = "El nombre del partido es obligatorio") String nombre,
    @NotBlank(message = "Las siglas son obligatorias") String siglas,
    @NotNull(message = "El numero de lista es obligatorio") Integer numeroLista) {}
