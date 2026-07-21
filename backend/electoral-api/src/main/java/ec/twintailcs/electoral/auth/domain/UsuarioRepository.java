package ec.twintailcs.electoral.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByPublicId(UUID publicId);
    boolean existsByEmail(String email);
    List<Usuario> findByRol(Rol rol);
}
