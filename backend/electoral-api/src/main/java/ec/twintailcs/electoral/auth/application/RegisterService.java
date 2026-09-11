package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de usuarios. Por ahora solo el MASTER registra (validado por {@code @PreAuthorize} en el
 * controller). No hace auto-registro publico porque sino seria un vector de spam.
 *
 * <p>La password se hashea con BCrypt (cost 12, ver PasswordHasher). Nunca se guarda plain text.
 *
 * <p>El email se genera automáticamente a partir de nombre, apellido y rol con el formato: {3
 * letras nombre}{3 letras apellido}{3 letras rol}@control.ttcs
 */
@Service
public class RegisterService {

  private final UsuarioRepository usuarios;
  private final RolRepository roles;
  private final PasswordHasher hasher;
  private final EmailGenerator emailGenerator;

  public RegisterService(
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
  public Usuario register(String nombre, String apellido, String passwordPlano, String rolNombre) {
    Rol rolEntity =
        roles
            .findByNombre(rolNombre)
            .orElseThrow(() -> new IllegalArgumentException("rol invalido: " + rolNombre));
    String email = emailGenerator.generar(nombre, apellido, rolNombre);
    Usuario u = new Usuario();
    u.setNombre(nombre);
    u.setApellido(apellido);
    u.setEmail(email);
    u.setPasswordHash(hasher.hash(passwordPlano));
    u.setRol(rolEntity);
    u.setHabilitado(true);
    return usuarios.save(u);
  }
}
