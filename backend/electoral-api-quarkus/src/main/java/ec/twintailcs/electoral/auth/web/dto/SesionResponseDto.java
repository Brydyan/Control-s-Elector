package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

/**
 * Detalle de una sesion activa de usuario.
 *
 * <p>El {@code publicId} es el identificador opaco de la sesion (el {@code sesion_id} de la tabla).
 * El id interno de la base no se expone: el backend Spring lo marca con {@code @JsonIgnore} y
 * tampoco lo serializa, asi que aca directamente no existe.
 */
@RegisterForReflection
public record SesionResponseDto(
    String publicId,
    Long usuarioId,
    String usuarioEmail,
    String ipCliente,
    String userAgent,
    Instant creadoEn,
    Instant ultimoUsoEn,
    Instant expiraEn) {}
