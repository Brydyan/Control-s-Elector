package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.UUID;

/** Respuesta GET /api/v1/user/profile: datos del perfil del usuario actual. */
@RegisterForReflection
public record UserProfileResponseDto(
    UUID id,
    String nombre,
    String apellido,
    String emailControl,
    String emailPersonal,
    String rol,
    String estado,
    Instant lastPasswordChangeAt) {}
