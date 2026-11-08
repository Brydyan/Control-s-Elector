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
│ [ Cancelar ]                   [ ✓ Crear y Enviar ]    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Datos a llenar

| Campo | Tipo | Validación | Ejemplo |
|-------|------|-----------|---------|
| **Nombre** | Texto | Minlength 2, max 100 | Juan |
| **Apellido** | Texto | Minlength 2, max 100 | García Muñoz |
| **Email Personal** | Email | RFC 5322 + regex | juan.garcia@example.com |
| **Rol** | Select | Enum (4 opciones) | OBSERVER |

### Flujo post-submit

```
Usuario presiona "Crear y Enviar"
          ↓
Frontend valida campos (required, email format)
          ↓
POST /api/v1/users/invite
  Body: {
    nombre: "Juan",
    apellido: "García Muñoz",
    emailPersonal: "juan.garcia@example.com",
    rol: "OBSERVER"
  }
          ↓
Backend genera:
  - username: "juagar.obs@control.ttcs"
  - temporaryPassword: "Kh7@mP9#qLx4"
  - Crea usuario en BD (tabla usuario)
  - Crea registro en user_invitations
  - Envía SMTP
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
    message: "Invitación enviada a juan.garcia@example.com"
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
      <div class="logo">🗳️ Control Electoral</div>
      <p style="margin-top: 10px; font-size: 14px;">Plataforma de Escrutinio Cívico</p>
    </div>

    <!-- Content -->
    <div class="content">
      <div class="section">
        <h2 style="color: #1f2937; margin-bottom: 10px;">¡Bienvenido, Juan!</h2>
        <p style="color: #4b5563; line-height: 1.6;">
          Tu cuenta de usuario ha sido creada exitosamente en Control Electoral.
          A continuación encontrarás tus credenciales de acceso temporal.
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
          <a href="https://control.brydyan.me" class="cta-button">
            Acceder a Control Electoral
          </a>
        </div>
      </div>

      <!-- Next Steps -->
      <div class="section">
        <h3 style="color: #1f2937;">Próximos Pasos:</h3>
        <ol style="color: #4b5563; line-height: 1.8;">
          <li>Abre el enlace de acceso arriba</li>
          <li>Inicia sesión con usuario y contraseña temporal</li>
          <li>Dirígete a <strong>Mi Perfil → Seguridad</strong></li>
          <li>Cambia tu contraseña a una nueva (más de 12 caracteres, combinada)</li>
          <li>Guarda tu nueva contraseña en lugar seguro</li>
        </ol>
      </div>

      <!-- Warning -->
      <div class="warning">
        <strong>⚠️ Importante:</strong>
        <ul style="margin: 10px 0 0 0; padding-left: 20px;">
          <li>Esta contraseña temporal expira en <strong>30 días</strong></li>
          <li>No compartas esta contraseña por email o chat</li>
          <li>Solo tú deberías conocer tu contraseña definitiva</li>
          <li>Si no solicitaste esta cuenta, contacta de inmediato</li>
        </ul>
      </div>

      <!-- Support -->
      <div class="section">
        <p style="color: #6b7280; font-size: 13px;">
          ¿Preguntas o problemas? Contacta al equipo de Control Electoral.
        </p>
      </div>
    </div>

    <!-- Footer -->
    <div class="footer">
      <p style="margin: 0;">Control Electoral CNE 🗳️ 2026</p>
      <p style="margin: 5px 0 0 0; font-size: 11px;">
        Plataforma de Escrutinio Cívico | Electorado Participativo
      </p>
    </div>
  </div>
</body>
</html>
```

### Pantalla renderizada (cómo se ve en email client)

```
┌──────────────────────────────────────────────────────┐
│  🗳️ Control Electoral                                │
│     Plataforma de Escrutinio Cívico                  │
├──────────────────────────────────────────────────────┤
│                                                      │
│ ¡Bienvenido, Juan!                                  │
│                                                      │
│ Tu cuenta de usuario ha sido creada exitosamente.   │
│                                                      │
│ ┌────────────────────────────────────────────────┐  │
│ │ 👤 Usuario (email):                            │  │
│ │ juagar.obs@control.ttcs                        │  │
│ │                                                │  │
│ │ 🔑 Contraseña (temporal):                      │  │
│ │ Kh7@mP9#qLx4                                   │  │
│ │                                                │  │
│ │ 🔗 Enlace de Acceso:                           │  │
│ │ [ Acceder a Control Electoral ]                │  │
│ └────────────────────────────────────────────────┘  │
│                                                      │
│ Próximos Pasos:                                     │
│ 1. Abre el enlace de acceso arriba                 │
│ 2. Inicia sesión con usuario y contraseña temporal │
│ 3. Dirígete a Mi Perfil → Seguridad               │
│ 4. Cambia tu contraseña a una nueva                │
│ 5. Guarda tu nueva contraseña en lugar seguro      │
│                                                      │
│ ⚠️ Importante:                                      │
│ • Esta contraseña temporal expira en 30 días       │
│ • No compartas esta contraseña por email           │
│ • Solo tú deberías conocer tu contraseña           │
│ • Si no solicitaste esta cuenta, contacta ya       │
│                                                      │
│ ─────────────────────────────────────────────────   │
│ Control Electoral CNE 🗳️ 2026                       │
│ Plataforma de Escrutinio Cívico                    │
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

**Generado Automáticamente (Backend):**
- Username: `juagar.obs@control.ttcs`
- Temporary Password: `Kh7@mP9#qLx4`

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

- ✅ **Contraseña temporal NUNCA se muestra en pantalla** (solo en email)
- ✅ **Username generado se muestra en modal de éxito** (para copiar rápido)
- ✅ **Show/Hide password toggles** en todos los campos de password
- ✅ **Real-time validation** para requisitos de contraseña
- ✅ **Rate limiting**: 1 invitación/5s por IP (backend)
- ✅ **Email validation**: RFC 5322 + regex (frontend + backend)
- ✅ **Mensajes en español** (ES como lenguaje MVP)
- ✅ **Links de email usan dominio production** (controls.brydyan.me)

---

**Status**: Ready for Frontend implementation (T-AUTH-INV-02 Builder phase)
