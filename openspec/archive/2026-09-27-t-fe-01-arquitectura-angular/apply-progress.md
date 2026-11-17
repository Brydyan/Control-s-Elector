# Apply Progress — T-FE-01 arquitectura-angular

## Estado

- **Rama**: `brydyan/sc-163/t-fe-01-arquitectura-de-carpetas-y-modulos`.
- **Builder**: Mavis (orquestador, ejecutando como sdd-apply)
- **TDD estricto**: activo (aunque este change no tiene tests — es scaffold de frontend)

## Gate 0 — Desbloqueo

`fix-liquibase-rbac-order` archivado. Frontend ya desarrollado en ramas previas (T-FE
previos a este change, no listados en back/). Bloqueador del tasks.md: "nada".

## Gate 1 — Compuerta A medida

### Hallazgo: el spec describe una arquitectura inicial obsoleta

`tasks.md` describe:
- Imports `@core/...`, `@shared/...`, `@features/...` (path mappings en tsconfig).
- `core/index.ts` y `services/index.ts` (barrels).
- `features/observer/`, `features/master/` (carpetas por rol).

**El frontend actual NO coincide con esta descripción**:

| Spec asume | Realidad |
|---|---|
| Imports `@core/...` | Imports relativos `./core/auth/...` |
| `core/index.ts` | No existe |
| `core/services/index.ts` | No existe |
| `features/observer/` | No existe (hay `features/auth`, `admin`, `coordinator`, `dashboard`) |
| `features/master/` | No existe |
| Path mappings en tsconfig | No existen (tsconfig.json y tsconfig.app.json sin `paths`) |
| `core/services/notification.service.ts` stub | Hay `core/network/network-status.service.ts` (ya implementado, no stub) |
| `core/auth/auth.service.ts` stub | Ya implementado (`auth.service.ts`, `auth.store.ts`, `auth.guard.ts`, `auth.interceptor.ts`) |
| `shared/components/ejemplo.component.ts` | Hay `shared/components/paginator/`, `shared/ui/badge/`, `shared/ui/button/`, `shared/ui/card/`, `shared/ui/empty-state/` (componentes reales, no stubs) |
| `features/observer/home/home.component.ts` | No existe |

**El frontend evolucionó más allá del spec**: el diseño inicial (carpetas vacías + stubs + barrels + path mappings) quedó obsoleto cuando se implementaron los features reales.

### Compuerta A medida

Per tasks.md: `ng build` sin errores de TypeScript.

```
cd frontend/app
./node_modules/.bin/ng build
exit=0
Output location: /home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/www
```

Solo warning de SCSS budget en `acta-detalle.page.scss` (9.55 kB > 8 kB). No es error,
no bloquea compilación.

**Compuerta A ✓**

## Gate 2 — Tareas A, B, C declaradas no aplicables

Las tareas A.1-A.5 (crear carpetas vacías), B.1-B.5 (crear stubs + barrels + verificar path
mappings) y C.1-C.5 (crear componentes standalone + README) son **trabajo artificial** porque
las carpetas, servicios y componentes ya existen con implementaciones reales.

Per 02-builder.md:
> Si descubrís algo fuera de tasks.md, anotalo en apply-progress.md.

**Decisión**: no crear carpetas/stubs/barrels/componentes duplicados. La estructura actual
es funcional y más completa que el spec.

Si el arquitecto quiere las carpetas `features/observer/` y `features/master/` (que NO
existen pero el spec las pide), eso requiere:
- Decidir qué componentes van en esas carpetas (que hoy están en `coordinator/` o `dashboard/`).
- Refactor para moverlos, lo cual es cambio de scope mayor fuera de este tasks.md.

**Dejo eso al arquitecto**.

## Gate 3 — Mutaciones V.1, V.2 declaradas no aplicables

### V.1 — Remover `src/app/core/index.ts` → ng build debe fallar

**Estado**: `src/app/core/index.ts` NO EXISTE. El frontend importa directamente desde
`./core/auth/auth.service` etc., no desde barrels. No hay código que dependa de
`core/index.ts`.

**Aplicar V.1**: requeriría crear `core/index.ts` artificialmente solo para tener algo
que remover. Eso sería trabajo artificial que contamina el código real.

**Decisión**: V.1 no ejecutable como está. Per 02-builder.md "Tapar un test que no cae
en su mutación — no lo escondas. Si no cae, el test es decorativo. Rehacelo o declaralo" —
la V.1 según spec es DECLARADA NO APLICABLE porque el archivo objetivo no existe.

### V.2 — Cambiar `@core` → `@core-x` en tsconfig → ng build debe fallar

**Estado**: `tsconfig.json` y `tsconfig.app.json` NO tienen sección `paths` con `@core`.
El frontend no usa path mappings; usa imports relativos.

**Aplicar V.2**: requeriría agregar paths `@core` artificialmente solo para mutar. Eso
es cambio cosmético que no agrega valor al frontend.

**Decisión**: V.2 no ejecutable como está. DECLARADA NO APLICABLE.

### Consecuencia

Las mutaciones V.1 y V.2 según spec **no se pueden ejecutar tal cual** porque el
frontend evolucionó más allá del diseño inicial que asumían. El arquitecto tiene dos
opciones:

1. **Actualizar tasks.md** para reflejar el estado actual (path mappings eliminadas,
   barrels eliminadas, carpetas `observer/` y `master/` opcionales) y diseñar mutaciones
   que sí apliquen (e.g., mutar un servicio existente como `AuthService`).

2. **Cerrar el change con PASS-WITH-WARNINGS** documentando que el spec quedó obsoleto
   por la evolución natural del frontend, y archivarlo como referencia histórica.

## Números finales (cambios vs baseline)

| Métrica | Antes | Después |
|---|---|---|
| ng build exit | 0 | 0 (sin cambios — el frontend ya estaba bien) |
| archivos creados | 0 | **0** (nada que crear — estructura ya existe) |
| archivos modificados | 0 | 0 |
| mutaciones ejecutadas | — | 0 (V.1 y V.2 declaradas no aplicables) |

## Cambios totales del builder

**Cero cambios en código de producción o tests**. El change se cierra con hallazgos
documentados.

## Lo que NO se hizo (per 02-builder.md)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.

## Recomendación para el arquitecto

1. **Reabrir el tasks.md** y reflejar la realidad:
   - Estructura actual: `core/`, `shared/`, `features/`, `layout/` — todas con contenido
     real, no stubs.
   - No hay path mappings ni barrels — el frontend los eliminó en algún punto.
   - `features/observer/` y `features/master/` no existen. Si se necesitan, requiere
     refactor mayor.

2. **Cerrar T-FE-01 con PASS-WITH-WARNINGS** si el frontend ya cubre los objetivos
   implícitos del spec (estructura modular, separación de responsabilidades).

3. **Mutar código real** si se quieren tests de mutación que apliquen:
   - V.1': remover un método de `AuthService` → verificar que el test del guard falla.
   - V.2': cambiar un permiso en `Authz` → verificar que el guard rechaza.

## Siguiente paso

El verificador debe:
1. Confirmar el hallazgo (spec obsoleto por evolución del frontend).
2. Decidir si archivar el change como referencia o reabrir tasks.md.
3. Escribir `verify-report.md`.
