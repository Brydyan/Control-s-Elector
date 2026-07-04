package ec.twintailcs.electoral.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Lee el usuario actual del SecurityContext.
 *
 * Como todavia no hay JWT, los roles no estan poblados. Por ahora
 * si no hay Authentication o no tiene autoridades, devuelve
 * Rol.OBSERVER con id=0 (modo "dev permisivo").
 *
 * Reemplazar cuando se integre JWT: extraer el id del claim sub
 * y el rol del claim rol, validar contra la DB.
 */
public final class CurrentUser {

    private CurrentUser() {}

    /** ID del usuario logueado. 0 si no hay authentication (modo dev). */
    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return 0L;
        }
        return 1L; // TODO: leer del claim JWT cuando se integre
    }

    /** Rol principal del usuario. OBSERVER por defecto hasta JWT. */
    public static Rol rol() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Rol.OBSERVER;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String name = ga.getAuthority();
            if (name.startsWith("ROLE_")) {
                name = name.substring(5);
            }
            try {
                return Rol.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // cae a OBSERVER
            }
        }
        return Rol.OBSERVER;
    }

    /** Convenience: el caller es admin (COORDINATOR/MASTER/ADMIN)? */
    public static boolean esAdmin() {
        Rol r = rol();
        return r == Rol.MASTER || r == Rol.COORDINATOR || r == Rol.ADMIN;
    }
}
