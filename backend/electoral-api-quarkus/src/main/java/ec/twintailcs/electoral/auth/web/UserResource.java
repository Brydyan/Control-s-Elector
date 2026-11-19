package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.AuthException;
import ec.twintailcs.electoral.auth.application.UserProfileException;
import ec.twintailcs.electoral.auth.application.UserProfileService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.auth.web.dto.UpdatePersonalEmailRequest;
import ec.twintailcs.electoral.auth.web.dto.UserProfileResponseDto;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * Endpoints de perfil de usuario. Acceso solo para el usuario autenticado.
 *
 * <p>Permitir que el usuario acceda a su propio perfil, actualice su email personal y cambie su
 * contraseña.
 */
@ApplicationScoped
@Path("/api/v1/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

  @Inject CurrentUser currentUser;
  @Inject UserProfileService userProfileService;
  @Inject UsuarioMapper mapper;

  /**
   * Obtener perfil del usuario actual.
   *
   * <p>Retorna datos inmutables (nombre, apellido, email control, rol) y datos mutables (email
   * personal, último cambio de contraseña).
   */
  @GET
  @Path("/profile")
  public Response getProfile() {
    String email = currentUser.email();
    if (email == null) {
      throw new AuthException("No autenticado");
    }

    Usuario usuario = Usuario.find("email", email).firstResult();
    if (usuario == null) {
      throw new AuthException("No autenticado");
    }

    UserProfileResponseDto response =
        new UserProfileResponseDto(
            usuario.getPublicId(),
            usuario.getNombre(),
            usuario.getApellido(),
            usuario.getEmail(),
            usuario.getEmailPersonal(),
            usuario.getRolNombre(),
            usuario.getEstadoUsuario(),
            null); // lastPasswordChangeAt: TODO si se necesita en el futuro

    return Response.ok(response).build();
  }

  /**
   * Actualizar email personal del usuario actual.
   *
   * <p>Requiere confirmación de identidad (password actual). Email personal es nullable (puede
   * dejarse vacío).
   */
  @PUT
  @Path("/update-personal-email")
  public Response updatePersonalEmail(@Valid UpdatePersonalEmailRequest request) {
    String email = currentUser.email();
    if (email == null) {
      throw new AuthException("No autenticado");
    }

    Usuario usuario = Usuario.find("email", email).firstResult();
    if (usuario == null) {
      throw new AuthException("No autenticado");
    }

    try {
      userProfileService.updatePersonalEmail(
          usuario.getPublicId(), request.emailPersonal(), request.password());
    } catch (UserProfileException e) {
      return Response.status(e.getStatusCode())
          .entity(Map.of("error", e.getMessage(), "code", e.getErrorCode()))
          .build();
    }

    return Response.ok(
            Map.of(
                "message", "Personal email updated successfully",
                "emailPersonal", request.emailPersonal() == null ? "" : request.emailPersonal()))
        .build();
  }
}
