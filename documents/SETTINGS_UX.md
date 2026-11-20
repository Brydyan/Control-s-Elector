# User Settings — UX Flow & Validation Guide

**Versión:** 1.0  
**Fecha:** 2026-11-19  
**Proyecto:** Control Electoral  
**Estado:** Implementado (MVP)

---

## Tabla de Contenidos

1. [Visión General](#1-visión-general)
2. [Arquitectura de Componentes](#2-arquitectura-de-componentes)
3. [Flujo: Mi Perfil](#3-flujo-mi-perfil)
4. [Flujo: Cambiar Contraseña](#4-flujo-cambiar-contraseña)
5. [Flujo: Actualizar Email](#5-flujo-actualizar-email)
6. [Validación Frontend](#6-validación-frontend)
7. [Comportamiento de Errores](#7-comportamiento-de-errores)
8. [Accesibilidad & Responsive](#8-accesibilidad--responsive)

---

## 1. VISIÓN GENERAL

### 1.1 Objetivo

La página **User Settings** es el centro de control para que usuarios autenticados gestionen su perfil, contraseña y email personal. Implementa 3 flujos independientes en tabs:

1. **Mi Perfil** — Visualización de datos inmutables
2. **Cambiar Contraseña** — Validación compleja con requisitos en vivo
3. **Email Personal** — Cambio con confirmación de identidad

### 1.2 Ubicación en Navegación

```
Main Menu
├── Dashboard (home)
├── Actas (mis actas)
├── Settings ← Aquí
│   ├── Tab 1: Mi Perfil
│   ├── Tab 2: Cambiar Contraseña
│   └── Tab 3: Email Personal
└── Logout
```

### 1.3 Acceso

- **Ruta:** `/settings`
- **Método:** GET (carga página)
- **Autenticación:** JWT requerido
- **Rol:** Cualquier usuario (OBSERVER, COORDINATOR, MASTER, ADMIN)
- **Responsive:** Desktop, tablet, mobile

---

## 2. ARQUITECTURA DE COMPONENTES

### 2.1 Estructura de Página

```
ProfileSettingsPage (Smart Container)
├── Tab Selector (Tab Navigation)
│   ├── "Mi Perfil" (Tab 1)
│   ├── "Cambiar Contraseña" (Tab 2)
│   └── "Email Personal" (Tab 3)
│
├── Tab 1: ProfileHeaderComponent
│   ├── ReadOnlyField: Nombre
│   ├── ReadOnlyField: Email Control
│   ├── DisplayField: Último cambio contraseña
│   └── Info: "Estos campos no se pueden editar"
│
├── Tab 2: ChangePasswordFormComponent
│   ├── PasswordInput: Contraseña Actual
│   ├── PasswordInput: Nueva Contraseña (con requirements feedback)
│   ├── PasswordInput: Confirmar Contraseña
│   ├── RequirementsCheckbox (5 items):
│   │   ├── [✓] 8+ caracteres
│   │   ├── [✓] Mayúscula (A-Z)
│   │   ├── [✓] Minúscula (a-z)
│   │   ├── [✓] Número (0-9)
│   │   └── [✓] Carácter especial (!@#$%...)
│   ├── Button: Cambiar Contraseña (enabled si 5/5 OK + confirm match)
│   └── ErrorAlert (si falla backend)
│
├── Tab 3: UpdateEmailFormComponent
│   ├── ReadOnlyField: Email Control (reference)
│   ├── EmailInput: Email Personal Nuevo
│   ├── PasswordInput: Confirmar Contraseña (para CSRF)
│   ├── Button: Actualizar Email (enabled si format OK + password filled)
│   └── ErrorAlert (si falla backend)
│
└── SuccessAlert (global, desaparece después 5s)
```

### 2.2 Estado Global (en página)

```typescript
interface SettingsPageState {
  activeTab: 'profile' | 'password' | 'email';
  profile: ProfileDto | null;
  loading: boolean;
  success: { message: string; timestamp: number } | null;
  error: { message: string; code: string; timestamp: number } | null;
}
```

---

## 3. FLUJO: MI PERFIL

### 3.1 Carga Inicial

```
1. Usuario navega a /settings
2. Página carga: GET /api/v1/user/profile
   ├─ Loading spinner visible
   └─ Request incluye JWT en Authorization header
3. Backend retorna:
   {
     id: "...",
     nombre: "Juan Pérez García",
     emailControl: "juan.perez@control.ec",
     emailPersonal: "jpg.personal@gmail.com",
     rol: "OBSERVER",
     estado: "ACTIVO",
     lastPasswordChangeAt: "2026-11-15T10:00:00Z"
   }
4. Pantalla renderiza Tab 1 con datos
5. Spinner desaparece
```

### 3.2 Visualización

```
┌─────────────────────────────────────┐
│          MI PERFIL (Tab 1)          │
├─────────────────────────────────────┤
│ Nombre Completo                     │
│ Juan Pérez García                   │ (read-only)
│                                     │
│ Email de Control (Corporativo)      │
│ juan.perez@control.ec               │ (read-only)
│                                     │
│ Rol                                 │
│ Observador (OBSERVER)               │ (read-only)
│                                     │
│ Estado                              │
│ ACTIVO                              │ (read-only)
│                                     │
│ Último cambio de contraseña         │
│ 15 de noviembre de 2026, 10:00      │ (read-only)
│                                     │
│ ℹ️ Estos campos no se pueden editar.│
│    Contacta con admin para cambios. │
└─────────────────────────────────────┘
```

### 3.3 Interacción

- **Ninguna:** Tab es de solo lectura
- **Info icon:** Tooltip explica por qué no se pueden editar

---

## 4. FLUJO: CAMBIAR CONTRASEÑA

### 4.1 Entrada en Formulario

```
Campos:
1. Contraseña Actual (required)
   - Type: password
   - Placeholder: "Ingresa tu contraseña actual"
   - Focus → show character count (optional UX polish)

2. Nueva Contraseña (required)
   - Type: password
   - Placeholder: "Mínimo 8 caracteres con mayúscula, minúscula, número y especial"
   - On Input → validar requisitos en vivo
   - Cambio de color en requirements

3. Confirmar Contraseña (required)
   - Type: password
   - Placeholder: "Confirma tu nueva contraseña"
   - On Input → comparar con Nueva Contraseña
   - Red text si no coinciden

4. Requirements Checklist (live feedback)
   - [] 8 o más caracteres
   - [] Contiene mayúscula (A-Z)
   - [] Contiene minúscula (a-z)
   - [] Contiene número (0-9)
   - [] Contiene carácter especial (!@#$%^&*...)

   On each keystroke en "Nueva Contraseña" → actualizar checkmarks
```

### 4.2 Validación Frontend (Real-time)

```typescript
// Mientras usuario escribe en "Nueva Contraseña"
const requirements = {
  minLength: newPassword.length >= 8,
  hasUppercase: /[A-Z]/.test(newPassword),
  hasLowercase: /[a-z]/.test(newPassword),
  hasNumber: /\d/.test(newPassword),
  hasSpecial: /[!@#$%^&*()_+\-=\[\]{}|;:,.<>?]/.test(newPassword)
};

const isValidPassword = Object.values(requirements).every(r => r === true);
const passwordsMatch = newPassword === passwordConfirm;
const submitDisabled = !isValidPassword || !passwordsMatch;
```

### 4.3 Visualización (Desktop)

```
┌─────────────────────────────────────┐
│   CAMBIAR CONTRASEÑA (Tab 2)        │
├─────────────────────────────────────┤
│ Contraseña Actual *                 │
│ [████████]                          │
│                                     │
│ Nueva Contraseña *                  │
│ [████████]                          │
│                                     │
│ Confirmar Contraseña *              │
│ [████████]                          │
│                                     │
│ Requisitos de Contraseña:           │
│ ☐ 8 o más caracteres               │
│ ☐ Mayúscula (A-Z)                  │
│ ☐ Minúscula (a-z)                  │
│ ☐ Número (0-9)                     │
│ ☐ Carácter especial                │
│                                     │
│ [ CAMBIAR CONTRASEÑA ]  [LIMPIAR]  │
└─────────────────────────────────────┘
```

**Estados del botón:**
- Inicial: disabled (gris, no clickeable)
- Escribiendo (< 5 reqs OK): disabled
- 5/5 reqs OK + passwordsMatch: enabled (azul)
- Enviando: disabled (spinner)
- Error: enabled (mostramos error)
- Éxito: resetear formulario

### 4.4 Envío y Respuesta

**Paso 1: User cliquea "Cambiar Contraseña"**

```
{
  "currentPassword": "OldP@ssw0rd!123",
  "newPassword": "NewP@ssw0rd!456",
  "passwordConfirm": "NewP@ssw0rd!456"
}
↓
POST /api/v1/auth/change-password
```

**Paso 2: Backend valida**

| Escenario | Backend Response | Frontend |
|-----------|-----------------|----------|
| ✓ Success | 200 OK, { message, lastPasswordChangeAt } | Mostrar success alert, limpiar formulario, desaparecer después 5s |
| ✗ Contraseña actual incorrecta | 400, { error: "Contraseña actual incorrecta" } | Error alert en rojo, focus en "Contraseña Actual" |
| ✗ Nueva = Actual | 400, { error: "Nueva contraseña no puede ser igual..." } | Error alert, sugerencia |
| ✗ No cumple requisitos (BE re-valida) | 400, { error: "Password invalid..." } | Error alert detallado |
| ✗ Rate limit (> 1/hora) | 429, { error: "Demasiados intentos..." } | Error alert con "Reintenta en X minutos" |
| ✗ Token expirado | 401, { error: "Token inválido..." } | Redirigir a login con mensaje |

### 4.5 UX en Éxito

```
┌──────────────────────────────────────┐
│ ✓ Contraseña actualizada exitosamente │ ← fade out después 5s
└──────────────────────────────────────┘

Formulario se limpia:
- Contraseña Actual: ""
- Nueva Contraseña: ""
- Confirmar Contraseña: ""
- Requirements: todos desactivados
- Botón: disabled de nuevo
```

### 4.6 UX en Error

```
┌──────────────────────────────────────┐
│ ✗ Contraseña actual incorrecta       │ ← rojo, no desaparece
└──────────────────────────────────────┘

Formulario preserva valores (no se limpia)
Usuario puede intentar de nuevo
```

---

## 5. FLUJO: ACTUALIZAR EMAIL

### 5.1 Entrada en Formulario

```
Campos:
1. Email de Control (read-only reference)
   - Valor: juan.perez@control.ec
   - Solo lectura, para referencia

2. Email Personal Nuevo (required)
   - Type: email
   - Placeholder: "tu.email.personal@example.com"
   - On Change → validar formato
   - On Blur → validar unicidad? (optional, postpone to backend)

3. Confirmar Contraseña (required)
   - Type: password
   - Placeholder: "Ingresa tu contraseña para confirmar identidad"
   - Previene CSRF en caso de sesión secuestrada
```

### 5.2 Validación Frontend

```typescript
// Email format (simple regex, backend hace validación RFC completa)
const isValidEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailPersonal);

// No puede ser igual a email control
const isNotControlEmail = emailPersonal !== profileData.emailControl;

// Password filled
const hasPassword = confirmPassword.length > 0;

const submitDisabled = !isValidEmail || !isNotControlEmail || !hasPassword;
```

### 5.3 Visualización (Desktop)

```
┌─────────────────────────────────────┐
│   EMAIL PERSONAL (Tab 3)            │
├─────────────────────────────────────┤
│ Email de Control (actual)           │
│ juan.perez@control.ec               │ (read-only)
│                                     │
│ Email Personal Nuevo *              │
│ [jpg.personal@gmail.com]            │
│                                     │
│ Confirmar Contraseña *              │
│ [████████]                          │
│                                     │
│ [ ACTUALIZAR EMAIL ]    [LIMPIAR]  │
│                                     │
│ ℹ️ Necesitamos tu contraseña para   │
│    confirmar tu identidad.          │
└─────────────────────────────────────┘
```

**Estados del botón:**
- Inicial: disabled
- Email inválido: disabled
- Email = control email: disabled
- Password vacío: disabled
- TODO OK: enabled (azul)
- Enviando: disabled (spinner)

### 5.4 Envío y Respuesta

**Paso 1: User cliquea "Actualizar Email"**

```
{
  "emailPersonal": "nuevo.email@example.com",
  "confirmPassword": "CurrentP@ssw0rd!"
}
↓
PUT /api/v1/user/update-personal-email
```

**Paso 2: Backend valida**

| Escenario | Backend Response | Frontend |
|-----------|-----------------|----------|
| ✓ Success | 200 OK, { message, emailPersonal } | Success alert, limpiar formulario, actualizar profile displayed |
| ✗ Contraseña incorrecta | 400, { error: "Contraseña incorrecta" } | Error alert, focus en password |
| ✗ Email duplicado | 409, { error: "Email ya registrado..." } | Error alert, sugerencia (otro usuario tiene ese email) |
| ✗ Email = control | 400, { error: "Email personal no puede ser igual..." } | Error alert |
| ✗ Email inválido | 400, { error: "Formato de email inválido" } | Error alert |
| ✗ Rate limit (> 3/hora) | 429, { error: "Demasiados intentos..." } | Error alert con retry time |
| ✗ Token expirado | 401 | Redirigir a login |

### 5.5 UX en Éxito

```
┌──────────────────────────────────────┐
│ ✓ Email actualizado exitosamente     │ ← fade out después 5s
└──────────────────────────────────────┘

Formulario se limpia:
- Email Personal Nuevo: ""
- Confirmar Contraseña: ""
- Botón: disabled de nuevo

Opcionalmente:
- Recargar profile displayed en Tab 1
```

---

## 6. VALIDACIÓN FRONTEND

### 6.1 Requisitos de Contraseña

**Implementar 5 checkboxes independientes:**

```typescript
export const passwordRequirements = [
  {
    id: 'minLength',
    label: '8 o más caracteres',
    test: (pwd: string) => pwd.length >= 8
  },
  {
    id: 'uppercase',
    label: 'Mayúscula (A-Z)',
    test: (pwd: string) => /[A-Z]/.test(pwd)
  },
  {
    id: 'lowercase',
    label: 'Minúscula (a-z)',
    test: (pwd: string) => /[a-z]/.test(pwd)
  },
  {
    id: 'number',
    label: 'Número (0-9)',
    test: (pwd: string) => /\d/.test(pwd)
  },
  {
    id: 'special',
    label: 'Carácter especial (!@#$%^&*...)',
    test: (pwd: string) => /[!@#$%^&*()_+\-=\[\]{}|;:,.<>?]/.test(pwd)
  }
];
```

### 6.2 Validación de Email

```typescript
// Simple regex para validación frontend
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const validateEmailFormat = (email: string): boolean => {
  return emailRegex.test(email);
};

// Comprobación: no igual a control email
export const isNotControlEmail = (email: string, controlEmail: string): boolean => {
  return email.toLowerCase() !== controlEmail.toLowerCase();
};
```

### 6.3 Feedback Visual

| Estado | Elemento | CSS Clase | Color |
|--------|----------|-----------|-------|
| Requirement NO cumplido | Checkbox | `unchecked` | Gris (#999) |
| Requirement cumplido | Checkbox | `checked` | Verde (#22c55e) |
| Email inválido | Input + Icon | `error` | Rojo (#ef4444) |
| Email válido | Input + Icon | `success` | Verde (#22c55e) |
| Button disabled | Button | `disabled` | Gris (#ccc), cursor no-drop |
| Button enabled | Button | `primary` | Azul (#3b82f6), cursor pointer |
| Sending | Button | `loading` | Azul con spinner |
| Error alert | Alert | `error-alert` | Rojo fondo, texto oscuro |
| Success alert | Alert | `success-alert` | Verde fondo, texto oscuro |

---

## 7. COMPORTAMIENTO DE ERRORES

### 7.1 Errores de Contraseña

| Error | Mensaje | Acción |
|-------|---------|--------|
| Contraseña actual incorrecta | "Contraseña actual incorrecta" | Focus en campo "Contraseña Actual" |
| Nueva = Actual | "Nueva contraseña no puede ser igual a la actual" | Sugerencia de crear una diferente |
| Complejidad (BE) | "La contraseña debe contener mayúscula, minúscula, número y carácter especial" | Revisar requirements |
| Rate limit | "Demasiados intentos. Reintenta en XX minutos" | Deshabilitar form 1 hora |
| Token expirado | "Sesión expirada. Por favor, inicia sesión nuevamente" | Redirigir a login |

### 7.2 Errores de Email

| Error | Mensaje | Acción |
|-------|---------|--------|
| Contraseña incorrecta | "Contraseña incorrecta" | Focus en campo password |
| Email duplicado | "Email ya está registrado por otro usuario" | Sugerencia: usar otro email |
| Igual a control | "Email personal no puede ser igual a email control" | Campo muestra valor de control |
| Formato inválido | "Formato de email inválido" | Focus en campo email |
| Rate limit | "Demasiados intentos. Reintenta en XX minutos" | Deshabilitar form 3 horas |
| Token expirado | "Sesión expirada. Por favor, inicia sesión nuevamente" | Redirigir a login |

### 7.3 Presentación de Errores

```
┌──────────────────────────────────────┐
│ ✗ Contraseña actual incorrecta       │ ← Alert rojo, borde rojo
│   Por favor verifica e intenta de    │
│   nuevo.                             │
│ [ ✕ ]                               │ ← Botón cerrar
└──────────────────────────────────────┘

Ubicación: Arriba del formulario
Desaparece: Al hacer click en [✕] o al editar un campo (si lo desea usuario)
No auto-desaparece: Error debe ser confirmado
```

---

## 8. ACCESIBILIDAD & RESPONSIVE

### 8.1 WCAG 2.1 AA Compliance

- **Labels:** Cada input tiene `<label>` con `for` atributo
- **ARIA:** `aria-required`, `aria-invalid`, `aria-describedby` en inputs
- **Focus:** Tab order lógico, focus visible en botones
- **Color:** No depender solo de color (checkmarks +  texto)
- **Texto:** Font size >= 14px, contraste >= 4.5:1

### 8.2 Responsive Design

| Breakpoint | Layout | Cambios |
|------------|--------|---------|
| < 640px | Mobile | Tabs apiladas, inputs full-width, botones stacked |
| 640px - 1024px | Tablet | Tabs horizontal, inputs en 1 columna |
| > 1024px | Desktop | Tabs horizontal, layout optimizado |

### 8.3 Keyboard Navigation

- **Tab:** Navega entre inputs y botones
- **Shift+Tab:** Navega hacia atrás
- **Enter:** Submit del formulario (si botón tiene focus)
- **Escape:** Cierra alerts de error/éxito

### 8.4 Screen Reader Support

```html
<div role="alert" aria-live="polite">
  ✓ Contraseña actualizada exitosamente
</div>
```

---

## 9. TESTING CHECKLIST

### 9.1 Frontend Unit Tests

- [ ] Requirements regex: todos 5 cases (true + false)
- [ ] Email validation: formato válido + inválido
- [ ] Password match: coinciden + no coinciden
- [ ] Submit button enabled/disabled según estado
- [ ] Form reset: valores limpios después submit

### 9.2 Frontend Integration Tests

- [ ] Cargar profile → GET /user/profile OK
- [ ] Cambiar contraseña → POST /auth/change-password → Success + form reset
- [ ] Cambiar contraseña → error 400 → mostrar error + no reset
- [ ] Rate limit → error 429 → mostrar mensaje + timeout
- [ ] Cambiar email → PUT /user/update-personal-email → Success
- [ ] Cambiar email → error 409 (duplicado) → mostrar error específico
- [ ] Token expirado → error 401 → redirigir a login

### 9.3 E2E Tests (Optativo MVP)

- [ ] E2E: Login → Settings → Cambiar Contraseña → Logout → Login con nueva contraseña ✓
- [ ] E2E: Login → Settings → Cambiar Email → Logout → Verificar email actualizado ✓

---

## 10. NOTAS DE DISEÑO

### ¿Por qué 5 requirements en vez de solo longitud?

OWASP recomienda complejidad multifactor para contraseñas usuarios. Los 5 requisitos evitan ataques de fuerza bruta comunes.

### ¿Por qué feedback en vivo (live)?

UX mejor: usuario sabe qué falta antes de enviar. Reduce clicks frustrados.

### ¿Por qué pedir contraseña en email update?

Previene CSRF. Si alguien obtiene sesión abierta, debe conocer contraseña para cambiar email (contacto crítico).

### ¿Email personal puede quedar null?

SÍ. Es optativo. Usuario puede dejar vacío o actualizar en cualquier momento.

### Phase 2: Confirmación de Email

MVP no requiere. Phase 2 añadirá:
- Link de confirmación + token TTL 24h
- Notificación a email anterior
- Email actualiza solo tras validación

---

## 11. SCREENSHOTS MOCKUP

### Vista Desktop — Tab Cambiar Contraseña

```
╔════════════════════════════════════════════════════════╗
║ Control Electoral — Configuración del Usuario          ║
║ [Mi Perfil]  [Cambiar Contraseña]  [Email Personal]  ║
╠════════════════════════════════════════════════════════╣
║                                                        ║
║  Cambiar Contraseña                                   ║
║                                                        ║
║  Contraseña Actual *                                  ║
║  ┌────────────────────────────────────────────────┐  ║
║  │ ••••••••                                        │  ║
║  └────────────────────────────────────────────────┘  ║
║                                                        ║
║  Nueva Contraseña *                                   ║
║  ┌────────────────────────────────────────────────┐  ║
║  │ ••••••••                                        │  ║
║  └────────────────────────────────────────────────┘  ║
║                                                        ║
║  Confirmar Contraseña *                               ║
║  ┌────────────────────────────────────────────────┐  ║
║  │ ••••••••                                        │  ║
║  └────────────────────────────────────────────────┘  ║
║                                                        ║
║  Requisitos de Contraseña:                            ║
║  ☑ 8 o más caracteres                                ║
║  ☑ Mayúscula (A-Z)                                   ║
║  ☑ Minúscula (a-z)                                   ║
║  ☑ Número (0-9)                                      ║
║  ☑ Carácter especial                                 ║
║                                                        ║
║  ┌─────────────────┐  ┌──────────┐                   ║
║  │ Cambiar Clave   │  │ Limpiar  │                   ║
║  └─────────────────┘  └──────────┘                   ║
║                                                        ║
╚════════════════════════════════════════════════════════╝
```

### Vista Mobile — Tab Email Personal (error)

```
╔════════════════════════════════════╗
║ Configuración de Usuario            ║
║                                     ║
║ [Mi Perfil]  [Cambiar]  [Email]    ║
║                (...)                ║
╠════════════════════════════════════╣
║                                     ║
║ ┌────────────────────────────────┐ ║
║ │ ✗ Email ya está registrado     │ ║
║ │   por otro usuario             │ ║
║ │               [✕]              │ ║
║ └────────────────────────────────┘ ║
║                                     ║
║ Email de Control                    ║
║ ┌────────────────────────────────┐ ║
║ │ juan.perez@control.ec          │ ║
║ │ (solo lectura)                 │ ║
║ └────────────────────────────────┘ ║
║                                     ║
║ Email Personal Nuevo *              ║
║ ┌────────────────────────────────┐ ║
║ │ jpg.personal@gmail.com         │ ║
║ └────────────────────────────────┘ ║
║                                     ║
║ Confirmar Contraseña *              ║
║ ┌────────────────────────────────┐ ║
║ │ ••••••••                       │ ║
║ └────────────────────────────────┘ ║
║                                     ║
║ ┌──────────────────────────────────┐║
║ │ Actualizar Email                 ││
║ └──────────────────────────────────┘║
║ ┌──────────────────────────────────┐║
║ │ Limpiar                          ││
║ └──────────────────────────────────┘║
║                                     ║
╚════════════════════════════════════╝
```

---

**Documento finalizado:** 2026-11-19  
**Próxima actualización:** Cuando se implemente Phase 2 (email confirmation)
