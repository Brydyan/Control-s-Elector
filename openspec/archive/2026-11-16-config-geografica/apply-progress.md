# Apply Progress — Configuración Geográfica Jerárquica

**SDD:** `2026-11-16-config-geografica` (`openspec/changes/back/`)
**Rama:** `brydyan/sc-516/sdd-configuracion-geografica-jerarquica`
**Inicio:** 2026-11-16 · **Cierre:** 2026-11-17
**Estado:** ✅ COMPLETADO — fases A–F ejecutadas, con las desviaciones del spec anotadas abajo.

---

## Tareas completadas

- [x] **A) Backend: endpoint del árbol completo**
  - [x] `ListarGeografiaUseCase`: una query con `LEFT JOIN`s sobre `provincia → canton → parroquia → jrv`
  - [x] `GeografiaService` como fachada; `MasterGeografiaResource` (`GET /api/v1/master/geografia`)
  - [x] `GeografiaTreeDto` con los cuatro niveles anidados
  - [x] Permiso `configuracion:read` (MASTER + ADMIN) en el changelog `035-config-geografica.sql`
  - [x] Tests: `GeografiaServiceIT` (2) + `GeografiaContractIT` (5)
  - Commits: `904e201`, `eabbb97`, `c1d59e0`

- [x] **B) Backend: CRUD de cantones**
  - [x] `POST/PUT/DELETE /api/v1/master/cantones` (`MasterCantonResource`)
  - [x] Código único por provincia → 409; `CantonResponse` gana `provinciaId` (aditivo)
  - [x] Borrado en cascada bloqueado: 409 con `fields.childrenCount` y `fields.hint`
  - [x] Tests: `GeografiaCantonIT` (8) + `GeografiaCantonContractIT` (7)
  - Commits: `a9656d6`, `eabbb97`, `c1d59e0`

- [x] **C) Backend: CRUD de parroquias y JRVs**
  - [x] `POST/PUT/DELETE /api/v1/master/parroquias` y `/api/v1/master/jrvs`
  - [x] Nombre de parroquia único dentro del cantón; número de JRV único en el sistema
  - [x] Parroquia con JRVs → 409 (`childrenCount`); JRV con actas → 409 (`actasCount`)
  - [x] El conteo de actas activa el contexto de RLS del MASTER (`RlsContexto.activar`)
  - [x] Tests: `GeografiaParroquiaJrvIT` (14) + `GeografiaParroquiaJrvContractIT` (8)
  - Commits: `dad0fa1`, `eabbb97`, `c1d59e0`

- [x] **C-bis) Backend: CRUD de provincias** (añadido, ver "Desviaciones" §1)
  - [x] `POST/PUT/DELETE /api/v1/master/provincias`
  - [x] Código opcional pero único; provincia con cantones → 409 (`childrenCount`)
  - [x] Tests: `GeografiaProvinciaIT` (9) + `GeografiaProvinciaContractIT` (6)
  - Commit: `e0166fa`

- [x] **D) Frontend: página + árbol**
  - [x] `ConfiguracionGeograficaPage` con `p-tree` de PrimeNG
  - [x] `GeografiaApi` (un servicio para los cuatro niveles) + `geografia.model.ts`
  - [x] "Expandir todo" / "Colapsar todo"; el estado de expansión sobrevive a las recargas
  - [x] Estados de loading, error con reintento y vacío
  - [x] `SCSS` responsive (a 768 px el árbol pasa a flujo)
  - [x] Tests: `configuracion-geografica.page.spec.ts` (9)
  - Commit: `c16341a`

- [x] **E) Frontend: cuatro modales CRUD**
  - [x] `ProvinciaModalComponent`, `CantonModalComponent`, `ParroquiaModalComponent`, `JrvModalComponent`
  - [x] `DynamicDialog` + `ReactiveForms`, como los modales de configuración electoral
  - [x] Validación en el cliente (incluido `sinSoloEspacios`, que replica el `@NotBlank` del backend)
  - [x] Toast de éxito; el error se muestra en el modal con su motivo (409/400), sin duplicar en toast
  - [x] Tests: 5 + 5 + 4 + 5 = 19
  - Commit: `b81a693`

- [x] **F) Integración: menú + permisos + E2E**
  - [x] Menú `admin.geografia` → `/master/configuracion-geografica` (ya estaba en el changelog 035)
  - [x] `MenusContractIT`: el menú solo lo ve MASTER (lo prueban los cuatro roles)
  - [x] Ruta protegida con `roleGuard` (`roles: ['MASTER']`)
  - [x] E2E contra la API real (5 casos): carga, expande, expande todo, crea un cantón, OBSERVER expulsado
  - Commits: `0f3cd7e`, `52cf1ea`

---

## Tests ejecutados

**Backend (Quarkus, `mvn -o -B -ntp verify`):**
```
antes:  133 tests
después: 166 tests   (+33: 9 de A, 9 de B, 14 de C, 1 de provincias)
resultado: ✅ 166/166 en verde
```

**Contrato (`backend/contract-tests`, contra el backend vivo):**
```
antes:  124 tests
después: 152 tests   (+28: geografía A/B/C/provincias + menú + dashboard + robustez)
resultado: ✅ 152/152 en verde
```

**Frontend (`pnpm test:ci`):**
```
antes:  982 tests (+ 5 a11y)
después: 1010 tests (+ 5 a11y)   (+28: 9 de página + 19 de modales)
resultado: ✅ 1010/1010 + 5/5 a11y + lint limpio + prettier limpio
```

**E2E (Playwright, contra la API real en `ng serve` + backend 8090):**
```
5 casos nuevos en e2e/master-config-geografica.spec.ts — ✅ 5/5 en verde
```

---

## Mutaciones verificadas

Corridas **contra el backend vivo** (hot reload) con la suite de contrato y los IT de
integración en cada vuelta. Todas atrapadas:

| # | Mutación | Atrapada por |
|---|---|---|
| A1 | Sin `LEFT JOIN` a canton | contrato (3 fallos) + IT (1 fallo) |
| A2 | Sin `JOIN` de parroquia por cantón | contrato: el árbol y el dashboard dejan de listar las mismas parroquias (1 fallo) |
| B1 | Sin validación de código único de cantón | contrato (mensaje "en esa provincia") + IT |
| B2 | Sin check de hijos al eliminar cantón | contrato (409 con `childrenCount`) + IT |
| C1 | Sin check de JRVs al eliminar parroquia | contrato (`childrenCount`) + IT |
| C2 | Sin check de actas al eliminar JRV | contrato (`actasCount`) + IT |

Las primeras tres pruebas **no** atrapaban las mutaciones, y eso obligó a **arreglar las
pruebas**, no el código (commit `c1d59e0`):

1. El 409 por código repetido lo produce **también** el índice único de la base: el test solo
   miraba el status. Ahora fija el mensaje del chequeo de la aplicación.
2. El 409 en cascada del cantón solo tenía un IT, sin prueba de contrato.
3. El `JOIN` de parroquia por cantón no era observable con pruebas de forma: se comparó el
   árbol de geografía con el desglose del dashboard.

---

## Commits

| Commit | Fase |
|---|---|
| `904e201` | A — `GET /api/v1/master/geografia` con el árbol |
| `a9656d6` | B — CRUD de cantones |
| `eabbb97` | Tests — aislar los tests que comparten la base |
| `dad0fa1` | C — CRUD de parroquias y JRVs |
| `e0166fa` | C-bis — CRUD de provincias |
| `c16341a` | D — página con el árbol |
| `b81a693` | E — cuatro modales CRUD |
| `0f3cd7e` | F — ruta protegida + E2E |
| `52cf1ea` | F — menú solo MASTER + limpieza de la geografía de prueba |
| `c1d59e0` | Tests — pruebas que atrapan las mutaciones |

---

## Desviaciones del spec (y por qué)

1. **Se añadió el CRUD de provincias al backend.** `tasks.md` no lo pedía, pero
   `specs/frontend.md` sí: el modal de crear provincia hace `POST /api/v1/master/provincias` y
   ese endpoint no existía. Sin él, el botón de la raíz del árbol no tenía a quién llamar.
2. **`proposal.md` dice que ADMIN ve la geografía; `tasks.md` exige menú oculto y ruta
   MASTER-only.** Se implementó `tasks.md`: la lectura del árbol sí la tiene ADMIN
   (`configuracion:read`), pero la pantalla (menú y ruta) es solo de MASTER.
3. **El JSON del spec está en `snake_case` (`provincia_id`, `es_urbana`, `total_electores`); la
   API del repo usa `camelCase`.** Se implementó camelCase, que es la convención del proyecto.
4. **`childrenCount` y `hint` van en el bloque `fields` del shape estándar de error**, no en la
   raíz del body como sugiere el ejemplo del spec.
5. **"Código único por provincia" vs índice único global.** `ix_canton_codigo` es global: un
   código repetido en otra provincia también es 409 (lo devuelve el índice, con el mensaje
   "Ya existe un canton con codigo X"). La validación de la aplicación es por provincia y da el
   mensaje "…en esa provincia". Las dos reglas conviven; el índice manda.
6. **Los modales usan `DynamicDialog` + `ReactiveForms`** (el patrón de los modales de
   configuración electoral), no `@Input() visible` / `@Output() confirm` del sketch del spec.
7. **"No eliminar si tiene hijos" es 409, no un 400.** El spec lo describe como conflicto en
   todas partes; la implementación devuelve 409 con el detalle de cuántos hijos bloquean.

---

## Hallazgos que quedan abiertos (ajenos a este SDD)

1. ⚠️ **La capa RLS está inerte en dev, en CI y en el k8s actual.** El backend conecta como
   `electoral_user`, que la imagen de Postgres crea como **superusuario** (`rolsuper=t`,
   `rolbypassrls=t`): Postgres no le aplica RLS ni con `FORCE ROW LEVEL SECURITY`. Medido en dev:
   con el rol de la app, `count(*) de acta` da **0** sin contexto y **206** con contexto; con el
   superusuario da **206** en los dos casos. El service está bien (activa el contexto), pero el
   camino real de la app no prueba RLS. **Arreglado por ODD aparte: `odd/tasks/rls-app-role.md`**
   (rol de aplicación sin superusuario + grants + datasource + CI + k8s).
2. ⚠️ **`025-data-seed.sql` editado en un changeset ya aplicado**: el checksum de
   `025-01-data-seed-dev` cambió y el backend no arranca contra una base migrada con la versión
   anterior (`Validation Failed: 1 changesets check sum`). La forma correcta es un changeset
   nuevo.
3. ⚠️ **`ConfiguracionContractIT.listarCandidatosFiltraPorCargo` fallaba** por el cargo
   `Prefecto` (agregado al seed, sin candidatos) y por el test, que asumía que el primer cargo
   de la lista tenía candidatos. Arreglado en `241bbc3`: el cargo de la prueba sale de un
   candidato que ya existe.
4. ⚠️ **La suite de contrato dejaba parroquias y JRVs en la base** con cada corrida. Corregido
   en `52cf1ea` (limpieza al terminar cada test).

---

## Próximos pasos

→ Listo para `/03-verify-sdd 2026-11-16-config-geografica`.
→ En paralelo: `odd/tasks/rls-app-role.md` (el hueco de RLS).
