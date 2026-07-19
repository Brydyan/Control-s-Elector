package ec.twintailcs.electoral.shared.security;

import ec.twintailcs.electoral.auth.config.UserPrincipal;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Interceptor que establece la session variable de PostgreSQL app.current_user_id
 * antes de cada query. Necesario para que RLS funcione.
 *
 * Flujo:
 * 1. Usuario hace request con JWT
 * 2. JwtAuthFilter extrae el publicId del JWT y lo pone en UserPrincipal
 * 3. Este filter lo convierte a internal id (Long) consultando UsuarioRepository
 * 4. Ejecuta: SET LOCAL app.current_user_id = '123'
 * 5. Todas las queries de este request usan esa variable
 * 6. RLS policies usan get_current_user_id() para filtrar filas
 */
@Component
public class RlsInterceptor extends OncePerRequestFilter {

    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;

    public RlsInterceptor(JdbcTemplate jdbcTemplate, UsuarioRepository usuarioRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Obtener el usuario autenticado del JWT (vía Spring Security)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
                UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();

                // Convertir publicId (UUID) a internal id (Long)
                var usuario = usuarioRepository.findByPublicId(userPrincipal.id());
                if (usuario.isPresent()) {
                    Long userId = usuario.get().getId();
                    // Establecer la session variable para RLS
                    // SET LOCAL es seguro: solo afecta esta transacción/query
                    jdbcTemplate.execute("SET LOCAL app.current_user_id = '" + userId + "'");
                }
            }
        } catch (Exception e) {
            // Si falla la setup de RLS, loguear pero continuar
            // El request seguirá pero RLS puede ser más restrictivo
            logger.warn("Error setting RLS context", e);
        }

        filterChain.doFilter(request, response);
    }
}
