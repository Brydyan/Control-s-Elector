# ODD — Frontend: Complete Recintos Route Navigation

**Estado:** Propuesta QA Review  
**Fecha:** 2026-11-22  
**Hallazgo:** Verificación de completitud en rama actual

---

## Propuesta

**Hallazgo en verificación:**

Componentes para Recintos (crear, editar, listar) **están implementados** ✅:
- `recinto-create-wizard.modal.ts` (creación con steps)
- `recinto-edit-wizard.modal.ts` (edición)
- `recintos-list.page.ts` (listado)
- `recinto.api.ts` (servicios CRUD)

**Pero falta:** Ruta en `app.routes.ts` para navegar a la página.

**Síntoma:** No hay forma de acceder a `/admin/recintos` desde la UI. Los archivos existen pero no están "cableados" al router.

---

## Clasificación

| Criterio | Evaluación |
|----------|-----------|
| **New work?** | No — componentes ya existen |
| **API change?** | No — backend `recinto.api.ts` ya existe |
| **Architecture?** | No — solo routing |
| **Multi-screen?** | No — una página |
| **Completación?** | ✅ Sí — terminar feature incompleta |

**→ ODD (Operational Document)** — ajuste menor, completación.

---

## Scope

| Fase | Tarea | Cambios |
|------|-------|---------|
| **A** | Agregar ruta `admin/recintos` a `app.routes.ts` | 1 archivo (app.routes.ts) |
| **B** | Verificar que ruta no rompe tests existentes | 0 cambios de lógica |
| **C** | Test manual: navegar a `/admin/recintos` sin errores | 0 archivos |

---

## Checklist

- [ ] **A.1** Ubicar línea en `app.routes.ts` (después de `admin/sesiones`)
- [ ] **A.2** Agregar objeto de ruta:
  ```typescript
  {
    path: 'admin/recintos',
    canActivate: [roleGuard],
    loadComponent: () =>
      import('./features/admin/pages/recintos/recintos-list.page').then(
        (m) => m.RecintosListPage,
      ),
    data: {
      roles: ['MASTER', 'ADMIN'],
      title: 'Gestión de Recintos',
      description: 'Administración de centros de recepción de votos y zonas electorales.',
      icon: 'location-outline',
    },
  },
  ```
- [ ] **B.1** Correr `pnpm test` → 0 regressions
- [ ] **C.1** Navegar a `/admin/recintos` en browser → carga sin error
- [ ] **C.2** Verifica que el modal de crear recinto aparece al hacer clic
- [ ] **C.3** Verifica que el modal de editar recinto funciona

---

## Errores Resueltos

| Error | Criticidad | Fix |
|-------|-----------|-----|
| **Ruta faltante** | 🔴 | Agregar a `app.routes.ts` |
| **Innavegable** | 🔴 | Sin ruta, usuario no puede llegar a la página |

---

## Notas

- **Componentes verificados:** Todos existen, tipados correctamente, con tests
- **API verificados:** `recinto.api.ts` usa `ConfigService.getApiUrlSafe()` (correctamente unificado en ODD anterior)
- **Role guard:** `['MASTER', 'ADMIN']` — asume que recintos es admin-only
- **Icon:** `location-outline` (PrimeNG standard icon, alineado con geografía)

---

## Dependencias

- ✅ No depende de nada
- ✅ No bloquea nada
- 📦 Habilita: Acceso a gestión de recintos desde menu/rutas

---

## Riesgo

- **Bajo:** Una línea de ruta, sin cambios lógicos
- **Testing:** Solo verificar que tests frontend pasan

---

## Próximos Pasos

✅ **Listo para `/02-builder-odd frontend-recintos-route-completion`**

---

## Referencia

Hallazgo de verificación: rama `brydyan/sc-520/feat-caching-strategy` luego de merge de develop.

**Archivos relacionados:**
- `frontend/app/src/app/app.routes.ts` (línea ~294)
- `frontend/app/src/app/features/admin/pages/recintos/recintos-list.page.ts`
- `frontend/app/src/app/features/admin/data/recinto.api.ts`

