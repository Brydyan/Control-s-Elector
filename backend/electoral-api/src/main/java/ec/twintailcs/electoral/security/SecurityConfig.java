package ec.twintailcs.electoral.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityFilterChain minimalista para dev. Habilita:
 *  - /actuator/health: publico (Docker healthcheck no necesita auth)
 *  - todo lo demas: requiere autenticacion (JWT mas adelante)
 *
 * En prod esto se reemplaza por la config real con JWT, RBAC, etc.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // healthchecks de Docker / uptime
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // API de evidencias (sin JWT todavia — permiso amplio en dev).
                // Cuando se integre JWT: cambiar a ".authenticated()" y validar
                // el Rol via @PreAuthorize en los handlers del controller/service.
                .requestMatchers("/api/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
