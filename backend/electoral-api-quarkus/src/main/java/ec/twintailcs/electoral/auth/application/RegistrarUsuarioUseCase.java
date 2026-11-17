package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioInvalidoException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Alta de un usuario con rol, para que un MASTER lo registre desde el panel de administracion.
 *
 * <p>Port exacto del {@code RegistrarUsuarioUseCase} del backend Spring: el email se genera a
 * partir de nombre, apellido y rol, la password se hashea con BCrypt y el usuario queda habilitado.
 * No es auto-registro: el endpoint exige permisos elevados.
 */
@ApplicationScoped
// El binding a nivel de CLASE es lo que hace que ArC seleccione el interceptor para esta clase
// (el binding a nivel de metodo no lo selecciona). El valor es el default: el metodo lo
// sobreescribe.
public class RegistrarUsuarioUseCase {

  @Inject PasswordHasher hasher;

  @Inject EmailGenerator emailGenerator;

  @Transactional
  // El guard vive en el caso de uso porque es donde el interceptor CDI puede correr: Quarkus REST
  // invoca los metodos del recurso directamente y no les aplica interceptores. En Spring el
  // @PreAuthorize esta en el controller, pero el orden que importa (validacion antes que
  // autorizacion) se pierde si la autorizacion se evalua en un filtro de request.
  public Usuario execute(String nombre, String apellido, String passwordPlano, String rolNombre) {
    if (passwordPlano == null || passwordPlano.trim().isEmpty()) {
      throw new UsuarioInvalidoException("La contraseña no puede estar vacía");
    }

    Rol rolEntity = Rol.find("nombre", rolNombre).firstResult();
    if (rolEntity == null) {
      throw new UsuarioInvalidoException("rol invalido: " + rolNombre);
    }

    String email = emailGenerator.generar(nombre, apellido, rolNombre);
    Usuario u = new Usuario();
    u.setNombre(nombre);
    u.setApellido(apellido);
    u.setEmail(email);
    u.setPasswordHash(hasher.hash(passwordPlano));
    u.setRol(rolEntity);
    u.setHabilitado(true);
    u.validarDatosObligatorios();
    u.persist();

    return u;
  }
}
