package ec.twintailcs.electoral.common;

import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resuelve UUIDs publicos a entidades JPA, para uso en MapStruct mappers
 * via {@code qualifiedByName}. Sin caché intencionalmente — cada llamada
 * va al repositorio.
 * <p>
 * Sigue el mismo patron que publimes-omnicanal (UuidResolver).
 */
@Component
@RequiredArgsConstructor
public class UuidResolver {

    private final UsuarioRepository usuarioRepository;
    private final EvidenciaRepository evidenciaRepository;
    private final RolRepository rolRepository;

    @Named("resolveUsuario")
    public Usuario resolveUsuario(UUID uuid) {
        return uuid == null ? null : usuarioRepository.findByPublicId(uuid).orElse(null);
    }

    @Named("resolveEvidencia")
    public Evidencia resolveEvidencia(UUID uuid) {
        return uuid == null ? null : evidenciaRepository.findByPublicId(uuid).orElse(null);
    }

    @Named("resolveRol")
    public Rol resolveRol(UUID uuid) {
        return uuid == null ? null : rolRepository.findByPublicId(uuid).orElse(null);
    }
}
