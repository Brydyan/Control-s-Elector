package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de invitacion: los datos de la persona a invitar.
 *
 * @param nombre nombre
 * @param apellido apellido
 * @param emailPersonal correo de contacto: a donde se manda la invitacion. Es distinto del login
 *     (que el backend genera a partir de nombre, apellido y rol) porque la persona invited puede no
 *     tener acceso al correo institucional
 * @param rolNombre rol a asignar
 */
@RegisterForReflection
public record InviteUserRequest(
    @NotBlank(message = "El nombre es requerido")
        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres")
        String nombre,
    @NotBlank(message = "El apellido es requerido")
        @Size(max = 100, message = "El apellido no puede pasar de 100 caracteres")
        String apellido,
    @NotBlank(message = "El email es requerido")
        @Email(message = "El email debe ser válido")
        @Size(max = 254, message = "El email no puede pasar de 254 caracteres")
        String emailPersonal,
    @NotBlank(message = "El rol es requerido") String rolNombre) {}
