package ec.twintailcs.electoral.auth.config;

import ec.twintailcs.electoral.auth.web.JwtAuthFilter;
import ec.twintailcs.electoral.shared.security.RlsAuthenticationBridge;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthFilter jwtAuthFilter,
      RlsAuthenticationBridge rlsAuthenticationBridge,
      ObjectMapper objectMapper)
      throws Exception {

    http.csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        // Dispatch de error de Spring. Sin esto, cualquier error que termine en
                        // sendError() (por ejemplo un body JSON malformado) se re-dispatcha a
                        // /error sin token y el cliente ve 401 en vez del status real.
                        "/error")
                    .permitAll()
                    // OpenAPI / Swagger UI — publicos para que devs y consumers
                    // puedan descubrir el contrato sin JWT. En prod restringir
                    // por IP/gateway (no exponer a internet abierto).
                    .requestMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/v3/api-docs/swagger-config",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/webjars/**")
                    .permitAll()
                    // Evidencias protegidas via @RequiredPermission en el controller.
                    // Cada endpoint especifica su permiso (evidencia:upload, :read, etc.).
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (req, res, authEx) ->
                            writeJsonError(
                                res,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "No autenticado o token invalido"))
                    .accessDeniedHandler(
                        (req, res, deniedEx) ->
                            writeJsonError(
                                res, objectMapper, HttpStatus.FORBIDDEN, "Permisos insuficientes")))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rlsAuthenticationBridge, JwtAuthFilter.class);

    return http.build();
  }

  private static void writeJsonError(
      HttpServletResponse res, ObjectMapper mapper, HttpStatus status, String message)
      throws IOException {
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("error", status.getReasonPhrase());
    body.put("message", message);
    mapper.writeValue(res.getOutputStream(), body);
  }
}
