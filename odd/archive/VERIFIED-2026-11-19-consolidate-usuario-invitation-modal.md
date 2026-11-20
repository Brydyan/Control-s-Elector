# ODD — Consolidar flujo de invitación en modal de usuario

**Referencia:** T-AUTH-INV-02 (Invitación con email personal, ODD #504 B)  
**Epic:** Admin › Gestión de Usuarios  
**Componente:** `AdminUsuariosPage`  

---

## Propuesta

Eliminar redundancia en el modal de creación de usuario (líneas 374-384):
- Hoy: botón "Enviar invitación" separado del flujo de "Guardar"
- Propuesta: consolidar en una única acción "Guardar" que:
  1. Crea usuario (siempre)
  2. Envía invitación si checkbox está activado
  3. Muestra modal de error + reintento si falla el email

**Resultado esperado:** UX más clara, una acción "Guardar", flujo transparente, manejo de error elegante.

---

## Scope

### A. Eliminar redundancia en template
- [ ] Remover botón "Enviar invitación" (líneas 374-384)
- [ ] Mantener checkbox "Enviar Invitación por Email"
- [ ] Mantener botón "Guardar"

### B. Fusionar lógica en método `guardar()`
- [ ] Crear usuario
- [ ] Si modo creación + checkbox activado:
  - Llamar a `invitarUsuario()` con los datos
  - En success: mostrar `invitacionEnviada` (ya existe)
  - En error: setear `error` con contexto de invitación fallida
- [ ] Mantener `submitting` signal para ambas operaciones

### C. Agregar modal de reintento de invitación
- [ ] Nueva signal: `invitacionFallida` (tipo: `UsuarioResponseDto | null`)
- [ ] Nuevo dialog p-dialog:
  - Header: "Error al enviar invitación"
  - Body: Resumen de error + nombre usuario
  - Footer: "Reintentar" + "Cancelar"
- [ ] Botón "Reintentar" llama a `reintentarInvitacion()` (nuevo método)
- [ ] Mantiene el usuario ya creado en BD

### D. Refactorizar métodos
- [ ] `guardar()` — ahora envía invitación si aplica
- [ ] `invitar()` — ELIMINAR (lógica movida a `guardar()`)
- [ ] `cambiarEnviarInvitacionPorEmail()` — mantener (controla el toggle)
- [ ] `reintentarInvitacion()` — NEW: reenvío desde modal error
- [ ] `puedeInvitar()` — verificar si aún se usa (probablemente no)

### E. Actualizar estado visual
- [ ] Mientras se crea usuario: mostrar "Guardando…"
- [ ] Si invitación está en vuelo: mostrar "Enviando invitación…" (o estado progresivo)
- [ ] Error banner con contexto claro (usuario sí fue creado, solo falló email)
- [ ] Success message: `invitacionEnviada` (ya mostraba esto)

### F. Pruebas
- [ ] Usuario + invitación exitosa → success message
- [ ] Usuario sin invitación (checkbox desactivado) → success, no email
- [ ] Usuario + error en invitación → error modal + reintento
- [ ] Reintento de invitación exitoso → success
- [ ] Cancelar modal de error → usuario sigue creado en tabla

---

## Decisiones de Diseño

**D1 — ¿Dónde vive la lógica de reintento?**
- Opción A: Modal separado (propuesta actual) ✅
- Opción B: Inline en el mismo modal con botón "Reintentar"
- **Elegida:** A (separación clara: "creación exitosa" vs "envío con error")

**D2 — ¿El usuario quedó creado si falló el email?**
- Sí, siempre. La creación es independiente del email.
- El error modal lo deja claro: "Usuario creado. Error al enviar el email."

**D3 — ¿Qué pasa con `emailPersonal` en el reintento?**
- Se mantiene en memoria.
- Modal de reintento muestra: "¿Reenviar a {{emailPersonal}}?"

---

## Cambios de Archivo

| Archivo | Acción | Líneas |
|---------|--------|--------|
| `admin-usuarios.page.html` | Remover botón invitar | 374-384 |
| `admin-usuarios.page.ts` | Fusionar `guardar()` + `invitar()` | 234-360 |
| `admin-usuarios.page.ts` | Agregar `invitacionFallida` signal | NEW |
| `admin-usuarios.page.ts` | Agregar `reintentarInvitacion()` método | NEW |
| `admin-usuarios.page.html` | Agregar modal reintento (p-dialog) | NEW |

---

## Checklist de Implementación

- [ ] Remover botón "Enviar invitación" del template
- [ ] Eliminar método `invitar()` (movida a `guardar()`)
- [ ] Agregar signal `invitacionFallida`
- [ ] Implementar fusión en `guardar()`: crear + invocar invitación si aplica
- [ ] Agregar modal p-dialog para reintento
- [ ] Implementar `reintentarInvitacion()`
- [ ] Eliminar método `puedeInvitar()` si no se usa en otra parte
- [ ] Verificar que `invitacionEnviada` aún funciona (casos exitosos)
- [ ] Revisar tests: `admin-usuarios.invitacion.spec.ts`
- [ ] Tests: usuario + invitación OK, usuario + error invitación + reintento OK
- [ ] No regresión: tabla bulk-send, re-send individual siguen igual

---

## Referencia a Tests Existentes

- `admin-usuarios.invitacion.spec.ts` — casos de invitación simple
- `admin-usuarios.invitaciones.spec.ts` — casos de invitaciones en lote
- Ambos deben seguir pasando (no afecta lógica backend)

---

## Apply Progress

```markdown
# Apply Progress — Consolidar flujo invitación modal

## Estado: PENDIENTE

Esperando asignación a Builder.

## Commits aplicados
(ninguno aún)

## Notas
(a llenar durante implementación)
```

---

## Próximos Pasos

✅ **QA aprobó:** Cambio clasificado como ODD (ajuste UI a feature existente)

→ **Listo para** `/02-build-odd` — Builder implementa scope A-F
