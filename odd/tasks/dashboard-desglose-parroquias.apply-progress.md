# Apply Progress — Dashboard: desglose de parroquias

**ODD ID:** `dashboard-desglose-parroquias`
**Rama:** `brydyan/sc-510/odd-e2e-local-fix` (la actual)
**Fecha:** 2026-11-16
**Estado:** ✅ COMPLETADO — T1–T6 ejecutados y verificados, con verificación por mutación.

---

## Tasks completadas

- [x] **T1** — `ParroquiaProgresoDto` (record nuevo) + campo `parroquias` en `ProgresoCantonDto`.
- [x] **T2** — Consulta del desglose y anidado por cantón en `DashboardService.progreso()`.
- [x] **T3** — `DashboardContractIT`: el desglose viene poblado y cuadra con el cantón.
- [x] **T4** — `ErroresContractIT`: el spec OpenAPI declara la forma del desglose.
- [x] **T5** — Frontend: sin la fabricación `?? []`, color por avance, estado vacío.
- [x] **T6** — Journey E2E contra la API real + verificación por mutación.

---

## Archivos creados

| Archivo | Qué |
|---|---|
| `backend/electoral-api-quarkus/.../dashboard/web/dto/ParroquiaProgresoDto.java` | Record del desglose por parroquia |
| `frontend/app/e2e/dashboard-progreso-desglose.spec.ts` | Journey E2E contra la API real |

## Archivos modificados

| Archivo | Qué |
|---|---|
| `backend/electoral-api-quarkus/.../dashboard/web/dto/ProgresoCantonDto.java` | Campo `parroquias` |
| `backend/electoral-api-quarkus/.../dashboard/application/DashboardService.java` | Consulta del desglose + anidado (**solo el método `progreso()`**: el resto del archivo tenía un cambio ajeno sin commitear que no se tocó) |
| `backend/contract-tests/.../DashboardContractIT.java` | Desglose poblado + conciliación |
| `backend/contract-tests/.../ErroresContractIT.java` | Forma del desglose en el spec OpenAPI |
| `frontend/app/src/app/features/dashboard/data/dashboard.api.ts` | `getProgreso()` devuelve el DTO completo; se quita la fabricación `?? []` y el import muerto |
| `frontend/app/src/app/features/dashboard/pages/progreso-cantones/progreso-cantones.page.html` | Color por avance + estado vacío |
| `frontend/app/src/app/features/dashboard/pages/progreso-cantones/progreso-cantones.page.scss` | Variantes de color de la mini-barra |
| `frontend/app/src/app/features/dashboard/pages/progreso-cantones/progreso-cantones.page.ts` | Import de `EmptyStateComponent` |
| `frontend/app/src/app/features/dashboard/pages/progreso-cantones/progreso-cantones.rendering.spec.ts` | 2 tests de rendering |

---

## Test counts

| suite | antes | después | delta |
|---|---|---|---|
| backend `electoral-api-quarkus` (`mvn verify`) | 133 run / 0 fallos / 0 errores | **133 run / 0 fallos / 0 errores** | 0 (este módulo no lleva tests nuevos) |
| contrato `backend/contract-tests` | 122 | **124** (1 fallo ajeno) | +2 |
| frontend unit (vitest) | 980 | **982** | +2 |
| E2E dashboard (API real) | — | **2** | +2 |

**0 regresiones.** El "antes" del backend se midió con un `git worktree` en `5eb64f1` (el commit
previo), con el mismo comando y el mismo entorno.

**El único fallo del contrato no es de este trabajo**:
`ConfiguracionContractIT.listarCandidatosFiltraPorCargo` — el cargo `Prefecto`, agregado al seed
sin candidatos, deja vacío el listado de candidatos del primer cargo. Es un cambio ajeno que
estaba sin commitear en el árbol de trabajo.

---

## Verificación por mutación

Los tests nuevos muerden, y está medido:

| contra el backend **sin** el fix (árbol de `HEAD`) | resultado |
|---|---|
| `DashboardContractIT.progresoDevuelveElDesglosePorParroquia` | ❌ falla: `el canton tiene que traer la clave 'parroquias'` |
| `ErroresContractIT.elSpecOpenApiDeclaraLaFormaDelDesglosePorParroquia` | ❌ falla |
| journey E2E del desglose | ❌ falla |

| contra el backend **con** el fix | resultado |
|---|---|
| los tres | ✅ pasan |

Se levantó un segundo backend desde el worktree de `HEAD` contra una base de datos nueva (para que
el checksum del changelog no interfiriera) y se corrieron las tres suites contra él.

---

## Verificación funcional contra datos reales

`GET /api/v1/dashboard/progreso` sobre la base de dev:

```
La Libertad: totalJrvs=2 jrvsConActa=1 pct=50.0
   - Ayangue:       0/1 = 0.0%
   - La Libertad:   1/1 = 100.0%
   [OK] suma parroquias: total=2 (canton 2) | escrutadas=1 (canton 1)
Santa Elena: totalJrvs=3 jrvsConActa=3 pct=100.0
   - Ballenita:     1/1 = 100.0%
   - Santa Elena:   2/2 = 100.0%
   [OK] suma parroquias: total=3 (canton 3) | escrutadas=3 (canton 3)
```

---

## Commits

| Commit | Task |
|---|---|
| `8198e55` `fix(dashboard): el panel "Desglose de Parroquias" recibia un payload vacio` | T1–T5 |
| `4da7006` `test(e2e): journey del desglose de parroquias contra la API real` | T6 |

Formato convencional, sin co-author de IA. Los commits contienen **solo** el alcance de este
trabajo: el trabajo ajeno que estaba sin commitear en el árbol (incluido un cambio del mismo
`DashboardService.java`) quedó intacto y sin commitear.

---

## Hallazgos que quedan abiertos (ajenos a este ODD)

1. **El seed no produce un spread útil**: las 5 JRV del seed tienen actas en los 5 estados, así que
   en dev todas las parroquías salen al 100 %. El E2E sigue siendo verde porque la aserción es
   "las filas coinciden con la API", no "el avance es X".
2. **`ConfiguracionContractIT.listarCandidatosFiltraPorCargo` falla** por el cargo `Prefecto` sin
   candidatos.
3. **`025-data-seed.sql` editado en un changeset ya aplicado**: el checksum de
   `025-01-data-seed-dev` cambió (`9:82e82c6f` → `9:1ae2c0a6`) y el backend **ya no arranca**
   contra una base migrada con la versión anterior: `Validation Failed: 1 changesets check sum`.
   La forma correcta es un changeset nuevo, como hizo el `027` con el `025`.
4. **`openspec/archive/2026-09-26-t-fe-15-.../implementation/`** tiene una copia completa del
   frontend, con el endpoint `/progreso-detallado` que no existe. CodeGraph la indexa y su
   `impact` devuelve esas copias primero.
5. **CodeGraph no modela `record` de Java**: el `ProgresoCantonDto` del backend no es símbolo en
   el índice, justo el tipo que carga el contrato.

---

## Notas de método

- La verificación se hizo contra la API real: backend Quarkus en modo dev sobre la base de
  Postgres del compose, con el contexto `bootstrap-admin` para que la migración `026` creara el
  admin de arranque (el opt-in del ODD `quarkus-deps-jdk25` en acción).
- El E2E corrió contra un `ng serve` propio en el puerto 4300 con un proxy temporal a ese backend,
  sin tocar el `proxy.conf.json` versionado ni el `ng serve` del puerto 4200.
- El baseline de tests del backend se midió con `git worktree`, no con `git stash`: el stash llegó
  a registrar archivos del ODD como borrados y el `pop` los eliminó del árbol de trabajo.
