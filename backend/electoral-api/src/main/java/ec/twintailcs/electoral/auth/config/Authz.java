package ec.twintailcs.electoral.auth.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Bean para usar en expresiones SpEL de @PreAuthorize.
 *
 * Uso:
 * <pre>{@code
 * @PreAuthorize("@authz.has('evidencia:upload')")
 * @PreAuthorize("@authz.hasAny({'acta:aprobar','acta:rechazar'})")
 * }</pre>
 *
 * Se invoca desde el SecurityContext, sin depender de AOP custom.
 */
@Component("authz")
public class Authz {

    /** Verifica que el usuario autenticado tenga EXACTAMENTE este permiso. */
    public boolean has(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName())) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permission::equals);
    }

    /** Verifica que el usuario tenga AL MENOS UNO de los permisos. */
    public boolean hasAny(String... permissions) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName())) return false;
        Set<String> required = Arrays.stream(permissions).collect(Collectors.toSet());
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(required::contains);
    }
}
