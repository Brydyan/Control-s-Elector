# T-BE-08 — Spring Security + JWT

**Estado:** ✅ **COMPLETADO** — El codebase actual ya tiene seguridad JWT implementada, pero con arquitectura mejorada vs este documento pedagógico.

**Dificultad:** Intermedio-Avanzado (referencia histórica)
**Tiempo estimado:** Ya implementado
**Depende de:** T-BE-04

---

## ⚠️ Nota de alineación con código actual

**Este documento describe una implementación usando jjwt-api.** El código actual está implementado y usa:
- **JOSE library** (com.nimbusds) en lugar de jjwt → ambas válidas, JOSE es más moderna
- **TokenPair record** con access + refresh juntos (vs solo access en el doc)
- **JwtService** con `issue()` y `parse()` (vs `generateAccessToken()`/`validateToken()` en el doc)
- **RlsInterceptor** adicional que setea session variable PostgreSQL (no descrito aquí)
- **CurrentUser helper** para acceso null-safe al usuario autenticado (no descrito aquí)
- **Sesion entity** persistida en BD (refresh token hash guardado, permite revocación)

**La tarea ya está completa.** Para el flujo actual, no necesitas jjwt. Si necesitas entender JWT conceptualmente, sigue leyendo. Si necesitas el código, usa lo que ya existe.

---

## Conceptos clave (referencia)

### ¿Qué es JWT?
JWT (JSON Web Token) es un string con 3 partes separadas por puntos:
```
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInJvbCI6Ik9CU0VSVkVSIn0.abc123
    HEADER                        PAYLOAD                           FIRMA
```

- **Header**: algoritmo usado (HS256)
- **Payload**: datos del usuario (publicId, email, rol, permisos, sesionId) — llamados "claims"
- **Firma**: garantiza que nadie modificó el token

El servidor verifica la firma con una clave secreta. Si alguien modifica el payload, la firma ya no coincide y el token es inválido.

### ¿Por qué access token en memoria + refresh en httpOnly cookie?
- **Access token en memoria JS**: desaparece al cerrar tab, no accesible a XSS
- **Refresh token en httpOnly cookie**: automáticamente enviado por browser, no accesible a JS (ni XSS)
- **Rotación de refresh**: cada use del refresh genera nuevo refresh + nuevo access (revoca el viejo)

### ¿Qué es el filtro JWT?
Es una clase que Spring ejecuta ANTES de procesar cada request. Extrae el token del header `Authorization`, lo valida, extrae claims, y popula SecurityContext con UserPrincipal.

### Arquitectura real (JOSE + Sesion + RLS)
```
Login (email+password) 
  → AuthService valida credenciales (BCrypt)
  → JwtService.issue() genera TokenPair (access + refresh)
  → Sesion persistida en BD con refreshHash (no token plano)
  → Refresh token va en httpOnly cookie (automático)
  → Access token retorna en body (cliente lo guarda en memoria)

Request posterior
  → Browser envía access token en header Authorization
  → JwtAuthFilter extrae token + valida firma + expiracion
  → Crea UserPrincipal (publicId, email, rol, permisos)
  → SetSecurity Context
  → RlsInterceptor convierte publicId → internal userId
  → RlsInterceptor setea session var PostgreSQL para RLS
  → Query ejecuta con filtro RLS automático
```

---

## Implementación actual (resumen)

| Componente | Ubicación | Status |
|-----------|-----------|--------|
| **JwtService** | `auth/application/JwtService.java` | ✅ IMPLEMENTADO (JOSE) |
| **JwtAuthFilter** | `auth/web/JwtAuthFilter.java` | ✅ IMPLEMENTADO |
| **SecurityConfig** | `auth/config/SecurityConfig.java` | ✅ IMPLEMENTADO |
| **RlsInterceptor** | `shared/security/RlsInterceptor.java` | ✅ IMPLEMENTADO (mejora undocumented) |
| **CurrentUser** | `auth/config/CurrentUser.java` | ✅ IMPLEMENTADO (helper) |
| **UserPrincipal** | `auth/config/UserPrincipal.java` | ✅ IMPLEMENTADO (record) |
| **AuthService** | `auth/application/AuthService.java` | ✅ IMPLEMENTADO (T-BE-09) |
| **AuthController** | `auth/web/AuthController.java` | ✅ IMPLEMENTADO (T-BE-09) |

## Archivos creados (no necesitas hacer nada)

Ya existen en `backend/electoral-api/src/main/java/ec/twintailcs/electoral/`:

```
auth/application/
  JwtService.java              ← Issue (access + refresh), Parse
  AuthService.java             ← Login, Refresh, Logout, Sesion
auth/config/
  SecurityConfig.java          ← Filter chain, method security
  JwtAuthFilter.java           ← Extract + validate token
  UserPrincipal.java           ← Authenticated user data
  CurrentUser.java             ← Helper para acceso seguro
shared/security/
  RlsInterceptor.java          ← PostgreSQL session var setup
```

Properties configuradas en `application.properties`:
```properties
app.jwt.secret=...               # 32+ chars, inyectado via @Value
app.jwt.access-ttl=15m           # Token expiracion
app.jwt.refresh-ttl=7d           # Refresh expiracion
app.jwt.issuer=control-elector   # issuer claim
```

**NO necesitas agregar jjwt.** Ya usa JOSE. Si querías entender JWT, continúa leyendo la sección "Conceptos" abajo.

### JwtService (ya implementado)

Ubicación: `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/application/JwtService.java`

Método clave: `issue()` emite par (access + refresh)
```java
public TokenPair issue(UUID publicId, String email, String rol,
                      List<String> permissions, String sesionId)
```

Retorna `TokenPair record`:
```java
record TokenPair(String access, String refresh,
                Duration accessTtl, Duration refreshTtl,
                String sesionId)
```

Validación: `parse()` valida firma + expiracion, lanza si inválido
```java
public ParsedJwt parse(String token) throws InvalidJwtException
```

Retorna `ParsedJwt record` con claims extraídos.

**Diferencia vs doc:** 
- Usa JOSE library (SignedJWT + JWTClaimsSet) no jjwt
- Emite dos tokens juntos (access + refresh)
- Refresh se retorna al controller → va a httpOnly cookie automáticamente
- Access se retorna en body → cliente lo guarda en memoria

### UserPrincipal (ya implementado)

Ubicación: `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/config/UserPrincipal.java`

Record que encapsula el usuario autenticado:
```java
record UserPrincipal(UUID id,      // publicId
                    String email,
                    String rol,
                    List<String> perms)  // permission strings
```

NO se usa `UserDetailsService` en esta arquitectura. La validación es JWT-based, no BD-based. UserPrincipal se crea directamente del JWT claims en JwtAuthFilter.

### JwtAuthFilter (ya implementado)

Ubicación: `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/web/JwtAuthFilter.java`

Este filtro se ejecuta en CADA request. Extrae el token, valida con JwtService, crea UserPrincipal, setea SecurityContext.

Flujo clave:
```
1. Lee header Authorization: "Bearer <token>"
2. Extrae token
3. Llama jwt.parse(token) → valida firma + expiracion
4. Si ok: extrae claims (subject=publicId, email, rol, perms)
5. Crea UserPrincipal(publicId, email, rol, perms)
6. Crea UsernamePasswordAuthenticationToken(principal=UserPrincipal, authorities=perms)
7. SetSecurityContextHolder
```

Después, RlsInterceptor (que corre POST-JwtAuthFilter) convierte publicId → internal userId y setea session var PostgreSQL.

**Diferencia vs doc:** principal es UserPrincipal record, no Long userId plano.

### SecurityConfig (ya implementado)

Ubicación: `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/config/SecurityConfig.java`

Configuración de filtro chain:
- CSRF disabled (API REST stateless)
- CORS disabled
- SessionCreationPolicy.STATELESS (sin cookies HTTP)
- Permitall: `/actuator/health`, `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`
- Resto: `.authenticated()` (requiere JWT válido)
- JwtAuthFilter registrado ANTES de UsernamePasswordAuthenticationFilter
- RlsInterceptor registrado DESPUÉS de JwtAuthFilter
- Exception handlers: 401 para auth fail, 403 para access denied

PasswordEncoder bean retorna BCryptPasswordEncoder(12).

### CurrentUser helper (ya implementado)

Ubicación: `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/config/CurrentUser.java`

Componente que extrae user autenticado de forma segura:

```java
@Component
public class CurrentUser {
    // Retorna UserPrincipal o null si anónimo
    UserPrincipal authOrNull()
    
    // Retorna email del usuario autenticado
    String email()
    
    // Retorna publicId (UUID)
    UUID id()
    
    // Retorna lista de permisos
    List<String> permisos()
    
    // Retorna nombre del rol
    String rolName()
}
```

Para usar en un controller:
```java
@GetMapping("/mis-actas")
public ResponseEntity<?> misActas() {
    UUID publicId = currentUser.id();  // Extrae publicId del JWT
    // ... usar publicId para filtrar actas del usuario
}
```

O manualmente via SecurityContextHolder:
```java
UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
    .getAuthentication()
    .getPrincipal();
String email = principal.email();
List<String> perms = principal.perms();
```

---

## Cómo verificar que funciona (después de T-BE-09)

1. Intenta acceder sin token:
```bash
curl http://localhost:8081/api/observer/mis-actas
# Retorna 401 Unauthorized
```

2. Con token válido (después de login en T-BE-09):
```bash
curl -H "Authorization: Bearer eyJ..." http://localhost:8081/api/observer/mis-actas
# Retorna 200 con actas del usuario autenticado
```

---

## Notas finales

**T-BE-08 ya está completado.** Los archivos están en:
- `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/` → aplicación
- `backend/electoral-api/src/main/java/ec/twintailcs/electoral/shared/security/` → RLS

**Próximo paso:** T-BE-09 (AuthController) agrega los endpoints login/refresh/logout que usan JwtService.
