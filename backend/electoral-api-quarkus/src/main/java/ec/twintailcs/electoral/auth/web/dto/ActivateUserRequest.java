package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

/**
 * Pedido de activacion de cuenta.
 *
 * @param token token de activacion recibido por email
 * @param password contrasena que eligio el usuario
 * @param passwordConfirm la misma contrasena, para que el backend no dependa solo del chequeo del
 *     formulario
 */
@RegisterForReflection
public record ActivateUserRequest(
    @NotBlank String token, @NotBlank String password, @NotBlank String passwordConfirm) {}
