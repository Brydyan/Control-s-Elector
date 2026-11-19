# SDD — User Profile & Settings

**Referencia:** T-AUTH-SETTINGS-01  
**Epic:** Auth › User Management  
**Componente:** Profile Page + Settings  

---

## Propuesta

Crear panel de configuración/perfil de usuario donde pueda:
- **Cambiar contraseña** — validación de contraseña actual + nueva
- **Cambiar correo personal** — solo el campo `email_personal` (correo CONTROL es inmutable)
- **Ver datos inmutables** — nombre, correo CONTROL, rol

**Hoy:** No existe. Usuario con contraseña temporal no puede cambiarla. Email personal no tiene interfaz de cambio.

**Mañana:** Usuario autenticado puede ir a `/settings` o `/profile` y actualizar su contraseña + email personal de forma segura.

---

## Scope

### Users & Flows

| Usuario | Acción | Resultado |
|---------|--------|-----------|
| Usuario ACTIVO | Cambiar contraseña | POST /api/v1/auth/change-password (old, new, confirm) |
| Usuario ACTIVO | Cambiar email personal | PUT /api/v1/user/update-personal-email (email, password confirm) |
| Usuario ACTIVO | Ver perfil | GET /api/v1/user/profile (read-only, immutable fields) |

### Pantalla Esperada

```
┌─ Profile / Settings ─────────────┐
│                                   │
│ Datos Inmutables:                 │
│  • Nombre: [Juan Pérez]           │
│  • Email CONTROL: [j@control.ec]  │
│  • Rol: [Master / Operador]       │
│                                   │
│ Cambiar Email Personal:           │
│  Email Personal: [j.pers@...]     │
│  [Guardar] [Cancelar]             │
│                                   │
│ Cambiar Contraseña:               │
│  Contraseña Actual: [·····]       │
│  Nueva Contraseña: [·····]        │
│  Confirmar: [·····]               │
│  [Cambiar] [Cancelar]             │
│                                   │
└───────────────────────────────────┘
```

---

## Decisiones de Diseño

**D1 — ¿Cambio de contraseña requiere confirmación anterior?**
- Opción A: SÍ. Pedir `currentPassword` + nueva. Más seguro.
- Opción B: NO. Solo nueva. Menos fricción, pero riesgo.
- **Elegida:** A. Es contraseña. Siempre pedir confirmación de la actual.

**D2 — ¿Email personal puede quedar vacío?**
- Opción A: SÍ. Campo opcional.
- Opción B: NO. Campo requerido (único por usuario).
- **Elegida:** A. Es personal. Usuario elige actualizar o no.

**D3 — ¿Rate limiting en cambios?**
- Opción A: SÍ. Max 1 cambio/hora.
- Opción B: NO. Sin límite.
- **Elegida:** A. Prevenir ataques. 1 cambio/hora por IP+user.

**D4 — ¿Validación de contraseña igual a anterior?**
- Opción A: Permitir. No es reutilización.
- Opción B: Bloquear. Nueva ≠ anterior.
- **Elegida:** B. Best practice. Fuerza nuevas contraseñas.

**D5 — ¿Email personal único en sistema?**
- Opción A: SÍ. Índice único en BD.
- Opción B: NO. Múltiples usuarios pueden tener el mismo.
- **Elegida:** A. Es dato de contacto. Mantener integridad.

---

## Próximos Pasos

Documento listo en: `/openspec/changes/back/2026-11-19-user-profile-settings/`

→ **Pasar a Arquitecto para feedback en:**
- D1 (confirmación contraseña anterior)
- D3 (rate limiting)
- D5 (unicidad email personal)
