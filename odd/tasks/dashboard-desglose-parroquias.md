# ODD — Dashboard: desglose de parroquias

**Estado:** implementado y verificado (2026-11-16).
**Spec padre:** `T-FE-15 — Dashboard de Resultados y Progreso` (`openspec/archive/2026-09-26-t-fe-15-dashboard-resultados-progreso/`), decisión C.1: *"Progreso cantonal detallado con desglose de parroquias"*.

---

## Por qué este trabajo

El panel **"Desglose de Parroquias"** (`/dashboard/progreso`) se veía con su encabezado y sus
columnas, pero **sin una sola fila**. No era un bug de render: el frontend leía `parroquias` de
`GET /api/v1/dashboard/progreso` y el backend **nunca mandaba ese campo**.

### La cadena que lo dejó pasar

| Eslabón | Qué pasaba |
|---|---|
| El backend | `ProgresoCantonDto` (record) no tenía el campo `parroquias` |
| El contrato | `DashboardContractIT` assertaba `cantonId`, `cantonNombre`, `totalJrvs`, `jrvsConActa` y `porcentaje`. **No `parroquias`** |
| La API del front | `dashboard.api.ts` fabricaba `parroquias: (c as ProgresoCantonDto).parroquias ?? []` |
| El rendering spec | Usaba un fixture con parroquias; nunca tocaba HTTP |

La capa de API del frontend convertía **un contrato roto en un panel vacío y silencioso**. Y el
test de regresión del frontend (`dashboard.api.spec.ts`) fijaba que se llamara a `/progreso` y
**no** a `/progreso-detallado`: la consolidación de endpoints fue decisión, y en ese movimiento el
payload se perdió sin que nadie lo notara.

`/progreso-detallado` fue el endpoint del mock original de T-FE-15 y **nunca existió en ningún
backend**. El contrato que el frontend había construido sigue siendo el del spec C.1 de T-FE-15:
`parroquias` es un campo especificado que faltaba, no un añadido.

### Lo que CodeGraph mostró

- Los dos `ProgresoCantonDto` (TS y el del archivo archivado) son símbolos **distintos** y el
  record Java no entra en el índice: **CodeGraph no modela `record` de Java**. El punto ciego de la
  herramienta es exactamente la costura donde vive el bug.
- `impact` devuelve primero las **copias archivadas** de `openspec/archive/.../implementation/`,
  que todavía llaman `/progreso-detallado`. Un agente futuro edita el archivo equivocado.

---

## Scope

**A. Backend — el dato**
- `ParroquiaProgresoDto` (record nuevo): `parroquiaId`, `parroquiaNombre`, `jrvsEscrutadas`,
  `jrvsTotal`, `avance`.
- `ProgresoCantonDto` gana `List<ParroquiaProgresoDto> parroquias` (aditivo).
- `DashboardService.progreso()` calcula el desglose y lo anida por cantón.

**B. Contrato — que el hueco no vuelva**
- `DashboardContractIT`: el desglose viene poblado y **cuadra** con el cantón.
- `ErroresContractIT`: el spec OpenAPI declara la forma del desglose.

**C. Frontend — que se vea**
- Se quita la fabricación `?? []`.
- La mini-barra de parroquia toma el color de su avance (antes verde fijo).
- Estado vacío cuando el cantón no tiene parroquías.

**Fuera de scope:** el payload de `resumen`/`resultados`/`novedades`, el modelo electoral, y el
seed de datos (el seed no produce un spread útil: las 5 JRV del seed tienen actas en los 5
estados, así que en dev todas las parroquías saldrían al 100%).

---

## Specs

**S1 — El desglose viaja anidado en el cantón.** `GET /api/v1/dashboard/progreso` devuelve cada
cantón con su lista de parroquías. Cambio de cantón en la UI **no** hace llamada HTTP.

**S2 — Misma semántica en los dos niveles.** Una parroquia cuenta los JRVs con al menos un acta
**no anulada** (cualquier estado: ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA), igual que el cantón.
`COUNT(DISTINCT)`: una JRV con tres cargos cuenta una vez. El nombre de la columna
("JRVs Escrutadas") es el del contrato que ya consume el frontend; **no** significa "solo
APROBADAS".

**S3 — Los dos niveles cuadran.** La suma de `jrvsTotal` de las parroquias de un cantón es su
`totalJrvs`; la suma de `jrvsEscrutadas` es su `jrvsConActa`. Es la invariante que hace visible un
cambio de semántica en uno de los dos niveles.

**S4 — Una parroquia sin JRV aparece.** Se calcula con `LEFT JOIN` desde `parroquia`, con el filtro
de `ANULADA` en el `ON` (en el `WHERE` el `LEFT JOIN` se volvería `INNER` y las parroquias sin JRV
con acta desaparecerían del desglose). "No hay JRV" es un dato distinto de "no hay avance".
`jrvsTotal == 0` → `avance = 0.0`, sin división por cero ni `NaN`.

**S5 — RLS no se salta.** La consulta lee `acta` (tabla con RLS forzado) y el service llama
`activarContexto()` como el resto de las consultas del dashboard. El guard del endpoint es
`ADMIN|MASTER|COORDINATOR`, los mismos roles que la política de `acta` permite ver todo.

> **Cuidado, y es un hallazgo de la verificación**: en dev y en CI el backend conecta como
> `electoral_user`, que es **superusuario**, y Postgres **no aplica RLS a un superusuario**
> (tampoco con `FORCE ROW LEVEL SECURITY`). O sea que, en esos entornos, la capa RLS está
> **inerte**: la consulta ve todas las filas haya contexto o no. Mutación comprobada: backend con
> `activarContexto()` desactivado → **mismos números** → el contexto no cambia nada ahí.
>
> Consecuencia honesta: la evidencia de que RLS filtra es `RlsIsolationIT`, que crea su **propio rol
> sin privilegios** (esa es la única prueba real de aislamiento). El service está bien (el
> `activarContexto()` es lo correcto y es lo que hace falta en prod, donde el rol de Supabase no es
> superusuario), pero **el camino real de la app no está probando RLS**.

**S6 — El contrato lo dice, en los dos sitios.** El body de la respuesta trae `parroquias`
poblado, y el spec OpenAPI declara `ProgresoCantonDto.properties.parroquias` con items
`ParroquiaProgresoDto`.

**S7 — Si no hay parroquías, se dice.** El panel muestra un estado vacío explícito, no una tabla
muda.

---

## Tasks

- [x] **T1** — `ParroquiaProgresoDto` + campo `parroquias` en `ProgresoCantonDto`.
- [x] **T2** — Consulta del desglose y anidado por cantón en `DashboardService.progreso()`.
- [x] **T3** — `DashboardContractIT`: desglose poblado + conciliación con el cantón.
- [x] **T4** — `ErroresContractIT`: la forma del desglose en el spec OpenAPI.
- [x] **T5** — Frontend: sin `?? []`, color por avance, estado vacío.
- [x] **T6** — Journey E2E contra la API real + verificación por mutación.

---

## Log

### L1 — 2026-11-16 — Implementación

Backend: el desglose se calcula en una tercera consulta nativa (mismo patrón y mismo criterio que
las dos del cantón), con `LEFT JOIN` desde `parroquia` y `ANULADA` filtrado en el `ON`. La lista
va anidada con `List.copyOf`, y el DTO es inmutable por ser record.

Frontend: `getProgreso()` pasa a devolver el DTO completo, así que `getProgresoDetalles()` ya no
fabrica nada. La mini-barra toma el color del avance con el mismo criterio que las tarjetas
cantonales (verde ≥80 %, azul 50-80 %, ámbar <50 %).

**Bug encontrado durante la verificación en vivo**: la primera versión leía las columnas de la
consulta al revés (`fila[3]` es el total, no las escrutadas) y producía `jrvsEscrutadas > jrvsTotal`
en las parroquias sin JRV. Lo detectó la conciliación de S3, que es exactamente para lo que está.

### L2 — 2026-11-16 — Verificación

| suite | antes | después |
|---|---|---|
| backend `electoral-api-quarkus` | 133 / 0 fallos | **133 / 0 fallos** |
| contrato `backend/contract-tests` | 122 | **124** (+2 nuevos), 1 fallo ajeno |
| frontend unit | 980 | **982** (+2 nuevos) |
| E2E dashboard | — | **2** (nuevos, contra la API real) |

Verificado contra la base de dev: `GET /dashboard/progreso` devuelve el desglose y las sumas
cuadran con los totales cantonales.

**Verificación por mutación**: contra el backend **sin** el fix (árbol de `HEAD`), los dos tests de
contrato nuevos fallan (`el canton tiene que traer la clave 'parroquias'`) y el journey E2E falla.
Contra el backend con el fix, los tres pasan. Los tests muerden.

**Hallazgo ajeno al alcance**: el contrato tiene 1 fallo que no es de este trabajo — el cargo
`Prefecto` (agregado al seed sin candidatos) deja vacío el listado de candidatos del primer cargo,
y `ConfiguracionContractIT.listarCandidatosFiltraPorCargo` lo detecta.
