# Authentication & User Management

**Versión:** 1.0  
**Fecha:** 2026-11-19  
**Proyecto:** Control Electoral — User Profile & Settings  
**Estado:** Implementado

---

## 1. INTRODUCCIÓN

Este documento especifica los mecanismos de autenticación, sesión y gestión de credenciales del sistema Control Electoral. Cubre autenticación JWT, gestión de sesiones, cambio de contraseña y actualización de email personal.

---

## 2. AUTENTICACIÓN — JWT Y SESIONES

### 2.1 Flujo de Login

```
1. Usuario ingresa credenciales (email_control, password)
2. Backend valida contra tabla `usuario`
3. JWT emitido con claims: { userId, rol, estado }
4. Token almacenado en cliente (httpOnly cookie o localStorage)
5. Sesión registrada en tabla `session` con refresh_token opaco
```

### 2.2 Tokens

| Token | Tipo | TTL | Almacenamiento | Propósito |
|-------|------|-----|-----------------|----------|
| Access Token | JWT | 15 min | httpOnly cookie | Autoriza requests API |
| Refresh Token | Opaco | 8 h | BD (tabla `session`) | Renueva access token sin re-login |

### 2.3 Revocación de Sesión

- **Logout:** DELETE en tabla `session` → refresh token inválido
- **Admin revoca sesión:** MASTER ejecuta revokeSession → sesión deletada, usuario forzado a re-login
- **Expiración:** Cron job elimina sesiones > 8h

---

## 3. PASSWORD MANAGEMENT

### 3.1 Cambio de Contraseña

Los usuarios pueden cambiar su contraseña de forma segura mediante el endpoint de **User Settings**.

#### Acceso
- **Ruta:** Settings → Cambiar Contraseña
- **Endpoint:** `POST /api/v1/auth/change-password`
- **Autenticación:** JWT requerido
- **Rol:** Cualquier usuario autenticado

#### Requisitos de Contraseña

Una contraseña válida debe cumplir:

| Requisito | Descripción |
|-----------|-----------|
| **Longitud** | Mínimo 8 caracteres |
| **Mayúscula** | Al menos 1 letra mayúscula (A-Z) |
| **Minúscula** | Al menos 1 letra minúscula (a-z) |
| **Número** | Al menos 1 dígito (0-9) |
| **Carácter especial** | Al menos 1 de: `!@#$%^&*()_+-=[]{}|;:,.<>?` |

#### Validaciones en Backend

1. **Contraseña actual correcta:** `bcrypt.verify(currentPassword, usuario.passwordHash)`
   - Si no coincide → Error 400: *"Contraseña actual incorrecta"*

2. **Nueva contraseña válida:** Cumple todos los requisitos de complejidad (5 puntos arriba)
   - Si no cumple → Error 400: *"La contraseña debe contener mayúscula, minúscula, número y carácter especial"*

3. **Nueva diferente de actual:** `!bcrypt.matches(newPassword, usuario.passwordHash)`
   - Si es igual → Error 400: *"Nueva contraseña no puede ser igual a la actual"*

4. **Rate limit:** Máximo 1 cambio por hora por IP
   - Si excede → Error 429: *"Demasiados intentos. Reintente más tarde"*

#### Request

```json
POST /api/v1/auth/change-password
Content-Type: application/json
Authorization: Bearer <access_token>

{
  "currentPassword": "OldP@ssw0rd!",
  "newPassword": "NewP@ssw0rd!",
  "passwordConfirm": "NewP@ssw0rd!"
}
```

#### Response (Success)

```json
HTTP 200 OK

{
  "message": "Contraseña actualizada exitosamente",
  "lastPasswordChangeAt": "2026-11-19T14:32:00Z"
}
```

#### Response (Error)

```json
HTTP 400 Bad Request

{
  "error": "Contraseña actual incorrecta"
}
```

#### Audit Log

Cada cambio de contraseña genera una entrada en `audit_log`:

```
Tabla: audit_log
- usuario_id: UUID
- accion: "PASSWORD_CHANGED"
- detalles: "Usuario cambió contraseña"
- timestamp: 2026-11-19T14:32:00Z
- ip_address: 192.168.1.100
```

### 3.2 Resetting Contraseña (Future - Phase 2)

No está implementado en MVP. Requiere:
- Link de reset con token TTL 24h
- Validación de email único
- Notificación por email

---

## 4. PROFILE & EMAIL MANAGEMENT

### 4.1 Perfil de Usuario

#### Acceso
- **Ruta:** Settings → Mi Perfil
- **Endpoint:** `GET /api/v1/user/profile`
- **Autenticación:** JWT requerido

#### Request

```
GET /api/v1/user/profile
Authorization: Bearer <access_token>
```

#### Response

```json
HTTP 200 OK

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

**Notas:**
- `emailControl`: Email corporativo, **inmutable**
- `emailPersonal`: Email personal, **nullable**
- `lastPasswordChangeAt`: Último cambio de contraseña exitoso

### 4.2 Actualizar Email Personal

#### Acceso
- **Ruta:** Settings → Email Personal
- **Endpoint:** `PUT /api/v1/user/update-personal-email`
- **Autenticación:** JWT requerido
- **Rol:** Cualquier usuario autenticado

#### Validaciones

1. **Contraseña actual requerida:** `bcrypt.verify(confirmPassword, usuario.passwordHash)`
   - Previene CSRF en caso de sesión secuestrada
   - Si no coincide → Error 400: *"Contraseña incorrecta"*

2. **Formato de email válido:** RFC 5322
   - Si inválido → Error 400: *"Formato de email inválido"*

3. **Diferente de email control:** `!emailPersonal.equals(usuario.emailControl)`
   - Si es igual → Error 400: *"Email personal no puede ser igual a email control"*

4. **Email único en BD:** No existe otro usuario con ese email
   - Si duplicado → Error 409: *"Email ya está registrado por otro usuario"*

#### Request

```json
PUT /api/v1/user/update-personal-email
Content-Type: application/json
Authorization: Bearer <access_token>

{
  "emailPersonal": "nuevo.email@example.com",
  "confirmPassword": "CurrentP@ssw0rd!"
}
```

#### Response (Success)

```json
HTTP 200 OK

{
  "message": "Email personal actualizado exitosamente",
  "emailPersonal": "nuevo.email@example.com"
}
```

#### Response (Error)

```json
HTTP 400 Bad Request

{
  "error": "Email ya está registrado por otro usuario"
}
```

#### Audit Log

```
Tabla: audit_log
- usuario_id: UUID
- accion: "EMAIL_UPDATED"
- detalles: "Usuario cambió email personal de X a Y"
- timestamp: 2026-11-19T14:35:00Z
- ip_address: 192.168.1.100
```

---

## 5. SEGURIDAD

### 5.1 Hashing de Contraseña

- **Algoritmo:** bcrypt con salt cost 12
- **Verificación:** Cada login y cambio de contraseña usan `bcrypt.verify()`
- **Never stored plain:** Jamás se almacena contraseña en texto plano

### 5.2 Rate Limiting

| Acción | Límite | Ventana |
|--------|--------|---------|
| Login fallido | 5 intentos | 15 min |
| Cambio de contraseña | 1 intento | 1 hora |
| Cambio de email | 3 intentos | 1 hora |

### 5.3 Sesión Segura

- **HTTPOnly Cookie:** Access token no accesible desde JavaScript
- **SameSite=Strict:** Previene CSRF
- **Revocación instant:** Logout/revoke elimina sesión inmediatamente
- **Timeout inactividad:** 30 min (opcional, Phase 2)

---

## 6. FLUJOS DE USUARIO

### 6.1 Cambio de Contraseña — Flujo Completo

```
Usuario en Settings → "Cambiar Contraseña"
    ↓
Ingresa contraseña actual (required)
    ↓
Ingresa nueva contraseña + confirmación
    ↓
Frontend valida requisitos (live feedback)
    ↓
Submit → Backend valida
    ├─ Contraseña actual OK? NO → Mostrar error 400
    ├─ Nueva válida? NO → Mostrar error 400
    ├─ Nueva ≠ Actual? NO → Mostrar error 400
    ├─ Rate limit OK? NO → Mostrar error 429
    └─ TODO OK? → Hash con bcrypt(12) → Guardar → Audit log → Success
    ↓
Mostrar: "Contraseña actualizada"
↓
Limpiar formulario
```

### 6.2 Actualizar Email Personal — Flujo Completo

```
Usuario en Settings → "Email Personal"
    ↓
Muestra email actual (read-only)
    ↓
Ingresa nuevo email
    ↓
Ingresa contraseña (required)
    ↓
Submit → Backend valida
    ├─ Contraseña OK? NO → Error 400
    ├─ Email válido? NO → Error 400
    ├─ Email ≠ control? NO → Error 400
    ├─ Email único? NO → Error 409
    └─ TODO OK? → Guardar → Audit log → Success
    ↓
Mostrar: "Email actualizado"
↓
Actualizar perfil mostrado
```

---

## 7. COMPLIANCE & AUDITORÍA

### 7.1 Logging de Eventos

Cada evento de autenticación se registra en `audit_log`:

| Evento | Detalles |
|--------|----------|
| LOGIN_SUCCESS | usuario_id, ip_address, user_agent |
| LOGIN_FAILED | email, motivo (usuario no existe, contraseña inválida), ip_address |
| LOGOUT | usuario_id, ip_address |
| PASSWORD_CHANGED | usuario_id, ip_address, timestamp |
| EMAIL_UPDATED | usuario_id, old_email → new_email, ip_address |
| SESSION_REVOKED | usuario_id, reason (admin revoke, timeout, etc) |

### 7.2 Retención de Datos

- **Audit log:** 90 días (configurable)
- **Session tokens:** Eliminados al logout o 8h de inactividad
- **Contraseña anterior:** Nunca almacenada (hash actual sobrescribe)

---

## 8. TESTING

### 8.1 Unit Tests

**Cobertura esperada:** 95%+

- Validación de complejidad de contraseña (todos 5 requisitos)
- Comparación bcrypt
- Validación de formato de email
- Detección de duplicados
- Rate limiting

### 8.2 Integration Tests

- POST /auth/change-password → 200 OK
- POST /auth/change-password → 400 Bad Request (contraseña incorrecta)
- POST /auth/change-password → 429 Too Many Requests (rate limit)
- PUT /user/update-personal-email → 200 OK
- PUT /user/update-personal-email → 409 Conflict (email duplicado)
- GET /user/profile → retorna datos correctos

### 8.3 E2E Tests (Phase 2)

- Login → Settings → Cambiar Contraseña → Logout → Login con nueva contraseña ✓

---

## 9. NOTAS DE DISEÑO

### ¿Por qué pedir contraseña en cambio de email?

Prevenir CSRF. Si un atacante logra una sesión abierta, debe confirmar la identidad del usuario (conocer contraseña actual) antes de cambiar email.

### ¿Email personal puede quedar null?

SÍ. Es un contacto personal opcional. Usuario elige actualizar o dejar vacío.

### ¿Phase 2: Email confirmation?

Sí. MVP requiere cambio inmediato. Phase 2 añadirá:
- Link de confirmación con token TTL 24h
- Notificación a email anterior
- Actualización solo tras validar nuevo email

---

## 10. REFERENCIAS

| Documento | Ubicación | Propósito |
|-----------|-----------|----------|
| SRS | `/documents/SRS.md` | Requisitos generales del sistema |
| API_REFERENCE.md | `/documents/API_REFERENCE.md` | Endpoints técnicos detallados |
| SETTINGS_UX.md | `/documents/SETTINGS_UX.md` | Flujos UX, mockups, validaciones frontend |
| Task Spec | `/openspec/changes/back/2026-11-19-user-profile-settings/tasks.md` | Detalles de implementación |

---

**Documento finalizado:** 2026-11-19  
**Próxima actualización:** Cuando se implemente Phase 2 (email confirmation)
