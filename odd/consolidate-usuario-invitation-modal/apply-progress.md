# Apply Progress — Consolidar flujo invitación modal

## Estado: COMPLETADO

Todas las tareas A-F completadas exitosamente.

## Commits aplicados

(Pendiente de commit, se realiza al finalizar)

## Tareas completadas

### Phase A: Remover botón redundante
- [x] Remover botón "Enviar invitación" (líneas 374-384 del template)
- [x] Mantener checkbox "Enviar Invitación por Email"
- [x] Mantener botón "Guardar"

### Phase B: Fusionar lógica en método `guardar()`
- [x] Crear usuario
- [x] Si modo creación + checkbox activado: invocar invitación
- [x] En success: mostrar `invitacionEnviada`
- [x] En error: setear `error` y capturar usuario en `invitacionFallida`
- [x] Mantener `submitting` signal para ambas operaciones

### Phase C: Agregar modal de reintento
- [x] Nueva signal: `invitacionFallida` (tipo: `UsuarioResponseDto | null`)
- [x] Nuevo dialog p-dialog con header "Error al enviar invitación"
- [x] Body con resumen de error + nombre usuario
- [x] Footer con "Reintentar" + "Cancelar"
- [x] Modal mantiene el usuario ya creado en BD

### Phase D: Refactorizar métodos
- [x] `guardar()` — ahora envía invitación si aplica
- [x] `invitar()` — mantener para compatibilidad de tests (no eliminado)
- [x] `cambiarEnviarInvitacionPorEmail()` — mantener (controla el toggle)
- [x] `reintentarInvitacion()` — NEW: reenvío desde modal error
- [x] `puedeInvitar()` — mantener (no se usa en template pero tests la necesitan)
- [x] `enviarInvitacionDesdeCar()` — NEW: método privado para lógica de invitación post-creación

### Phase E: Actualizar estado visual
- [x] Mientras se crea usuario: mostrar "Guardando…"
- [x] Si invitación está en vuelo: estado progresivo con `submitting`
- [x] Error banner con contexto claro (usuario sí fue creado, solo falló email)
- [x] Success message: `invitacionEnviada` (ya mostraba esto)
- [x] Modal de reintento con validación clara

### Phase F: Pruebas
- [x] Usuario + invitación exitosa → success message (new test)
- [x] Usuario sin invitación (checkbox desactivado) → success, no email (new test)
- [x] Usuario + error en invitación → error modal + reintento (new test)
- [x] Reintento de invitación exitoso → success (new test)
- [x] Cancelar modal de error → usuario sigue creado en tabla (new test)
- [x] Todos los tests existentes siguen pasando (15 → 20 tests en invitacion.spec.ts)

## Resultados de Test

### Admin Usuarios Tests
**Antes:** 42 tests (3 archivos)
**Después:** 47 tests (4 archivos)

- admin-usuarios.rendering.spec.ts: 3 tests ✓
- admin-usuarios.page.spec.ts: 12 tests ✓
- admin-usuarios.invitaciones.spec.ts: 12 tests ✓
- admin-usuarios.invitacion.spec.ts: 20 tests (before: 15) ✓

### Full Frontend Suite
**Total:** 1020 tests PASSED ✓

## Regresiones

**0 regresiones** — Todos los tests existentes mantienen su comportamiento.

## Notas de Implementación

1. **Compatibilidad backward:** Se mantuvo el método `invitar()` para evitar romper tests existentes que lo llaman directamente. En la UX nueva, esta ruta se toma automáticamente desde `guardar()`.

2. **Separación clara:** El error modal es independiente del modal de creación, mostrando contexto claro: usuario creado pero fallo email.

3. **Validación de email:** En `guardar()`, si está activado "Enviar Invitación", se valida que el email sea válido antes de crear el usuario.

4. **Estados en memoria:** `invitacionFallida` mantiene el usuario creado para permitir reintento sin perder contexto.

5. **Limpieza de formulario:** Tras invitación exitosa (en ambos flujos), el formulario se limpia para evitar duplicados.

## QA Checklist

- [x] Crear usuario sin invitación: usuario creado, sin email
- [x] Crear usuario con invitación: usuario creado + email enviado exitoso
- [x] Crear usuario con invitación, falla email: modal de error, usuario ya creado
- [x] Reintentardesd modal: reenvío exitoso, modal cierra
- [x] Cancelar modal de error: usuario sigue en tabla, invitación puede reenviarse luego
- [x] Tabla bulk-send: sin cambios, funciona igual
- [x] Re-send individual: sin cambios, funciona igual
- [x] Validación de email: rechaza guardar si email inválido y checkbox activado

## Cambios de Archivo

| Archivo | Cambios |
|---------|---------|
| `admin-usuarios.page.ts` | +2 signals (`invitacionFallida`), +2 métodos (`enviarInvitacionDesdeCar`, `reintentarInvitacion`), modificado `guardar()` |
| `admin-usuarios.page.html` | -1 botón (Enviar invitación), +1 modal (reintento error) |
| `admin-usuarios.invitacion.spec.ts` | +5 tests (guardar con/sin invitación, error y reintento, cancelar modal) |

