package ec.twintailcs.electoral.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Detalle de una sesion activa de usuario")
public record SesionResponseDto(
    @Schema(hidden = true) @JsonIgnore Long id,
    @Schema(description = "Identificador opaco de la sesion", example = "a1b2c3d4...")
        String publicId,
    @Schema(description = "ID interno del usuario propietario") Long usuarioId,
    @Schema(description = "Email del usuario", example = "obs.juan.perez@control.ec")
        String usuarioEmail,
    @Schema(description = "IP desde donde se origino la sesion", example = "192.168.1.100")
        String ipCliente,
    @Schema(description = "User-Agent del cliente") String userAgent,
    @Schema(description = "Timestamp de creacion (UTC)") Instant creadoEn,
    @Schema(description = "Ultimo uso del refresh token (UTC)") Instant ultimoUsoEn,
    @Schema(description = "Expiracion del refresh token (UTC)") Instant expiraEn) {}
