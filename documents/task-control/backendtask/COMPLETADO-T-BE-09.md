# T-BE-09 — AuthController: Login, Refresh y Logout

**Estado:** ✅ **COMPLETADO** — Implementación real es más avanzada que este documento.

**Dificultad:** Intermedio (referencia histórica)
**Tiempo estimado:** Ya implementado
**Depende de:** T-BE-08

---

## ⚠️ Nota de alineación con código actual

**Implementación actual mejorada vs el documento:**
- **Sesion entity** persistida en BD (no solo refresh token)
- **IP cliente + User-Agent** registrados en cada login
- **Refresh token rotación** automática (cada use genera nuevo refresh + nuevo access)
- **Refresh token en httpOnly cookie** (no en body — seguro contra XSS)
- **PasswordHasher** en lugar de BCryptPasswordEncoder (flexible, usa BCrypt internamente)
- **LoginRequest record** (request) vs LoginRequestDto (doc pedagógico)
- **AuthResponse record** con accessToken, tokenType, expiresInSeconds, sesionId, usuario

**La tarea ya está completa.** Endpoints funcionales y seguros.

---

## ¿Qué se construyó?

Los endpoints de autenticación:
- `POST /api/v1/auth/login` — email+password → access (body) + refresh (cookie) + user data
- `POST /api/v1/auth/refresh` — usa refresh cookie automáticamente → nuevo access + nuevo refresh
- `POST /api/v1/auth/logout` — revoca sesion (refresh futuro falla)
- `GET /api/v1/auth/me` — info del usuario autenticado

---

## Conceptos clave (referencia)

### Login flow
```
1. Cliente: POST /api/v1/auth/login { "email": "...", "password": "..." }
2. Backend:
   - Busca usuario por email
   - Valida password con PasswordHasher.matches()
   - Crea Sesion en BD (con sesionId, IP, User-Agent)
   - JwtService.issue() emite TokenPair (access + refresh)
   - Guarda hash del refresh en Sesion (nunca el token plano)
   - Retorna: 200 + body { accessToken, tokenType, expiresInSeconds, sesionId, usuario }
   - Setea SET-COOKIE header con refresh token (httpOnly, Secure, SameSite=Strict)
3. Cliente:
   - Guarda accessToken en memoria JS
   - Browser automáticamente guarda refresh en httpOnly cookie
```

### Refresh token — ¿qué es?
Es un JWT (NO UUID como el doc dice) que se guarda como hash en la Sesion BD. Sirve para obtener nuevo access + nuevo refresh cuando el access expira. TTL: 7 días.

### httpOnly cookie
- Browser envía automáticamente en every request (con `withCredentials: true`)
- JavaScript no puede leerla (protección XSS)
- Solo HTTPS en producción

### IP tracking
Cada login registra IP cliente + User-Agent para auditoría/seguridad.

---

## Archivos implementados (no necesitas hacer nada)

Ya existen en `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/`:

```
application/
  AuthService.java          ← login(), refresh(), logout(), hashOf()
web/
  AuthController.java       ← endpoints POST /login, /refresh, /logout, GET /me
  (DTOs son records, no clases viejas)
data/ (frontend)
  auth.repository.ts        ← HTTP calls
  auth.service.ts           ← orchestration
```

### DTOs (records)

LoginRequest (request):
```java
record LoginRequest(String email, String password)
```

RefreshRequest (request, opcional):
```java
record RefreshRequest(String refreshToken) // puede ir en body o venir de cookie
```

AuthResponse (response):
```java
record AuthResponse(String accessToken, String tokenType,
                   long expiresInSeconds, String sesionId,
                   UsuarioDto usuario)
```

### AuthService (ya implementado)

Ubicación: `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/application/AuthService.java`

Métodos clave:

1. **login(email, password, ipCliente, userAgent)** → TokenPair
   - Busca usuario por email
   - Valida password con PasswordHasher (que usa BCrypt)
   - Crea Sesion en BD con ipCliente, userAgent
   - JwtService.issue() emite access + refresh
   - Guarda refreshHash (HMAC-SHA256, no token plano) en Sesion
   - Retorna TokenPair

2. **refresh(refreshToken)** → TokenPair
   - Parsea refreshToken como JWT
   - Valida "typ" claim == "refresh"
   - Busca Sesion por refreshHash
   - Verifica que sesion no está revocada ni expirada
   - Emite nuevo par (con mismo sesionId)
   - Rota refresh: guarda nuevo hash, viejo invalida

3. **logout(refreshToken)**
   - Marca sesion como revocado
   - Refreshes futuros fallan

4. **hashOf(token)** private
   - HMAC-SHA256(token + refreshPepper)
   - No BCrypt (refresh es corto y lo rotamos rápido)

### AuthController (ya implementado)

Ubicación: `backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/web/AuthController.java`

Endpoints:

```
POST /api/v1/auth/login
  Body: { "email": "...", "password": "..." }
  Response: 200 + AuthResponse { accessToken, tokenType, expiresInSeconds, sesionId, usuario }
  Side effect: Sets SET-COOKIE header con refresh token (httpOnly)

POST /api/v1/auth/refresh
  Body: {} (o { "refreshToken": "..." } si no confías en cookies)
  Browser: envía refresh cookie automáticamente
  Response: 200 + nuevo AuthResponse
  Side effect: nuevo refresh en cookie (rotación)

POST /api/v1/auth/logout
  Body: {} (opcional)
  Requires: valid access token
  Response: 204 No Content
  Side effect: marca sesion como revocado

GET /api/v1/auth/me
  Requires: valid access token
  Response: 200 + UsuarioDto (datos usuario autenticado)
```

Helper private:
- `buildRefreshCookie(token)` → Set-Cookie header con flags de seguridad
- `readRefresh(req, body)` → extrae refresh de cookie O body (fallback)

---

## Cómo verificar que funciona

**1. Asegúrate de que tienes un usuario en BD (Liquibase lo crea en dev).**

**2. Login:**
```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@electoral.ec","password":"Password123"}' \
  -v
```

Respuesta esperada:
```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "sesionId": "550e8400-e29b-41d4-a716-446655440000",
  "usuario": { "id": 1, "email": "test@electoral.ec", "rol": "OBSERVER", "permisos": [...] }
}
```

Headers response incluye:
```
Set-Cookie: refresh_token=eyJ...; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800
```

**3. Refresh (browser envía cookie automáticamente):**
```bash
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H "Cookie: refresh_token=eyJ..." \
  -v
```

**4. Con accessToken en requests posteriores:**
```bash
curl http://localhost:8081/api/observer/mis-actas \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

---

## Notas

- **T-BE-09 ya está completado.**
- Refresh token se envía automáticamente por browser en httpOnly cookie (con `withCredentials: true`)
- Si el frontend no envía `withCredentials: true`, las cookies no viajan ni se guardan
- Cada refresh genera nuevo refresh (rotación automática)
