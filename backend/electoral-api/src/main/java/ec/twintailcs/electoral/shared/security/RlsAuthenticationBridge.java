package ec.twintailcs.electoral.shared.security;

import ec.twintailcs.electoral.auth.config.UserPrincipal;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puente entre la autenticación JWT y el contexto RLS.
 *
 * <p>Corre justo después del {@code JwtAuthFilter}. Lee el
 * {@link UserPrincipal} ya validado por Spring Security, resuelve el id
 * interno del usuario y guarda (id, rolNombre) en {@link RlsContext}.
 *
 * <p>Importante:
 * <ul>
 *   <li>NO ejecuta SET LOCAL acá: eso lo hace {@link RlsInterceptor} en
 *       preHandle(), dentro del contexto transaccional correcto.</li>
 *   <li>Si la resolución falla, lanza. Antes el interceptor original
 *       logueaba y seguía, dejando RLS desincronizado. Eso era la causa
 *       de que OBSERVER pudiera ver actas ajenas.</li>
 *   <li>{@link RlsContext#clear()} corre siempre en el finally para
 *       evitar leaks entre requests que comparten hilo.</li>
 * </ul>
 */
@Component
public class RlsAuthenticationBridge extends OncePerRequestFilter {

    private final UsuarioRepository usuarioRepository;

    public RlsAuthenticationBridge(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                Usuario usuario = usuarioRepository.findByPublicId(principal.id())
                        .orElseThrow(() -> new IllegalStateException(
                                "Usuario autenticado no encontrado: " + principal.id()));
                RlsContext.set(usuario.getId(), usuario.getRolNombre());
            }
            filterChain.doFilter(request, response);
        } finally {
            RlsContext.clear();
        }
    }
}