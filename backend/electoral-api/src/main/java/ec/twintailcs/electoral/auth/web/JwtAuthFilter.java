package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.config.UserPrincipal;
import ec.twintailcs.electoral.auth.application.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
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

    private static final String ROLE_PREFIX = "ROLE_";

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
            JwtService.ParsedJwt parsed = jwt.parseAccess(token);

            // 1) Permisos crudos (recurso:accion) que usan @authz.has(...) y
            //    hasAuthority(...) en expresiones SpEL. Cada perm se conserva
            //    tal cual viene del JWT.
            List<SimpleGrantedAuthority> auths = new ArrayList<>();
            for (String perm : parsed.perms()) {
                if (perm != null && !perm.isBlank()) {
                    auths.add(new SimpleGrantedAuthority(perm));
                }
            }

            // 2) Rol canonico Spring Security (ROLE_<rol>) que usan
            //    hasRole(...) / hasAnyRole(...) en @PreAuthorize. Sin este
            //    authority los endpoints @PreAuthorize("hasAnyRole(...)")
            //    devuelven 403 incluso con token valido.
            String rol = parsed.rol();
            if (rol == null || rol.isBlank()) {
                // Token malformado (sin rol): misma ruta que token invalido.
                // SecurityConfig devuelve 401 al endpoint protegido.
                throw new JwtService.InvalidJwtException("JWT sin rol");
            }
            String roleAuthority = ROLE_PREFIX + rol;
            boolean roleAlreadyPresent = auths.stream()
                    .anyMatch(a -> roleAuthority.equals(a.getAuthority()));
            if (!roleAlreadyPresent) {
                auths.add(new SimpleGrantedAuthority(roleAuthority));
            }

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
