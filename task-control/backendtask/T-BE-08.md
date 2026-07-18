# T-BE-08 — Spring Security + JWT

**Dificultad:** Intermedio-Avanzado
**Tiempo estimado:** 4-5 horas
**Depende de:** T-BE-04

---

## ¿Qué vas a construir?

El sistema de autenticación completo:
- **JWT** para identificar al usuario en cada request
- **Spring Security** para proteger los endpoints
- **Filtro personalizado** que intercepta cada request y valida el token

---

## Conceptos clave

### ¿Qué es JWT?
JWT (JSON Web Token) es un string con 3 partes separadas por puntos:
```
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInJvbCI6Ik9CU0VSVkVSIn0.abc123
    HEADER                        PAYLOAD                           FIRMA
```

- **Header**: algoritmo usado (HS256)
- **Payload**: datos del usuario (userId, rol, expiración) — llamados "claims"
- **Firma**: garantiza que nadie modificó el token

El servidor verifica la firma con una clave secreta. Si alguien modifica el payload, la firma ya no coincide y el token es inválido.

### ¿Por qué el access token va en memoria (no localStorage)?
- `localStorage` es accesible por cualquier JavaScript — si hay un ataque XSS, el atacante roba el token
- En memoria (variable JS), el token desaparece al cerrar el tab — más seguro
- Por eso tenemos refresh token en localStorage (de vida más corta que nos da un nuevo access token)

### ¿Qué es el filtro JWT?
Es una clase que Spring ejecuta ANTES de procesar cada request. Extrae el token del header `Authorization`, lo valida, y si es válido, le dice a Spring Security "este request viene de usuario X con rol Y".

### Dependencias necesarias
Agrega en `pom.xml`:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/security/
  JwtService.java
  JwtAuthenticationFilter.java
  SecurityConfig.java
  UserDetailsServiceImpl.java
src/main/resources/
  application.properties   ← agregar jwt.secret
```

---

## Pasos

### Paso 1 — Agregar la clave secreta en properties

```properties
# application.properties — agregar estas líneas
jwt.secret=TuClaveSecretaMuyLargaDeAlMenos64CaracteresParaHS512AquiVaLaClave
jwt.expiration-ms=900000
# 900000 ms = 15 minutos
```

**Importante:** En producción, nunca pongas la clave real en el archivo — usa variables de entorno.

### Paso 2 — JwtService

```java
// src/main/java/ec/twintailcs/electoral/security/JwtService.java
package ec.twintailcs.electoral.security;

import ec.twintailcs.electoral.domain.Usuario;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    // Lee el valor de jwt.secret en application.properties
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Genera un access token JWT para el usuario.
     * El token contiene: userId, email, rol y expiración.
     */
    public String generateAccessToken(Usuario usuario) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());

        return Jwts.builder()
            .subject(String.valueOf(usuario.getId()))    // el "sub" del JWT = userId
            .claim("email", usuario.getEmail())           // dato extra: email
            .claim("rol", usuario.getRol().name())        // dato extra: rol
            .issuedAt(new Date())                         // cuándo se creó
            .expiration(new Date(System.currentTimeMillis() + expirationMs)) // cuándo expira
            .signWith(key)                                // firma con la clave secreta
            .compact();                                   // genera el string final
    }

    /**
     * Valida si el token es correcto y no expiró.
     * Retorna true si es válido, false si no.
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token inválido, expirado, o mal formado
            return false;
        }
    }

    /**
     * Extrae el userId del token (el campo "sub").
     */
    public Long extractUserId(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extrae el rol del token.
     */
    public String extractRol(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return claims.get("rol", String.class);
    }
}
```

### Paso 3 — UserDetailsServiceImpl

Spring Security necesita cargar los datos del usuario. Esta clase le dice cómo hacerlo.

```java
// src/main/java/ec/twintailcs/electoral/security/UserDetailsServiceImpl.java
package ec.twintailcs.electoral.security;

import ec.twintailcs.electoral.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UserDetailsServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        // Convertimos el rol a formato que entiende Spring Security: "ROLE_OBSERVER"
        return new org.springframework.security.core.userdetails.User(
            String.valueOf(usuario.getId()),   // username = userId (para identificarlo)
            usuario.getPasswordHash(),
            List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))
        );
    }
}
```

### Paso 4 — JwtAuthenticationFilter

Este filtro se ejecuta en CADA request. Extrae el token del header, lo valida, y autentica al usuario.

```java
// src/main/java/ec/twintailcs/electoral/security/JwtAuthenticationFilter.java
package ec.twintailcs.electoral.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Leer el header Authorization
        String authHeader = request.getHeader("Authorization");

        // Si no hay header o no empieza con "Bearer ", dejar pasar sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extraer el token (quitar "Bearer " del inicio)
        String token = authHeader.substring(7);

        // Validar el token
        if (!jwtService.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extraer datos del token
        Long userId = jwtService.extractUserId(token);
        String rol = jwtService.extractRol(token);

        // Crear el objeto de autenticación de Spring Security
        // Esto es lo que @PreAuthorize y SecurityContextHolder.getContext().getAuthentication() usan
        var authentication = new UsernamePasswordAuthenticationToken(
            userId,  // principal = el userId (para recuperarlo en los controllers)
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + rol))
        );

        // Registrar la autenticación en el contexto de seguridad
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Continuar con el request
        filterChain.doFilter(request, response);
    }
}
```

### Paso 5 — SecurityConfig

```java
// src/main/java/ec/twintailcs/electoral/security/SecurityConfig.java
package ec.twintailcs.electoral.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Habilita @PreAuthorize en los controllers
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Deshabilitar CSRF — no necesario en APIs REST stateless
            .csrf(csrf -> csrf.disable())

            // No usar sesiones HTTP — cada request se autentica con JWT
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Reglas de acceso por ruta
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()   // login y refresh son públicos
                .anyRequest().authenticated()                   // todo lo demás requiere token
            )

            // Agregar nuestro filtro JWT ANTES del filtro de autenticación estándar
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt es el estándar para hashear passwords
        return new BCryptPasswordEncoder();
    }
}
```

### Paso 6 — Cómo obtener el userId en un controller

```java
// En cualquier controller, obtén el usuario autenticado así:
@GetMapping("/mis-jrvs")
public ResponseEntity<?> misJrvs() {
    // El "principal" es el userId que pusimos en el filtro
    Long userId = (Long) SecurityContextHolder.getContext()
        .getAuthentication()
        .getPrincipal();

    // Ahora puedes usar userId para consultas
    return ResponseEntity.ok(jrvService.findByObserverId(userId));
}
```

---

## Cómo verificar que funciona

1. Intenta acceder a cualquier endpoint sin token:
```bash
curl http://localhost:8080/api/observer/mis-actas
# Debe retornar 401 Unauthorized
```

2. Con un token válido (después de implementar login en T-BE-09):
```bash
curl -H "Authorization: Bearer eyJ..." http://localhost:8080/api/observer/mis-actas
# Debe retornar 200 con datos (lista de actas propias)
```

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `401` en todos los endpoints | Filtro no está registrado | Verifica `@Component` en `JwtAuthenticationFilter` |
| `403` en endpoints con `@PreAuthorize` | Falta `@EnableMethodSecurity` | Agrega en `SecurityConfig` |
| `SignatureException` | Clave secreta diferente al generar y validar | Usa la misma clave en ambos métodos |
| `io.jsonwebtoken` not found | Faltan dependencias | Agrega las 3 dependencias jjwt en pom.xml |
| Token no expira | Calculaste mal la expiración | Verifica que es `currentTimeMillis() + expirationMs` |
