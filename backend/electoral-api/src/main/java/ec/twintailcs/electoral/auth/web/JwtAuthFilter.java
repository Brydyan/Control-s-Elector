package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.config.UserPrincipal;
import ec.twintailcs.electoral.auth.application.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee el header Authorization: Bearer <jwt>, valida la firma + expiracion
 * y puebla el SecurityContext con un Authentication que tiene como
 * authorities los permisos del usuario ({@code recurso:accion}).
 *
 * Si el header no esta o el token es invalido, el filter pasa (return).
 * SecurityConfig decide que endpoint requiere auth y manda 401 via
 * AuthenticationEntryPoint.
 *
 * El sesionId en el token NO se usa aca (cada Sesion ya esta validada
 * implicitamente por su existencia en DB y por el filtro de revocacion).
 * Para revocacion inmediata al logout, habria que agregar un cache
 * (Caffeine o similar) con TTL=accessTtl. Por ahora, la ventana de
 * revocacion es de 15m (access-ttl). Documentamos como mejora.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring("Bearer ".length());
        try {
            JwtService.ParsedJwt parsed = jwt.parse(token);

            List<SimpleGrantedAuthority> auths = parsed.perms().stream()
                    .map(p -> new SimpleGrantedAuthority(p))
                    .toList();

            var principal = new UserPrincipal(
                    UUID.fromString(parsed.subject()),
                    parsed.email(),
                    parsed.rol(),
                    parsed.perms());
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, auths);
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            // Token invalido: dejamos el contexto vacio. SecurityConfig
            // devuelve 401 al endpoint protegido.
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
