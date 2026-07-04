package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de usuarios. Por ahora solo el MASTER registra (validado por
 * {@code @PreAuthorize} en el controller). No hace auto-registro publico
 * porque sino seria un vector de spam.
 *
 * La password se hashea con BCrypt (cost 12, ver PasswordHasher). Nunca
 * se guarda plain text.
 */
@Service
public class RegisterService {

    private final UsuarioRepository usuarios;
    private final PasswordHasher hasher;

    public RegisterService(UsuarioRepository usuarios, PasswordHasher hasher) {
        this.usuarios = usuarios;
        this.hasher = hasher;
    }

    @Transactional
    public Usuario register(String email, String passwordPlano, String rol) {
        if (usuarios.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("email ya registrado");
        }
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(hasher.hash(passwordPlano));
        u.setRol(rol);
        u.setHabilitado(true);
        return usuarios.save(u);
    }
}
