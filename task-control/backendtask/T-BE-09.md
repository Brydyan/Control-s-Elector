# T-BE-09 — AuthController: Login, Refresh y Logout

**Dificultad:** Intermedio
**Tiempo estimado:** 2-3 horas
**Depende de:** T-BE-08

---

## ¿Qué vas a construir?

Los endpoints de autenticación:
- `POST /api/auth/login` — usuario envía email+password, recibe tokens
- `POST /api/auth/refresh` — renueva el access token con el refresh token
- `POST /api/auth/logout` — revoca el refresh token del usuario actual
- `DELETE /api/auth/sessions/{usuarioId}` — MASTER revoca TODAS las sesiones de alguien

---

## Conceptos clave

### @RestController y @RequestMapping
```java
@RestController                        // = @Controller + @ResponseBody (retorna JSON automáticamente)
@RequestMapping("/api/auth")           // prefijo de todas las rutas de esta clase
public class AuthController { }
```

### ResponseEntity<?>
Permite controlar el código HTTP de la respuesta:
```java
return ResponseEntity.ok(datos);                    // 200 OK
return ResponseEntity.status(HttpStatus.CREATED).body(datos);  // 201 Created
return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error); // 401
```

### BCrypt — verificar password
BCrypt guarda el hash de la contraseña. Para verificar:
```java
// Esto retorna true si "password123" produce el mismo hash que passwordHash
passwordEncoder.matches("password123", usuario.getPasswordHash());
```

### Refresh token — ¿qué es?
Es un String aleatorio (UUID) que se guarda en la BD. NO es JWT. Solo sirve para obtener un nuevo access token cuando el actual expira. Expira en 8 horas.

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/
  dto/
    LoginRequestDto.java
    LoginResponseDto.java
    RefreshRequestDto.java
  service/
    AuthService.java
  controller/
    AuthController.java
```

---

## Pasos

### Paso 1 — DTOs de autenticación

```java
// src/main/java/ec/twintailcs/electoral/dto/LoginRequestDto.java
package ec.twintailcs.electoral.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequestDto {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    // Getters y Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/LoginResponseDto.java
package ec.twintailcs.electoral.dto;

public class LoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private String rol;
    private String nombre;
    private Long userId;

    // Constructor completo
    public LoginResponseDto(String accessToken, String refreshToken,
                            String rol, String nombre, Long userId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.rol = rol;
        this.nombre = nombre;
        this.userId = userId;
    }

    // Getters (solo getters — es una respuesta, no se modifica)
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getRol() { return rol; }
    public String getNombre() { return nombre; }
    public Long getUserId() { return userId; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/RefreshRequestDto.java
package ec.twintailcs.electoral.dto;

import jakarta.validation.constraints.NotBlank;

public class RefreshRequestDto {

    @NotBlank(message = "El refreshToken es obligatorio")
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
```

### Paso 2 — AuthService

```java
// src/main/java/ec/twintailcs/electoral/service/AuthService.java
package ec.twintailcs.electoral.service;

import ec.twintailcs.electoral.domain.RefreshToken;
import ec.twintailcs.electoral.domain.Usuario;
import ec.twintailcs.electoral.dto.*;
import ec.twintailcs.electoral.exception.AccesoNoPermitidoException;
import ec.twintailcs.electoral.repository.*;
import ec.twintailcs.electoral.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AuthService(UsuarioRepository usuarioRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto dto, HttpServletRequest request) {

        // 1. Buscar usuario por email
        Usuario usuario = usuarioRepository.findByEmail(dto.getEmail())
            .orElseThrow(() -> new AccesoNoPermitidoException("Credenciales inválidas"));

        // 2. Verificar password con BCrypt
        // passwordEncoder.matches() compara el texto plano con el hash guardado
        if (!passwordEncoder.matches(dto.getPassword(), usuario.getPasswordHash())) {
            // Mismo mensaje que "usuario no encontrado" — no revelar si el email existe
            auditLogService.registrar(null, "LOGIN_FALLIDO", "usuario", null,
                "Intento de login fallido para: " + dto.getEmail(), request);
            throw new AccesoNoPermitidoException("Credenciales inválidas");
        }

        // 3. Generar access token JWT (15 min, solo en memoria del cliente)
        String accessToken = jwtService.generateAccessToken(usuario);

        // 4. Generar refresh token opaco (UUID aleatorio, se guarda en BD)
        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setUsuario(usuario);
        refreshToken.setExpiresAt(LocalDateTime.now().plusHours(8)); // 8 horas
        refreshToken.setRevocado(false);
        refreshTokenRepository.save(refreshToken);

        // 5. Registrar login exitoso en audit log
        auditLogService.registrar(usuario.getId(), "LOGIN_EXITOSO", "usuario", usuario.getId(),
            "Login exitoso", request);

        return new LoginResponseDto(
            accessToken,
            refreshTokenValue,
            usuario.getRol().name(),
            usuario.getNombre(),
            usuario.getId()
        );
    }

    @Transactional
    public String refresh(RefreshRequestDto dto) {

        // 1. Buscar el refresh token en BD
        RefreshToken refreshToken = refreshTokenRepository.findByToken(dto.getRefreshToken())
            .orElseThrow(() -> new AccesoNoPermitidoException("Refresh token inválido"));

        // 2. Verificar que no fue revocado
        if (refreshToken.isRevocado()) {
            throw new AccesoNoPermitidoException("Refresh token revocado");
        }

        // 3. Verificar que no expiró
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AccesoNoPermitidoException("Refresh token expirado");
        }

        // 4. Generar nuevo access token
        return jwtService.generateAccessToken(refreshToken.getUsuario());
    }

    @Transactional
    public void logout(Long userId, HttpServletRequest request) {
        // Revocar todos los refresh tokens del usuario que hace logout
        Usuario usuario = usuarioRepository.findById(userId)
            .orElseThrow(() -> new AccesoNoPermitidoException("Usuario no encontrado"));

        refreshTokenRepository.revocarTodosDeUsuario(usuario);

        auditLogService.registrar(userId, "LOGOUT", "usuario", userId,
            "Usuario cerró sesión", request);
    }

    @Transactional
    public void revocarSesiones(Long targetUserId, Long masterId, HttpServletRequest request) {
        // Solo MASTER puede revocar sesiones de otros — verificado en el controller con @PreAuthorize

        Usuario target = usuarioRepository.findById(targetUserId)
            .orElseThrow(() -> new AccesoNoPermitidoException("Usuario no encontrado: " + targetUserId));

        refreshTokenRepository.revocarTodosDeUsuario(target);

        auditLogService.registrar(masterId, "REVOCAR_SESIONES", "usuario", targetUserId,
            "MASTER revocó todas las sesiones del usuario " + targetUserId, request);
    }
}
```

### Paso 3 — AuthController

```java
// src/main/java/ec/twintailcs/electoral/controller/AuthController.java
package ec.twintailcs.electoral.controller;

import ec.twintailcs.electoral.dto.*;
import ec.twintailcs.electoral.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login
     * Público — no requiere token
     * Body: { "email": "...", "password": "..." }
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto dto,
                                                   HttpServletRequest request) {
        LoginResponseDto response = authService.login(dto, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/refresh
     * Público — solo necesita el refresh token
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@Valid @RequestBody RefreshRequestDto dto) {
        String newAccessToken = authService.refresh(dto);
        // Retornamos un mapa simple con el nuevo token
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    /**
     * POST /api/auth/logout
     * Requiere estar autenticado — revoca los tokens del usuario actual
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        // Obtener el userId del token JWT actual (lo puso el filtro)
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.logout(userId, request);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    /**
     * DELETE /api/auth/sessions/{usuarioId}
     * Solo MASTER puede revocar sesiones de otros usuarios
     */
    @DeleteMapping("/sessions/{usuarioId}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Void> revocarSesiones(@PathVariable Long usuarioId,
                                                  HttpServletRequest request) {
        Long masterId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.revocarSesiones(usuarioId, masterId, request);
        return ResponseEntity.noContent().build();
    }
}
```

---

## Cómo verificar que funciona

**1. Primero, crea un usuario MASTER en la BD directamente:**
```sql
INSERT INTO usuario (nombre, email, password_hash, rol)
VALUES ('Admin Master', 'master@electoral.ec',
        '$2a$10$N.zmdr9zkoa05kRw8mQrWeGX8f8X8X8X8X8X8X8X8X8X8X8X8X8', -- bcrypt de "Password123"
        'MASTER');
```

Tip: Para generar un hash BCrypt en Java puedes usar:
```java
System.out.println(new BCryptPasswordEncoder().encode("Password123"));
```

**2. Prueba el login:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"master@electoral.ec","password":"Password123"}'
```

Respuesta esperada:
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "rol": "MASTER",
  "nombre": "Admin Master",
  "userId": 1
}
```

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `401` en login | `@PostMapping("/login")` no es público | Verifica `.requestMatchers("/api/auth/**").permitAll()` en SecurityConfig |
| `400 Bad Request` | Validación fallida (`@Valid`) | Lee el cuerpo de la respuesta — dice qué campo falla |
| `500` al hacer matches de password | Usuario en BD no tiene hash BCrypt | El password en BD debe ser el hash, no texto plano |
| `NullPointerException` en logout | `SecurityContextHolder` no tiene usuario | El filtro JWT no autenticó correctamente — revisa T-BE-08 |
