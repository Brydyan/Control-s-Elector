package ec.twintailcs.electoral.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lee el usuario actual del SecurityContext.
 *
 * Si el endpoint esta con permitAll (evidencia en este PR), el
 * SecurityContext queda vacio, email=null, etc. Por eso los metodos
 * son null-safe y permiten fallback explicito (id=0L).
 *
 * Cuando se integre feat/rbac, los endpoints dejaran de ser permitAll,
 * el contexto vendra siempre con auth, y los callers dejaran de usar
 * los defaults.
 */
@Component
public class CurrentUser {

    public Authentication authOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName())) return null;
        return auth;
    }

    /** Email del usuario logueado (subject del JWT), o null si no hay auth. */
    public String email() {
        Authentication a = authOrNull();
        return a == null ? null : a.getName();
    }

    /** ID del usuario. CERO si no esta autenticado. */
    public Long id() {
        Authentication a = authOrNull();
        if (a == null) return 0L;
        // El JWT incluye email + rol. Para id, haria falta un lookup via
        // UsuarioRepository. Hasta que integremos eso, usamos una env var
        // o lo inferimos via un claim extra en el JWT. Por ahora, 0L.
        return 0L;
    }

    /** Authority strings cargados (recurso:accion). Vacio si no hay auth. */
    public List<String> permisos() {
        Authentication a = authOrNull();
        if (a == null) return List.of();
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    /** Nombre del rol. Null si no hay auth. */
    public String rolName() {
        Authentication a = authOrNull();
        if (a == null) return null;
        // El rol viaja como autoridad "ROL_OBSERVER" o como claim. Por
        // ahora, null hasta feat/rbac lo emita bien.
        return null;
    }

    // --- static helpers backward-compat con el viejo CurrentUser ---
    // EvidenciaController los usa staticamente. Mantengo esa API.
    public static Long staticId() { return new CurrentUser().id(); }
    public static String staticEmail() { return new CurrentUser().email(); }
    public static String staticRolName() { return new CurrentUser().rolName(); }
}
