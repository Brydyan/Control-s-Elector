package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

/**
 * Respuesta de una activacion exitosa.
 *
 * @param publicId id publico del usuario activado
 * @param nombre nombre
 * @param apellido apellido
 * @param email email de la cuenta
 * @param rol rol asignado
 */
@RegisterForReflection
public record ActivarUsuarioResponse(
    UUID publicId, String nombre, String apellido, String email, String rol) {}
