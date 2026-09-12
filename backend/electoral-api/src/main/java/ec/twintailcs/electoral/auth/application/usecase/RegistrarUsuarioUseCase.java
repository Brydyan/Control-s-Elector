package ec.twintailcs.electoral.auth.application.usecase;

import ec.twintailcs.electoral.auth.application.EmailGenerator;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioInvalidoException;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrarUsuarioUseCase {

  private final UsuarioRepository usuarios;
  private final RolRepository roles;
  private final PasswordHasher hasher;
  private final EmailGenerator emailGenerator;

  public RegistrarUsuarioUseCase(
      UsuarioRepository usuarios,
      RolRepository roles,
      PasswordHasher hasher,
      EmailGenerator emailGenerator) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.hasher = hasher;
    this.emailGenerator = emailGenerator;
  }

  @Transactional
  public Usuario execute(String nombre, String apellido, String passwordPlano, String rolNombre) {
    if (passwordPlano == null || passwordPlano.trim().isEmpty()) {
      throw new UsuarioInvalidoException("La contraseña no puede estar vacía");
    }

    Rol rolEntity =
        roles
            .findByNombre(rolNombre)
            .orElseThrow(() -> new UsuarioInvalidoException("rol invalido: " + rolNombre));

    String email = emailGenerator.generar(nombre, apellido, rolNombre);
    Usuario u = new Usuario();
    u.setNombre(nombre);
    u.setApellido(apellido);
    u.setEmail(email);
    u.setPasswordHash(hasher.hash(passwordPlano));
    u.setRol(rolEntity);
    u.setHabilitado(true);
    u.validarDatosObligatorios();

    return usuarios.save(u);
  }
}
