# Tasks — T-FE-01: Arquitectura Angular Base (Validación de Implementación)

## Fase A: Validación de Estructura de Carpetas

✅ **COMPLETADO** — Estructura ya implementada y funcional.

- [x] **A.1** — Carpeta `src/app/core/` existe con `auth/`, `menu/`, `network/`.
- [x] **A.2** — Carpeta `src/app/shared/` existe con `ui/`, `components/`, `models/`, `errors/`, `a11y/`.
- [x] **A.3** — Carpeta `src/app/features/` existe con features por rol: `observer/`, `coordinator/`, `admin/`, `dashboard/`, `auth/`, `placeholder/`.
- [x] **A.4** — Carpeta `src/app/layout/` existe (shell de aplicación).
- [x] **A.5** — Verificación: `ng build` exit 0, sin errores de TypeScript.

**Compuerta A:** ✅ PASS — Compilación correcta.

---

## Fase B: Validación de Servicios Singleton

✅ **COMPLETADO** — Servicios implementados y funcionales.

- [x] **B.1** — `core/auth/auth.service.ts` existe (implementado, no stub).
- [x] **B.2** — `core/auth/auth.store.ts`, `auth.guard.ts`, `auth.interceptor.ts` existen (completos).
- [x] **B.3** — `core/menu/menu.service.ts` existe (implementado).
- [x] **B.4** — `core/network/network-status.service.ts` existe (implementado).
- [x] **B.5** — Componentes importan servicios correctamente (autores verificados en features/).

**Compuerta B:** ✅ PASS — Servicios singleton funcionales.

---

## Fase C: Validación de Componentes Standalone

✅ **COMPLETADO** — Componentes reales, no stubs.

- [x] **C.1** — Componentes UI standalone en `shared/ui/`: Badge, Button, Card, EmptyState, Paginator.
- [x] **C.2** — Componentes complejos standalone en `shared/components/` (reutilizables).
- [x] **C.3** — Componentes en `features/` importan y usan componentes de shared sin errores.
- [x] **C.4** — Renderización sin errores validada vía `ng build`.
- [x] **C.5** — Documentación: README explica la estructura.

**Compuerta C:** ✅ PASS — Componentes standalone y reutilizables funcionales.

---

## Mejoras Futuras (Opcional, No Bloqueante)

Estas tareas NO están en scope de T-FE-01. Se proponen para futuros T-FE-* si se decide mejorar.

### Opción F1: Agregar tsconfig Path Aliases

- [ ] Configurar `paths` en tsconfig.json: `@core/*`, `@shared/*`, `@features/*`, `@layouts/*`.
- [ ] Refactorizar imports en features/ de relativos a aliases.
- [ ] Validar `ng build` sin errores.

**Cuándo:** Si imports relativos se vuelven tedious o llegan a 5+ niveles de `../`.

### Opción F2: Agregar Barrels (index.ts)

- [ ] Crear `core/index.ts` que re-exporta servicios públicos.
- [ ] Crear `shared/ui/index.ts`, `shared/components/index.ts`, etc.
- [ ] Refactorizar imports de componentes a usar barrels.
- [ ] Validar `ng build` sin errores.

**Cuándo:** Si la navegación entre imports se vuelve confusa.

---

## Qué NO Aplica en Esta Validación

- **No crear stubs** — Componentes y servicios ya son reales
- **No agregar rutas nuevas** — T-FE-02
- **No agregar guards/interceptors nuevos** — T-FE-03, T-FE-04
- **No escribir lógica de negocio nueva** — Está fuera de scope
- **No compilar para producción** — `ng build` dev es suficiente

---

## Compuertas Finales

| Fase | Comando | Expected | Resultado |
|------|---------|----------|-----------|
| A | `ng build` | exit 0 | ✅ PASS |
| B | `ng build` | exit 0 | ✅ PASS |
| C | `ng build` | exit 0 | ✅ PASS |

**Todos los gates pasaron.** Arquitectura validada como funcional.

---

## Mutaciones (Per Spec Anterior — No Aplicables)

**V.1** — Remover `src/app/core/index.ts` → ng build debe fallar

**Estado:** `core/index.ts` NO EXISTE en el proyecto actual. Los imports son relativos, no vía barrels. **Mutación no aplicable** como está planteada.

**V.2** — Cambiar path en tsconfig → ng build debe fallar

**Estado:** tsconfig NO tiene paths configuradas. Los imports son relativos. **Mutación no aplicable** como está planteada.

**Conclusión:** Las mutaciones del spec anterior asumían barrels y paths. Como éstos no están implementados, no se pueden ejecutar como estaban. El código actual funciona sin ellos.
