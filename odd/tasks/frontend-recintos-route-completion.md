# ODD — Frontend: Complete Recintos Route Navigation

**Estado:** Verificado (QA) — 2026-11-22
**Fecha:** 2026-11-22  
**Hallazgo:** Verificación de completitud en rama actual
**Verificación:** `03-verify-odd` → **PASS con notas** (ver "Desviaciones" y "Deuda")

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
| **D** | *(QA)* Menú: "Recintos" en la sección Administración de MASTER | `nav.config.ts`, `icon-map.ts` |
| **E** | *(QA)* Rework: la feature no compilaba y el paginador leía un campo inexistente | 3 componentes + 2 specs de recintos |
| **F** | *(QA)* Guardas de test que protegen la ruta y el item de menú | `app.routes.spec.ts`, `nav.config.spec.ts` |

---

## Checklist

- [x] **A.1** Ubicar línea en `app.routes.ts` (después de `admin/sesiones`)
- [x] **A.2** Agregar objeto de ruta:
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
  ⚠️ **Desviación**: `roles` quedó `['MASTER']` (commit `bc88fd9`), no `['MASTER','ADMIN']`.
  Ver "Desviaciones" abajo.
- [x] **B.1** Correr `pnpm test` → 0 regressions
- [x] **C.1** Navegar a `/admin/recintos` en browser → carga sin error
  ⚠️ Evidencia automatizada: la ruta resuelve y el chunk lazy compila (`pnpm build` verde);
  la prueba manual en navegador no se ejecutó (E2E fuera de alcance por instrucción).
- [x] **C.2** Verifica que el modal de crear recinto aparece al hacer clic
  ⚠️ Evidencia automatizada: `recinto-form.modal.spec.ts` cubre la carga de coordinadores
  y el guardado; no hay prueba de navegador.
- [x] **C.3** Verifica que el modal de editar recinto funciona
  ⚠️ Evidencia automatizada: `recinto-edit-wizard.modal.spec.ts` cubre el flujo de edición.

---

## Desviaciones respecto de la propuesta

| # | Propuesta | Implementado | Motivo | Decisión |
|---|-----------|--------------|--------|----------|
| D.1 | `roles: ['MASTER','ADMIN']` | `roles: ['MASTER']` | ADMIN tiene `configuracion:read` (listar sí) pero **no** `configuracion:write`; y `recintos-list.page` **no** tiene gating por rol, así que ADMIN vería "Crear Recinto" y el backend lo rebotaría con 403. | Producto, 2026-11-22: **solo MASTER**. Consecuencia: ADMIN pierde lectura de recintos en la UI (el backend sí se la permite). |
| D.2 | Ruta sola (1 archivo) | Ruta + `nav.config.ts` + `icon-map.ts` + 2 specs | Sin entrada en `NAV_CONFIG` el síntoma original seguía vivo: "no hay forma de acceder desde la UI" (solo por URL). `NAV_CONFIG` es la fuente canónica del sidebar y `sanitizeNavSections` descarta lo que no está ahí. | Producto, 2026-11-22: **sí**, agregar "Recintos" a la sección "Administración" de MASTER. |
| D.3 | "Componentes verificados: tipados correctamente" | **Falso**: la feature no compilaba | `pnpm build` fallaba con 5 errores (2× `NG8002` por `pButton` sin `ButtonModule`, 2× `TS2339`). Ninguno venía de los commits de este ODD: venían de `26a5636` (feat(recintos) Phase D) y arrastraban CI rojo en `develop`. | Corregido en el rework de QA (ver "Archivos"). |
| D.4 | — | `meta.total` → `meta.totalElements` | El backend manda `{page, size, totalElements, totalPages}`. La página leía `meta.total` (inexistente) → `totalElements` siempre 0 → paginador roto. Los mocks de los specs copiaban el bug (`meta: { total: 2 }`). | Corregido en el rework de QA (ver "Archivos"). |

---

## Errores Resueltos

| Error | Criticidad | Fix |
|-------|-----------|-----|
| **Ruta faltante** | 🔴 | Agregar a `app.routes.ts` |
| **Innavegable** | 🔴 | Sin ruta, usuario no puede llegar a la página |

---

## Notas

- **Componentes verificados:** Todos existen, tipados correctamente, con tests
  ⚠️ Ajustado en QA: el tipado **no** estaba correcto (ver D.3). Corregido en el rework.
- **API verificados:** `recinto.api.ts` usa `ConfigService.getApiUrlSafe()` (correctamente unificado en ODD anterior)
- **Role guard:** `['MASTER']` — la escritura de recintos es `configuracion:write` (solo MASTER).
  Desvía del `['MASTER','ADMIN']` original; ver D.1.
- **Icon:** `location-outline` (nombre Ionic). Agregado a `ICON_MAP` → `pi pi-map-marker`
  (antes caía al fallback `pi pi-file`). El menú usa `pi pi-map-marker` directo.

---

## Evidencia de verificación (2026-11-22)

| Comprobación | Resultado |
|--------------|-----------|
| `pnpm build` (producción) | ✅ verde (antes: 5 errores de compilación) |
| `pnpm test:ci` (vitest) | ✅ 111 archivos / 1153 tests (antes: 1151) |
| `pnpm test:ci` (a11y) | ✅ 3 archivos / 5 tests |
| `pnpm lint` | ✅ sin hallazgos |
| `pnpm format:check` | ✅ limpio |
| Ruta `admin/recintos` existe, lazy y `roles: ['MASTER']` | ✅ `app.routes.spec.ts` (test nuevo) |
| `/admin/recintos` en el menú de MASTER | ✅ `nav.config.spec.ts` (test nuevo) |
| E2E / prueba manual en navegador | ⛔ no ejecutados (fuera de alcance por instrucción) |

---

## Dependencias

- ✅ No depende de nada
- ✅ No bloquea nada
- 📦 Habilita: Acceso a gestión de recintos desde menu/rutas

---

## Riesgo

- **Bajo:** Una línea de ruta, sin cambios lógicos
- **Testing:** Solo verificar que tests frontend pasan
- **Real (QA):** el riesgo real era que la feature **no compilaba** y arrastraba CI rojo
  en `develop` desde `26a5636`. Ya está corregido.

---

## Archivos tocados

| Archivo | Cambio | Por qué |
|---------|--------|---------|
| `frontend/app/src/app/app.routes.ts` | Ruta `admin/recintos` (commit `b24e30b`, roles `['MASTER']` en `bc88fd9`) | Alcance original del ODD |
| `frontend/app/src/app/layout/nav.config.ts` | Item "Recintos" en Administración de MASTER | D.2 |
| `frontend/app/src/app/shared/ui/icon-map.ts` | `location-outline` → `pi pi-map-marker` | El icono de la ruta caía al fallback |
| `frontend/app/src/app/features/admin/pages/recintos/recinto-form.modal.ts` | `ButtonModule`; `rol` → `rolNombre` | D.3 (build) |
| `frontend/app/src/app/features/admin/pages/recintos/recinto-jrvs.modal.ts` | `ButtonModule`; import muerto de `Checkbox` | D.3 (build) |
| `frontend/app/src/app/features/admin/pages/recintos/recintos-list.page.ts` | `ButtonModule`; `meta.total` → `meta.totalElements`; imports muertos | D.3 y D.4 (build) |
| `frontend/app/src/app/features/admin/pages/recintos/*.spec.ts` | Mocks con la forma real de `meta` | D.4 |
| `frontend/app/src/app/app.routes.spec.ts` | Guarda de la ruta nueva | F |
| `frontend/app/src/app/layout/nav.config.spec.ts` | Guarda del item de menú | F |

---

## Deuda que queda (fuera de alcance)

- Los componentes de recintos usan `<button pButton>` de PrimeNG crudo en vez del design
  system del proyecto (`<app-button>`), y con clases legacy (`p-button-success`,
  `p-button-rounded p-button-sm`) que PrimeNG 21 ya no aplica. Es el único módulo de
  `features/admin` en esa situación (22 features usan `app-button`).
- ADMIN puede leer recintos por API (`configuracion:read`) pero no por UI (D.1).
- Sin E2E de la ruta (no existe CI de Playwright).

---

## Próximos Pasos

✅ **Verificado y listo para merge** (rework de QA aplicado).

---

## Referencia

Hallazgo de verificación: rama `brydyan/sc-520/feat-caching-strategy` luego de merge de develop.

**Archivos relacionados:**
- `frontend/app/src/app/app.routes.ts` (línea ~294)
- `frontend/app/src/app/features/admin/pages/recintos/recintos-list.page.ts`
- `frontend/app/src/app/features/admin/data/recinto.api.ts`

