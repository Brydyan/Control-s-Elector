package ec.twintailcs.electoral.electoral.web.dto;

/** Respuesta publica de la API para un Partido. */
public record PartidoResponse(Long id, String nombre, String siglas, Integer numeroLista) {}
