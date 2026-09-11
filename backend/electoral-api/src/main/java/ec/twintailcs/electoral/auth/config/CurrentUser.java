package ec.twintailcs.electoral.auth.config;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Lee el usuario actual del SecurityContext.
 *
 * Cuando el JWT es valido, el JwtAuthFilter inyecta un
 * {@link UserPrincipal} como principal (con id, email, rol, permisos)
 * y CurrentUser devuelve valores reales.
 *
 * Si el endpoint esta con permitAll (login, refresh, logout), el
 * SecurityContext queda vacio con anonymousUser, y los metodos
 * devuelven defaults null-safe.
 */
@Component
public class CurrentUser {

    public Authentication authOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName())) return null;
        return auth;
    }

    /**
     * Email del usuario logueado (claim {@code email} del JWT).
     *
     * Si el principal es un {@link UserPrincipal} (camino normal via
     * JwtAuthFilter) usamos su email explicito. Como fallback (p.ej.
     * tests que setean un principal distinto), usamos
     * {@link Authentication#getName()} que para UserDetails o
     * AuthenticatedPrincipal devuelve el username. Null si no hay auth.
     */
    public String email() {
        Authentication a = authOrNull();
        if (a == null) return null;
        if (a.getPrincipal() instanceof UserPrincipal up) return up.email();
        return a.getName();
    }

    /**
     * ID del usuario extraido del claim {@code sub} del JWT.
     * CERO si no esta autenticado.
     */
    public UUID id() {
        Authentication a = authOrNull();
        if (a == null) return null;
        if (a.getPrincipal() instanceof UserPrincipal up) return up.id();
        return null;
    }

    /** Authority strings cargados (recurso:accion). Vacio si no hay auth. */
    public List<String> permisos() {
        Authentication a = authOrNull();
        if (a == null) return List.of();
        if (a.getPrincipal() instanceof UserPrincipal up) return up.permisos();
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    /**
     * Nombre del rol extraido del claim {@code rol} del JWT.
     * Null si no esta autenticado.
     */
    public String rolName() {
        Authentication a = authOrNull();
        if (a == null) return null;
        if (a.getPrincipal() instanceof UserPrincipal up) return up.rol();
        return null;
    }

    // --- static helpers backward-compat con callers existentes ---
    // EvidenciaController los usa staticamente. Mantenemos la API.
    public static UUID staticId() { return new CurrentUser().id(); }
    public static String staticEmail() { return new CurrentUser().email(); }
    public static String staticRolName() { return new CurrentUser().rolName(); }
}
