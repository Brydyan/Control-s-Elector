package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

/**
 * Respuesta de una activacion exitosa.
 *
 * @param publicId id publico del usuario activado
 * @param nombre nombre
 * @param apellido apellido
 * @param email correo de Control con el que inicia sesion
 * @param emailPersonal correo de contacto de la persona; null si la cuenta se creo sin correo
 * @param rol rol asignado
 */
@RegisterForReflection
public record ActivarUsuarioResponse(
    UUID publicId,
    String nombre,
    String apellido,
    String email,
    String emailPersonal,
    String rol) {}
