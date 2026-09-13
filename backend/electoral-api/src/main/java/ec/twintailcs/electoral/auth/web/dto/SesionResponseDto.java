package ec.twintailcs.electoral.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record SesionResponseDto(
    @JsonIgnore Long id,
    String publicId,
    Long usuarioId,
    String usuarioEmail,
    String ipCliente,
    String userAgent,
    Instant creadoEn,
    Instant ultimoUsoEn,
    Instant expiraEn) {}
