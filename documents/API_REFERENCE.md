# API Reference — User Profile & Settings

**Versión:** 1.0  
**Fecha:** 2026-11-19  
**Proyecto:** Control Electoral  
**Estado:** Implementado (MVP)

---

## Tabla de Contenidos

1. [Endpoints de Autenticación](#1-endpoints-de-autenticación)
2. [Endpoints de Perfil](#2-endpoints-de-perfil)
3. [Códigos de Error](#3-códigos-de-error)
4. [Rate Limiting](#4-rate-limiting)
5. [Seguridad](#5-seguridad)

---

## 1. ENDPOINTS DE AUTENTICACIÓN

### 1.1 POST /api/v1/auth/change-password

Permite a un usuario autenticado cambiar su contraseña.

#### Detalles

| Atributo | Valor |
|----------|-------|
| **Método** | POST |
| **Path** | `/api/v1/auth/change-password` |
| **Autenticación** | JWT (Bearer token requerido) |
| **Rate Limit** | 1 intento / hora por usuario + IP |
| **Response Content-Type** | `application/json` |

#### Request

```http
POST /api/v1/auth/change-password HTTP/1.1
Host: api.control-electoral.ec
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

{
  "currentPassword": "OldP@ssw0rd!123",
  "newPassword": "NewP@ssw0rd!456",
  "passwordConfirm": "NewP@ssw0rd!456"
}
```

#### Request Body

| Campo | Tipo | Requerido | Descripción |
|-------|------|----------|-----------|
| `currentPassword` | string | SÍ | Contraseña actual del usuario (plain text) |
| `newPassword` | string | SÍ | Nueva contraseña deseada (plain text) |
| `passwordConfirm` | string | SÍ | Confirmación de nueva contraseña (debe coincidir con `newPassword`) |

#### Validaciones

**En Frontend (UI feedback):**
- `newPassword` y `passwordConfirm` coinciden (live)
- `newPassword` cumple requisitos: 8+ chars, mayúscula, minúscula, número, especial

**En Backend:**
1. JWT válido y no expirado
2. `bcrypt.verify(currentPassword, usuario.passwordHash)` → error si no coincide
3. `newPassword` cumple complejidad (5 requisitos)
4. `!bcrypt.matches(newPassword, usuario.passwordHash)` → error si igual a actual
5. Rate limit: máximo 1/hora → error 429 si excede
6. Contraseña hasheada con bcrypt(cost 12)
7. Entrada en `audit_log` registrada

#### Response (200 OK)

```json
{
  "message": "Contraseña actualizada exitosamente",
  "lastPasswordChangeAt": "2026-11-19T14:32:45Z"
}
```

#### Response (400 Bad Request)

```json
{
  "error": "Contraseña actual incorrecta",
  "code": "INVALID_CURRENT_PASSWORD"
}
```

Otros errores 400 posibles:
- `"Nueva contraseña no puede ser igual a la actual"` → code: `PASSWORD_UNCHANGED`
- `"Contraseña debe tener 8+ caracteres"` → code: `PASSWORD_TOO_SHORT`
- `"Contraseña debe contener mayúscula, minúscula, número y carácter especial"` → code: `PASSWORD_INVALID_COMPLEXITY`
- `"Las contraseñas no coinciden"` → code: `PASSWORD_MISMATCH`

#### Response (401 Unauthorized)

```json
{
  "error": "Token inválido o expirado",
  "code": "INVALID_TOKEN"
}
```

#### Response (429 Too Many Requests)

```json
{
  "error": "Demasiados intentos. Reintente más tarde",
  "code": "RATE_LIMIT_EXCEEDED",
  "retryAfter": 3600
}
```

#### Ejemplo con curl

```bash
curl -X POST https://api.control-electoral.ec/api/v1/auth/change-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "currentPassword": "OldP@ssw0rd!123",
    "newPassword": "NewP@ssw0rd!456",
    "passwordConfirm": "NewP@ssw0rd!456"
  }'
```

#### Audit Log

Se registra entrada con:
- `usuario_id`: UUID del usuario
- `accion`: `"PASSWORD_CHANGED"`
- `detalles`: `"Usuario cambió contraseña exitosamente"`
- `timestamp`: ISO 8601
- `ip_address`: Dirección IP del request

---

### 1.2 GET /api/v1/user/profile

Obtiene el perfil del usuario autenticado.

#### Detalles

| Atributo | Valor |
|----------|-------|
| **Método** | GET |
| **Path** | `/api/v1/user/profile` |
| **Autenticación** | JWT (Bearer token requerido) |
| **Response Content-Type** | `application/json` |

#### Request

```http
GET /api/v1/user/profile HTTP/1.1
Host: api.control-electoral.ec
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### Response (200 OK)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nombre": "Juan Pérez García",
  "emailControl": "juan.perez@control-electoral.ec",
  "emailPersonal": "jpg.personal@gmail.com",
  "rol": "OBSERVER",
  "estado": "ACTIVO",
  "lastPasswordChangeAt": "2026-11-15T10:00:00Z"
}
```

#### Response Body

| Campo | Tipo | Nullable | Descripción |
|-------|------|----------|-----------|
| `id` | UUID | NO | ID único del usuario |
| `nombre` | string | NO | Nombre completo del usuario |
| `emailControl` | string | NO | Email corporativo (immutable) |
| `emailPersonal` | string | SÍ | Email personal (opcional) |
| `rol` | string (enum) | NO | Rol del usuario: `OBSERVER`, `COORDINATOR`, `MASTER`, `ADMIN` |
| `estado` | string (enum) | NO | Estado: `ACTIVO`, `INACTIVO`, `SUSPENDIDO` |
| `lastPasswordChangeAt` | ISO 8601 timestamp | SÍ | Última actualización de contraseña |

#### Response (401 Unauthorized)

```json
{
  "error": "Token inválido o expirado",
  "code": "INVALID_TOKEN"
}
```

#### Ejemplo con curl

```bash
curl -X GET https://api.control-electoral.ec/api/v1/user/profile \
  -H "Authorization: Bearer <access_token>"
```

---

## 2. ENDPOINTS DE PERFIL

### 2.1 PUT /api/v1/user/update-personal-email

Actualiza el email personal del usuario autenticado.

#### Detalles

| Atributo | Valor |
|----------|-------|
| **Método** | PUT |
| **Path** | `/api/v1/user/update-personal-email` |
| **Autenticación** | JWT (Bearer token requerido) |
| **Rate Limit** | 3 intentos / hora por usuario + IP |
| **Response Content-Type** | `application/json` |

#### Request

```http
PUT /api/v1/user/update-personal-email HTTP/1.1
Host: api.control-electoral.ec
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

{
  "emailPersonal": "nuevo.email@example.com",
  "confirmPassword": "CurrentP@ssw0rd!"
}
```

#### Request Body

| Campo | Tipo | Requerido | Descripción |
|-------|------|----------|-----------|
| `emailPersonal` | string | SÍ | Nuevo email personal (debe ser único, válido RFC 5322) |
| `confirmPassword` | string | SÍ | Contraseña actual (para confirmar identidad, previene CSRF) |

#### Validaciones

**En Backend:**
1. JWT válido y no expirado
2. `bcrypt.verify(confirmPassword, usuario.passwordHash)` → error si no coincide
3. `validateEmailFormat(emailPersonal)` → RFC 5322 (simple regex)
4. `!emailPersonal.equals(usuario.emailControl)` → error si igual a email control
5. `!existsOtherUserWithEmail(emailPersonal, userId)` → error si duplicado
6. Rate limit: máximo 3/hora → error 429 si excede
7. Email actualizado en BD
8. Entrada en `audit_log` registrada

#### Response (200 OK)

```json
{
  "message": "Email personal actualizado exitosamente",
  "emailPersonal": "nuevo.email@example.com"
}
```

#### Response (400 Bad Request)

```json
{
  "error": "Contraseña incorrecta",
  "code": "INVALID_PASSWORD"
}
```

Otros errores 400 posibles:
- `"Formato de email inválido"` → code: `INVALID_EMAIL_FORMAT`
- `"Email personal no puede ser igual a email control"` → code: `EMAIL_EQUALS_CONTROL`

#### Response (409 Conflict)

```json
{
  "error": "Email ya está registrado por otro usuario",
  "code": "EMAIL_ALREADY_EXISTS"
}
```

#### Response (429 Too Many Requests)

```json
{
  "error": "Demasiados intentos. Reintente más tarde",
  "code": "RATE_LIMIT_EXCEEDED",
  "retryAfter": 3600
}
```

#### Ejemplo con curl

```bash
curl -X PUT https://api.control-electoral.ec/api/v1/user/update-personal-email \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "emailPersonal": "nuevo.email@example.com",
    "confirmPassword": "CurrentP@ssw0rd!"
  }'
```

#### Audit Log

Se registra entrada con:
- `usuario_id`: UUID del usuario
- `accion`: `"EMAIL_UPDATED"`
- `detalles`: `"Usuario cambió email personal de X a Y"`
- `timestamp`: ISO 8601
- `ip_address`: Dirección IP del request

---

## 3. CÓDIGOS DE ERROR

### Mapa de Errores

| HTTP | Code | Significado |
|------|------|-----------|
| 400 | `INVALID_CURRENT_PASSWORD` | Contraseña actual no coincide |
| 400 | `PASSWORD_UNCHANGED` | Nueva contraseña igual a la anterior |
| 400 | `PASSWORD_TOO_SHORT` | Menos de 8 caracteres |
| 400 | `PASSWORD_INVALID_COMPLEXITY` | Falta mayúscula, minúscula, número o especial |
| 400 | `PASSWORD_MISMATCH` | Las contraseñas no coinciden |
| 400 | `INVALID_PASSWORD` | Contraseña incorrecta (en email update) |
| 400 | `INVALID_EMAIL_FORMAT` | Email no cumple RFC 5322 |
| 400 | `EMAIL_EQUALS_CONTROL` | Email personal igual a control |
| 401 | `INVALID_TOKEN` | JWT inválido o expirado |
| 409 | `EMAIL_ALREADY_EXISTS` | Email ya registrado por otro usuario |
| 429 | `RATE_LIMIT_EXCEEDED` | Límite de intentos excedido |

---

## 4. RATE LIMITING

### Límites por Endpoint

| Endpoint | Límite | Ventana | Clave |
|----------|--------|---------|-------|
| POST /auth/change-password | 1 | 1 hora | `auth:change-password:{userId}:{ipAddress}` |
| PUT /user/update-personal-email | 3 | 1 hora | `user:email-update:{userId}:{ipAddress}` |
| GET /user/profile | Unlimited | - | - |

### Comportamiento

- Si límite excedido → HTTP 429
- Header `Retry-After` incluye segundos para reintentar
- El límite se reinicia cada hora

### Ejemplo de Rate Limit Hit

```json
HTTP 429 Too Many Requests

{
  "error": "Demasiados intentos. Reintente más tarde",
  "code": "RATE_LIMIT_EXCEEDED",
  "retryAfter": 3600
}
```

Con header:
```
Retry-After: 3600
```

---

## 5. SEGURIDAD

### 5.1 Autenticación

- **Tipo:** JWT (JSON Web Token)
- **Firma:** HS256 (HMAC-SHA256)
- **TTL Access Token:** 15 minutos
- **Almacenamiento:** httpOnly cookie (no accesible desde JavaScript)
- **SameSite:** Strict (previene CSRF)

### 5.2 Encriptación

- **Contraseña:** bcrypt con salt cost 12
- **Verificación:** Siempre con `bcrypt.verify()` (timing-safe)

### 5.3 Validación de Input

- Todas las entradas se validan en backend
- Nunca confiar en validación frontend únicamente
- Sanitización de email con RFC 5322

### 5.4 Headers de Seguridad

Todos los endpoints retornan:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
```

### 5.5 Logging

Todos los cambios de autenticación se registran en `audit_log` inmutable:
- Login exitoso/fallido
- Cambio de contraseña
- Cambio de email
- Revocación de sesión
- IP address, timestamp, user agent

---

## 6. EJEMPLOS DE USO

### Escenario 1: Cambiar Contraseña Exitoso

```bash
# 1. Usuario obtiene token tras login
curl -X POST https://api.control-electoral.ec/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"juan@control.ec","password":"OldP@ssw0rd!123"}'

# Respuesta incluye access_token

# 2. Usuario cambia contraseña
curl -X POST https://api.control-electoral.ec/api/v1/auth/change-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "currentPassword": "OldP@ssw0rd!123",
    "newPassword": "NewP@ssw0rd!456",
    "passwordConfirm": "NewP@ssw0rd!456"
  }'

# Respuesta:
# {
#   "message": "Contraseña actualizada exitosamente",
#   "lastPasswordChangeAt": "2026-11-19T14:32:45Z"
# }

# 3. Usuario logout y login con nueva contraseña
curl -X POST https://api.control-electoral.ec/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"juan@control.ec","password":"NewP@ssw0rd!456"}'

# Respuesta incluye nuevo access_token
```

### Escenario 2: Actualizar Email Personal

```bash
# 1. Usuario obtiene profile actual
curl -X GET https://api.control-electoral.ec/api/v1/user/profile \
  -H "Authorization: Bearer <access_token>"

# Respuesta muestra emailPersonal actual

# 2. Usuario actualiza email personal
curl -X PUT https://api.control-electoral.ec/api/v1/user/update-personal-email \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "emailPersonal": "nuevo.email@gmail.com",
    "confirmPassword": "NewP@ssw0rd!456"
  }'

# Respuesta:
# {
#   "message": "Email personal actualizado exitosamente",
#   "emailPersonal": "nuevo.email@gmail.com"
# }

# 3. Verificar email actualizado
curl -X GET https://api.control-electoral.ec/api/v1/user/profile \
  -H "Authorization: Bearer <access_token>"

# emailPersonal es ahora "nuevo.email@gmail.com"
```

---

## 7. VERSIONING & CHANGELOG

| Versión | Fecha | Cambios |
|---------|-------|---------|
| 1.0 | 2026-11-19 | Endpoints iniciales: change-password, profile, update-personal-email |
| (Phase 2) | TBD | Email confirmation con token TTL, password reset, 2FA |

---

**Documento finalizado:** 2026-11-19  
**Próxima actualización:** Cuando se implemente Phase 2
