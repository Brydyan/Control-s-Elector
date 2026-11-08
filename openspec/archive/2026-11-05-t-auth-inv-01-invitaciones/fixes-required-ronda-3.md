# Fixes Required — T-AUTH-INV-01 (Ronda 3)

**Status:** ✅ SIN BLOQUEANTES DE ESTE CHANGE

**Hallazgo crítico:** 1 bug fuera de scope (J.7: `rol` vs `rolNombre`)

---

## Bloqueantes de Código (Este Change)

❌ **NINGUNO**

- Backend: 45 tests / 0 fallos
- Frontend: 651 tests / 0 fallos
- Mutaciones: 7/7 efectivas
- Build & Lint: 0 errores

---

## Hallazgo Crítico: J.7 — `rol` vs `rolNombre` (FUERA DE SCOPE)

### 1. Síntoma

Crear o editar usuario desde el panel devuelve **HTTP 400 Bad Request**.

```
POST /api/v1/admin/usuarios
{
  "nombre": "Juan",
  "apellido": "García",
  "rol": "MASTER"  // ← frontend manda esto
}

Response: 400 Bad Request
ValidationException: Field 'rolNombre' cannot be null
```

### 2. Causa Raíz

**Frontend envía:**
```typescript
// admin.api.ts → crearUsuario()
return {
  nombre: usuario.nombre,
  apellido: usuario.apellido,
  rol: usuario.rol  // ← key es "rol"
}
```

**Backend espera:**
```java
// CreateUsuarioRequest.java
@NotBlank String rolNombre;  // ← key es "rolNombre"
```

**Mismatch:** El JSON tiene `rol` (nullabel), el DTO valida `rolNombre` (@NotBlank).

### 3. Reproducción

```bash
# Desde el panel admin: Usuarios → Agregar usuario
# Llenar formulario con nombre/apellido/rol
# Clica "Guardar"

# Resultado: "Error al guardar usuario: rolNombre is required"
```

### 4. Ubicación en Código

| Componente | Archivo | Línea |
|-----------|---------|-------|
| Frontend request | `frontend/app/src/app/features/admin/data/admin.api.ts` | `crearUsuario()` |
| Backend DTO | `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/auth/web/dto/CreateUsuarioRequest.java` | `@NotBlank String rolNombre` |
| Backend DTO | `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/auth/web/dto/UpdateUsuarioRequest.java` | `@NotBlank String rolNombre` |

### 5. Fix Propuesto

**Opción A:** Frontend renombra `rol` → `rolNombre`
```typescript
// admin.api.ts
return {
  nombre: usuario.nombre,
  apellido: usuario.apellido,
  rolNombre: usuario.rol  // ← renombrar clave
}
```

**Opción B:** Backend acepta `rol` (alias)
```java
// CreateUsuarioRequest.java
@JsonProperty("rol")
@NotBlank String rolNombre;  // ← alias para retrocompat
```

### 6. Impacto

- 🔴 **CRÍTICO para usabilidad:** El panel no puede crear/editar usuarios
- ✅ **NO bloquea cierre de T-AUTH-INV-01** — bulk invite sigue funcionando (backend ruta `/api/v1/auth/invitaciones` es distinto)
- ⚠️ **Afecta a T-FE-19** (Gestión de Usuarios) — que es otro change

### 7. Decisión para Arquitecto

**¿Es de scope de este change (invitaciones)?**

- ✅ SÍ → Fijar antes de cerrar (recomendado: Opción A, renombrar frontend)
- ❌ NO → Crear ticket separate (T-FE-19-fix-rol-relnombre) y aceptar que el panel está roto

**apply-progress.md §J.7 declara:** "está fuera de `tasks.md` (este change es de invitaciones, no del alta de usuarios)".

---

## Qué NO hay que rehacer

✅ **Bulk invite:** Funcional, testeado, verificado  
✅ **Activación por email:** Funcional, 651 tests en verde  
✅ **Mutaciones:** 7/7 efectivas (M2-M6b, M9-M10)  
✅ **Backend:** 45 tests, 0 fallos  
✅ **Frontend:** 651 tests, 0 fallos  

---

## Pendientes Abiertos (SIN HACER, DECLARADOS)

| Pendiente | Estado | Bloqueador | Próximo |
|-----------|--------|-----------|---------|
| **C.10 / D.10** — mutación login | Pendiente | Docker caído | Ronda 4 |
| **M1 / C.1** — mutación migración | Pendiente | Docker caído | Ronda 4 |
| **Task 4.4** — k6 load test ejecutado | Escrito, no ejecutado | Docker + tokens | Ronda 4 |
| **Cobertura JaCoCo** | No instrumentado | Out of scope | Future work |
| **Suite de contrato** | No extendida | Credenciales desactualizadas | Future work |

---

## Resumen

| Categoría | Estado |
|-----------|--------|
| T-AUTH-INV-01 bloqueantes | ✅ NINGUNO |
| Código verificado | ✅ 696 tests, 7 mutaciones |
| Bug J.7 (`rol` vs `rolNombre`) | 🔴 CRÍTICO pero fuera de scope (decisión Arquitecto) |
| Ready to close | ⚠️ A decisión (fix J.7 now o separate ticket?) |

---

**Verificador:** Arquitecto  
**Fecha:** 2026-11-06  
**Recomendación:** Fijar J.7 antes de cerrar (Opción A: renombrar `rol` → `rolNombre` en frontend)
