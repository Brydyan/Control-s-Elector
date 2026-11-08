# UI Mockup — T-AUTH-INV-02: User Invitations + SMTP

Esqueletos visuales para creación de usuarios y flujo de invitación.

---

## 1. FORMULARIO CREAR USUARIO (MASTER Admin Panel)

### Ubicación
```
/master/usuarios → Botón "➕ Crear Usuario" → Modal/Page
```

### Pantalla: "Crear Nuevo Usuario"

```
┌─────────────────────────────────────────────────────────┐
│ 🔐 Crear Nuevo Usuario                         [X]       │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Información Personal                                    │
│ ─────────────────────────────────────────────────────  │
│                                                         │
│ Nombre *                                                │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Juan                                                │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ Apellido *                                              │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ García Muñoz                                        │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ Email Personal (Contacto) *                             │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ juan.garcia@example.com                             │ │
│ └─────────────────────────────────────────────────────┘ │
│ ℹ️ Se usará para enviar credenciales de acceso         │
│                                                         │
│ Rol *                                                   │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Selecciona rol... ▼                                 │ │
│ └─────────────────────────────────────────────────────┘ │
│ • OBSERVER      (veedor)                               │
│ • COORDINATOR   (coordinador)                          │
│ • MASTER        (administrador)                        │
│ • ADMIN         (auditoria)                            │
│                                                         │
│ ─────────────────────────────────────────────────────  │
│                                                         │
│ Opciones de Invitación                                  │
│ ─────────────────────────────────────────────────────  │
│                                                         │
│ ☑ Enviar Invitación por Email                          │
│   📧 Se enviará el email con credenciales temporales    │
│   Si desactivas, solo se crea el usuario sin email.    │
│                                                         │
│ ─────────────────────────────────────────────────────  │
│                                                         │
│ [ Cancelar ]                   [ ✓ Crear Usuario ]    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Datos a llenar

| Campo | Tipo | Validación | Ejemplo | Obligatorio |
|-------|------|-----------|---------|-----------|
| **Nombre** | Texto | Minlength 2, max 100 | Juan | ✅ |
| **Apellido** | Texto | Minlength 2, max 100 | García Muñoz | ✅ |
| **Email Personal** | Email | RFC 5322 + regex | juan.garcia@example.com | ✅ |
| **Rol** | Select | Enum (4 opciones) | OBSERVER | ✅ |
| **Enviar Invitación** | Toggle/Checkbox | Boolean | ON/OFF | ❌ (default: ON) |

**Comportamiento del toggle:**
- **ON (default):** Envía email con credenciales al crear usuario
- **OFF:** Solo crea usuario sin enviar email (útil si se enviará después manualmente)

### Flujo post-submit

```
Usuario presiona "Crear Usuario"
          ↓
Frontend valida campos (required, email format)
          ↓
POST /api/v1/users/invite
  Body: {
    nombre: "Juan",
    apellido: "García Muñoz",
    emailPersonal: "juan.garcia@example.com",
    rol: "OBSERVER",
    sendEmail: true  (depende del toggle)
  }
          ↓
Backend genera:
  - username: "juagar.obs@control.ttcs"
  - temporaryPassword: "Kh7@mP9#qLx4"
  - Crea usuario en BD (tabla usuario)
  - Crea registro en user_invitations (status: "INVITED" o "CREATED")
  - Si sendEmail=true: Envía SMTP con credenciales
  - Si sendEmail=false: NO envía email (se puede hacer después)
          ↓
Response 201 Created:
  {
    success: true,
    usuario: {
      id: "uuid-1234",
      nombre: "Juan",
      apellido: "García Muñoz",
      emailPersonal: "juan.garcia@example.com",
      rol: "OBSERVER",
      username: "juagar.obs@control.ttcs"
    },
    message: sendEmail 
      ? "Invitación enviada a juan.garcia@example.com"
      : "Usuario creado. Puedes enviar la invitación después."
  }
          ↓
Frontend muestra modal de éxito
```

### Modal de Éxito (Post-Invitación)

```
┌─────────────────────────────────────────────────────────┐
│ ✅ Invitación Enviada                                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Usuario creado exitosamente                             │
│                                                         │
│ 📧 Credenciales enviadas a:                             │
│    juan.garcia@example.com                              │
│                                                         │
│ Información de Acceso (generada automáticamente)        │
│ ─────────────────────────────────────────────────────  │
│                                                         │
│ Usuario (username):                                     │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ juagar.obs@control.ttcs                             │ │
│ │                                              [Copiar] │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ ⚠️ Nota: La contraseña temporal NO se muestra aquí.    │
│    El usuario la recibió por email.                     │
│    No la compartas fuera del email.                     │
│                                                         │
│ [ Cerrar ]              [ Crear Otro Usuario ]          │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 1.5 TABLA USUARIOS (MASTER) - Con Acciones de Re-envío

### Ubicación
```
/master/usuarios → Tabla con columna "Acciones"
```

### Pantalla: "Gestión de Usuarios" (Tabla)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ 🔐 Gestión de Usuarios                                  [➕ Crear Usuario]   │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│ Nombre         │ Email             │ Rol       │ Estado      │ Acciones    │
├────────────────┼───────────────────┼───────────┼─────────────┼─────────────┤
│ Juan García    │ juan@example.com  │ OBSERVER  │ 🟢 INVITED  │ ⋮          │
│                │                   │           │             │ • Ver      │
│                │                   │           │             │ • Editar   │
│                │                   │           │             │ • 📧 Re-enviar
│                │                   │           │             │ • Deshabilitar
│                │                   │           │             │ • Eliminar │
├────────────────┼───────────────────┼───────────┼─────────────┼─────────────┤
│ María López    │ maria@example.com │ COORD     │ 🟡 EXPIRED  │ ⋮          │
│                │                   │           │             │ • Ver      │
│                │                   │           │             │ • Editar   │
│                │                   │           │             │ • 📧 Re-enviar
│                │                   │           │             │ • Deshabilitar
│                │                   │           │             │ • Eliminar │
├────────────────┼───────────────────┼───────────┼─────────────┼─────────────┤
│ Pedro García   │ pedro@example.com │ MASTER    │ 🟢 ACTIVE   │ ⋮          │
│                │                   │           │             │ • Ver      │
│                │                   │           │             │ • Editar   │
│                │                   │           │             │ • Deshabilitar
│                │                   │           │             │ • Eliminar │
│                │                   │           │             │ (Re-enviar N/A)
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Estados de Invitación en Tabla

| Estado | Icono | Descripción | Puede Re-enviar |
|--------|-------|-------------|-----------------|
| **INVITED** | 🟢 Verde | Email enviado, usuario aún no se loguea | ✅ SÍ |
| **EXPIRED** | 🟡 Amarillo | Invitación expirada (>30 días) | ✅ SÍ |
| **ACTIVE** | 🟢 Verde | Usuario logueado exitosamente | ❌ NO |
| **DISABLED** | 🔴 Rojo | Usuario deshabilitado por MASTER | ❌ NO |

### Botón Menú Acciones (Dropdown)

```
┌─────────────────────────────────────────────────────┐
│ Acciones para: Juan García (juagar.obs@control.ttcs)│
├─────────────────────────────────────────────────────┤
│ • 👁️  Ver Detalles                                  │
│ • ✏️  Editar Información                            │
│ ─────────────────────────────────────────────────── │
│ • 📧 Re-enviar Invitación     ← SI status=INVITED   │
│ ─────────────────────────────────────────────────── │
│ • 🔒 Deshabilitar Usuario                           │
│ • 🗑️  Eliminar Usuario (soft-delete)               │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Modal Re-enviar Invitación

```
┌─────────────────────────────────────────────────────────┐
│ 📧 Re-enviar Invitación                         [X]      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ ¿Re-enviar credenciales de acceso a:                   │
│ juan.garcia@example.com?                               │
│                                                         │
│ Usuario: juagar.obs@control.ttcs                        │
│ Rol: OBSERVER                                           │
│                                                         │
│ ℹ️ Se generará una nueva contraseña temporal.          │
│    La anterior será invalidada.                         │
│                                                         │
│ ☑ Entiendo que se enviará una nueva invitación         │
│                                                         │
│ [ Cancelar ]           [ ✓ Re-enviar por Email ]       │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Flujo Re-enviar Invitación

```
Usuario presiona "Re-enviar Invitación" desde tabla
          ↓
Frontend muestra modal confirmatorio
          ↓
Usuario presiona "Re-enviar por Email"
          ↓
POST /api/v1/users/{id}/resend-invitation
  Header: Authorization: Bearer {jwt_token}
          ↓
Backend:
  - Valida que usuario tenga status INVITED o EXPIRED
  - Genera NUEVA temporaryPassword
  - Actualiza registro en user_invitations (resetea timestamp)
  - Envía SMTP con NUEVAS credenciales
  - Log auditoría: ACTION_INVITATION_RESENT + usuario + timestamp
          ↓
Response 200 OK:
  {
    success: true,
    message: "Invitación re-enviada a juan.garcia@example.com",
    newInvitationSentAt: "2026-11-08T15:30:00Z",
    expiresAt: "2026-12-08T15:30:00Z"
  }
          ↓
Frontend muestra toast: "✅ Invitación re-enviada"
Tabla se actualiza (estado, timestamp)
```

### Modal Éxito (Re-envío)

```
┌─────────────────────────────────────────────────────────┐
│ ✅ Invitación Re-enviada                               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Credenciales de acceso enviadas nuevamente a:          │
│ juan.garcia@example.com                                 │
│                                                         │
│ Información:                                            │
│ • Se generó una nueva contraseña temporal              │
│ • La anterior ya no es válida                          │
│ • Esta invitación expira en 30 días                    │
│ • Enviado: 2026-11-08 15:30                            │
│                                                         │
│                        [ Cerrar ]                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 2. EMAIL INVITACIÓN SMTP (Lo que recibe el usuario)

### Asunto
```
Bienvenido a Control Electoral - Tus Credenciales de Acceso
```

### Cuerpo HTML

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    .container { max-width: 600px; margin: 0 auto; }
    .header { background-color: #1e40af; color: white; padding: 40px; text-align: center; }
    .logo { font-size: 28px; font-weight: bold; }
    .content { padding: 40px; background-color: #f9fafb; }
    .section { margin-bottom: 30px; }
    .credentials { background-color: #ffffff; border-left: 4px solid #1e40af; padding: 20px; margin: 20px 0; }
    .credential-item { margin: 15px 0; }
    .credential-label { font-weight: bold; color: #374151; }
    .credential-value { 
      background-color: #f3f4f6; 
      padding: 12px; 
      border-radius: 4px; 
      font-family: "Courier New", monospace; 
      word-break: break-all;
    }
    .cta-button {
      display: inline-block;
      background-color: #1e40af;
      color: white;
      padding: 12px 30px;
      border-radius: 4px;
      text-decoration: none;
      font-weight: bold;
      margin: 20px 0;
    }
    .warning { 
      background-color: #fef3c7; 
      border-left: 4px solid #f59e0b; 
      padding: 15px; 
      margin: 20px 0;
      border-radius: 4px;
    }
    .footer { 
      text-align: center; 
      padding: 20px; 
      color: #6b7280; 
      font-size: 12px; 
      border-top: 1px solid #e5e7eb;
    }
  </style>
</head>
<body>
  <div class="container">
    <!-- Header -->
    <div class="header">
      <div class="logo">🗳️ Control's Elector</div>
      <p style="margin-top: 10px; font-size: 14px;">Plataforma de Observación Electoral</p>
    </div>

    <!-- Content -->
    <div class="content">
      <div class="section">
        <h2 style="color: #1f2937; margin-bottom: 10px;">¡Bienvenido, Juan!</h2>
        <p style="color: #4b5563; line-height: 1.6;">
          Tu cuenta ha sido <strong>creada pero está INACTIVA</strong>. Para activarla y acceder a Control's Elector, debes cambiar tu contraseña temporal usando las credenciales que encontrarás a continuación.
        </p>
      </div>

      <!-- Credentials -->
      <div class="credentials">
        <h3 style="color: #1e40af; margin-top: 0;">Tus Credenciales Temporales</h3>
        
        <div class="credential-item">
          <div class="credential-label">👤 Usuario (email):</div>
          <div class="credential-value">juagar.obs@control.ttcs</div>
        </div>

        <div class="credential-item">
          <div class="credential-label">🔑 Contraseña (temporal):</div>
          <div class="credential-value">Kh7@mP9#qLx4</div>
        </div>

        <div class="credential-item">
          <div class="credential-label">🔗 Enlace de Acceso:</div>
          <a href="https://app.controls.brydyan.me" class="cta-button">
            Acceder a Control's Elector
          </a>
        </div>
      </div>

      <!-- Next Steps -->
      <div class="section">
        <h3 style="color: #1f2937;">Próximos Pasos para Activar tu Cuenta:</h3>
        <ol style="color: #4b5563; line-height: 1.8;">
          <li>Abre el enlace de acceso arriba</li>
          <li>Inicia sesión con usuario y contraseña temporal</li>
          <li>Dirígete a <strong>Mi Perfil → Seguridad</strong></li>
          <li><strong>Cambia tu contraseña</strong> a una nueva (mín. 12 caracteres, mayúscula + minúscula + número + símbolo) <em style="color: #059669;">— Esto activa tu cuenta</em></li>
          <li>Guarda tu nueva contraseña en lugar seguro</li>
        </ol>
      </div>

      <!-- Warning -->
      <div class="warning">
        <strong>⚠️ Importante:</strong>
        <ul style="margin: 10px 0 0 0; padding-left: 20px;">
          <li>Esta contraseña temporal expira en <strong>24 horas</strong>. Activa tu cuenta ahora.</li>
          <li>No compartas esta contraseña por email o chat</li>
          <li>Solo tú deberías conocer tu contraseña definitiva</li>
          <li>Si no solicitaste esta cuenta, contacta de inmediato</li>
        </ul>
      </div>

      <!-- Support -->
      <div class="section">
        <p style="color: #6b7280; font-size: 13px;">
          ¿Preguntas o problemas? Contacta al equipo de Control's Elector.
        </p>
      </div>
    </div>

    <!-- Footer -->
    <div class="footer">
      <p style="margin: 0;">Control's Elector 🗳️ 2026</p>
      <p style="margin: 5px 0 0 0; font-size: 11px;">
        Plataforma de Observación Electoral | Participación Cívica
      </p>
    </div>
  </div>
</body>
</html>
```

### Pantalla renderizada (cómo se ve en email client)

```
┌──────────────────────────────────────────────────────┐
│  🗳️ Control's Elector                               │
│     Plataforma de Observación Electoral              │
├──────────────────────────────────────────────────────┤
│                                                      │
│ ¡Bienvenido, Juan!                                  │
│                                                      │
│ Tu cuenta está CREADA pero INACTIVA.                │
│ Para activarla, cambia tu contraseña temporal.      │
│                                                      │
│ ┌────────────────────────────────────────────────┐  │
│ │ 👤 Usuario (email):                            │  │
│ │ juagar.obs@control.ttcs                        │  │
│ │                                                │  │
│ │ 🔑 Contraseña (temporal):                      │  │
│ │ Kh7@mP9#qLx4                                   │  │
│ │                                                │  │
│ │ 🔗 Enlace de Acceso:                           │  │
│ │ [ Acceder a Control's Elector ]                │  │
│ └────────────────────────────────────────────────┘  │
│                                                      │
│ Próximos Pasos para Activar tu Cuenta:             │
│ 1. Abre el enlace de acceso arriba                 │
│ 2. Inicia sesión con usuario y contraseña temporal │
│ 3. Dirígete a Mi Perfil → Seguridad               │
│ 4. Cambia tu contraseña (esto activa tu cuenta)   │
│ 5. Guarda tu nueva contraseña en lugar seguro      │
│                                                      │
│ ⚠️ Importante:                                      │
│ • Esta contraseña temporal expira en 24 horas      │
│ • No compartas esta contraseña por email           │
│ • Solo tú deberías conocer tu contraseña           │
│ • Si no solicitaste esta cuenta, contacta ya       │
│                                                      │
│ ─────────────────────────────────────────────────   │
│ Control's Elector 🗳️ 2026                           │
│ Plataforma de Observación Electoral                 │
│                                                      │
└──────────────────────────────────────────────────────┘
```

---

## 3. FORMULARIO PRIMER LOGIN (Change Password - First Time)

### Ubicación
```
Usuario se loguea por primera vez con credenciales temporales
          ↓
Sistema detecta: "first_login = true" en JWT
          ↓
Redirige a: /auth/change-password-required
```

### Pantalla: "Cambiar Contraseña Obligatorio"

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│                 🔐 Cambiar Contraseña                   │
│                                                         │
│            ⚠️ Se requiere cambiar contraseña            │
│              antes de continuar usando la app           │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Contraseña Actual (temporal) *                          │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ ••••••••••••                        [👁️ Mostrar]   │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ Nueva Contraseña *                                      │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ ••••••••••••                        [👁️ Mostrar]   │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ Requisitos de Contraseña:                               │
│ ☑ Mínimo 12 caracteres                                 │
│ ☐ Mayúsculas (A-Z)                                     │
│ ☐ Minúsculas (a-z)                                     │
│ ☐ Números (0-9)                                        │
│ ☐ Caracteres especiales (!@#$%^&*)                     │
│                                                         │
│ Confirmar Nueva Contraseña *                            │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ ••••••••••••                        [👁️ Mostrar]   │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ ☑ Entiendo que esta es mi contraseña definitiva y      │
│   la guardaré en lugar seguro                          │
│                                                         │
│                    [ ✓ Actualizar Contraseña ]         │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Validaciones en Tiempo Real

```
┌─────────────────────────────────────────────────────────┐
│ Nueva Contraseña: MyStr0ng!Pass                         │
│                                                         │
│ ✅ Mínimo 12 caracteres              (14 chars)        │
│ ✅ Mayúsculas (A-Z)                  (M, S, P)         │
│ ✅ Minúsculas (a-z)                  (ytrngass)        │
│ ✅ Números (0-9)                     (0)               │
│ ✅ Caracteres especiales (!@#$%^&*)  (!)               │
│                                                         │
│ Fuerza: ████████████░░  FUERTE                          │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Flujo post-submit

```
Usuario presiona "Actualizar Contraseña"
          ↓
Frontend valida:
  - Current password no vacío
  - Nueva contraseña cumple requisitos
  - Confirmación coincide con nueva
          ↓
PUT /api/v1/auth/change-password
  Body: {
    currentPassword: "Kh7@mP9#qLx4",
    newPassword: "MyStr0ng!Pass",
    confirmPassword: "MyStr0ng!Pass"
  }
  Header: Authorization: Bearer {jwt_token}
          ↓
Backend:
  - Valida JWT tiene "first_login = true"
  - Hash nueva contraseña con bcrypt
  - Actualiza tabla usuario
  - Log auditoría: ACTION_PASSWORD_CHANGED
  - Emite nuevo JWT con "first_login = false"
          ↓
Response 200 OK:
  {
    success: true,
    message: "Contraseña actualizada exitosamente",
    nextRedirect: "/dashboard"  (o ruta según rol)
  }
          ↓
Frontend redirige a dashboard/home
Usuario ya está autenticado normalmente
```

### Modal Éxito (Post-Cambio)

```
┌─────────────────────────────────────────────────────────┐
│ ✅ Contraseña Actualizada                              │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Perfecto, Juan.                                         │
│                                                         │
│ Tu contraseña ha sido actualizada exitosamente.        │
│ Ahora puedes acceder a Control Electoral normalmente.  │
│                                                         │
│ Recordatorio de seguridad:                              │
│ • Guarda tu contraseña en lugar seguro                 │
│ • No la compartas con nadie                            │
│ • Cambiala regularmente (cada 90 días)                │
│ • Si la olvidas, usa "¿Olvidaste tu contraseña?"      │
│                                                         │
│              [ Continuar a Dashboard ]                 │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## Resumen: Datos Necesarios por Pantalla

### 1️⃣ Crear Usuario (MASTER)

| Campo | Obligatorio | Tipo | Ejemplo |
|-------|-------------|------|---------|
| Nombre | ✅ | Texto | Juan |
| Apellido | ✅ | Texto | García |
| Email Personal | ✅ | Email | juan@example.com |
| Rol | ✅ | Select | OBSERVER |
| Enviar Invitación | ❌ | Toggle | ON (default) |

**Generado Automáticamente (Backend):**
- Username: `juagar.obs@control.ttcs`
- Temporary Password: `Kh7@mP9#qLx4`

### 1.5️⃣ Tabla Usuarios + Re-enviar Invitación (MASTER)

| Acción | Visible Cuando | Genera |
|--------|----------------|---------|
| **Ver Detalles** | Siempre | Usuario completo |
| **Editar** | Siempre | Formulario pre-poblado |
| **📧 Re-enviar** | Status = INVITED o EXPIRED | Nueva temporaryPassword + SMTP |
| **Deshabilitar** | Status ≠ DISABLED | Soft-delete flag |
| **Eliminar** | Siempre | Hard-delete |

**Flujo Re-envío:**
- POST `/api/v1/users/{id}/resend-invitation`
- Genera nueva temporaryPassword
- Invalida la anterior
- Envía SMTP con nuevas credenciales
- Reset expiry counter (30 días más)

### 2️⃣ Email Invitación (Auto-generado)

| Dato | Fuente | Propósito |
|------|--------|----------|
| Usuario (username) | Backend generated | Login credential |
| Contraseña Temporal | Backend generated | Temporary access |
| Login Link | Config | Quick access |
| Expiry (30 días) | Config | Security |

### 3️⃣ Primer Login - Cambiar Contraseña

| Campo | Obligatorio | Tipo | Validación |
|-------|-------------|------|-----------|
| Contraseña Actual | ✅ | Password | Debe ser contraseña temporal |
| Nueva Contraseña | ✅ | Password | ≥12 chars, upper+lower+digit+special |
| Confirmar Contraseña | ✅ | Password | Debe coincidir con nueva |
| Checkbox aceptación | ✅ | Checkbox | Obligatorio para submit |

---

## Notas de UX

### Crear Usuario
- ✅ **Contraseña temporal NUNCA se muestra en pantalla** (solo en email)
- ✅ **Username generado se muestra en modal de éxito** (para copiar rápido)
- ✅ **Toggle "Enviar Invitación"** con opción de crear sin email
- ✅ **Rate limiting**: 1 invitación/5s por IP (backend)
- ✅ **Email validation**: RFC 5322 + regex (frontend + backend)

### Tabla Usuarios
- ✅ **Columna "Estado"** muestra: INVITED (🟢), EXPIRED (🟡), ACTIVE (🟢), DISABLED (🔴)
- ✅ **Botón "Re-enviar Invitación"** solo visible si status = INVITED o EXPIRED
- ✅ **Modal confirmatorio** antes de re-enviar
- ✅ **Nueva contraseña generada** al re-enviar (antigua se invalida)
- ✅ **Expiry reset** a 30 días nuevamente

### General
- ✅ **Show/Hide password toggles** en todos los campos de password
- ✅ **Real-time validation** para requisitos de contraseña
- ✅ **Mensajes en español** (ES como lenguaje MVP)
- ✅ **Links de email usan dominio production** (controls.brydyan.me)
- ✅ **Toast notifications** para confirmaciones (re-envío, cambio de pwd)
- ✅ **Audit logging** de todas las acciones (create, resend, change-password)

---

## Backend API Endpoints Necesarios

### 1. Crear Usuario + Enviar Invitación
```http
POST /api/v1/users/invite
Authorization: Bearer {jwt_token}
Content-Type: application/json

Body:
{
  "nombre": "Juan",
  "apellido": "García",
  "emailPersonal": "juan@example.com",
  "rol": "OBSERVER",
  "sendEmail": true  (default: true)
}

Response 201 Created:
{
  "success": true,
  "usuario": {
    "id": "uuid-1234",
    "nombre": "Juan",
    "apellido": "García",
    "emailPersonal": "juan@example.com",
    "rol": "OBSERVER",
    "username": "juagar.obs@control.ttcs"
  },
  "message": "Invitación enviada a juan@example.com"
}
```

### 2. Re-enviar Invitación (Generate New Temp Password)
```http
POST /api/v1/users/{id}/resend-invitation
Authorization: Bearer {jwt_token}
Content-Type: application/json

Response 200 OK:
{
  "success": true,
  "message": "Invitación re-enviada a juan@example.com",
  "newInvitationSentAt": "2026-11-08T15:30:00Z",
  "expiresAt": "2026-12-08T15:30:00Z"
}
```

### 3. Cambiar Contraseña (Primer Login)
```http
PUT /api/v1/auth/change-password
Authorization: Bearer {jwt_token}
Content-Type: application/json

Body:
{
  "currentPassword": "Kh7@mP9#qLx4",
  "newPassword": "MyStr0ng!Pass",
  "confirmPassword": "MyStr0ng!Pass"
}

Response 200 OK:
{
  "success": true,
  "message": "Contraseña actualizada exitosamente",
  "nextRedirect": "/dashboard"
}
```

### 4. Listar Usuarios (con estado de invitación)
```http
GET /api/v1/users?page=0&size=20
Authorization: Bearer {jwt_token}

Response 200 OK:
{
  "content": [
    {
      "id": "uuid-1234",
      "nombre": "Juan",
      "apellido": "García",
      "email": "juan@example.com",
      "rol": "OBSERVER",
      "username": "juagar.obs@control.ttcs",
      "invitationStatus": "INVITED",  // INVITED, EXPIRED, ACTIVE, DISABLED
      "invitedAt": "2026-11-08T10:00:00Z",
      "invitedBy": "uuid-admin",
      "lastLogin": null,
      "enabled": true
    }
  ],
  "totalElements": 45,
  "totalPages": 3
}
```

---

**Status**: Ready for Frontend implementation (T-AUTH-INV-02 Builder phase)
