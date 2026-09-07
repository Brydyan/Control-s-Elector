# Spec — Modelo electoral (retrospectivo)

Dominio: `electoral`. Change: `2026-09-07-t-be-02-entidades-electorales`.

---

## RF-ELE-01 — Cada cargo tiene un nivel geográfico

GIVEN un cargo
THEN su `nivel` es NACIONAL, PROVINCIA, CANTON o PARROQUIA

Según `CLAUDE.md`:

| Cargo | Nivel | Aplica en |
|---|---|---|
| Consejero/a CPCCS | NACIONAL | todas las JRVs |
| Prefecto/a | PROVINCIA | todas las JRVs |
| Alcalde/sa | CANTON | JRVs del cantón |
| Concejal Urbano | CANTON | JRVs en zonas urbanas |
| Concejal Rural | CANTON | JRVs en zonas rurales |
| Vocal Junta Parroquial Rural | PARROQUIA | **sólo parroquias rurales** |

---

## RF-ELE-02 — `JRV_CARGO` define qué se vota en cada mesa

GIVEN una JRV
WHEN se consultan sus cargos activos
THEN se devuelven exactamente los cargos que se votan en esa mesa

Es la fuente de verdad de `ObserverController.cargosDeJrv` y del cálculo de pendientes de
`ObserverController.jrvs`. Si sobra un cargo, el dashboard espera un acta que nunca llegará;
si falta, un resultado real queda sin registrar.

**Mutación**: hacer que `findByJrvIdAndActivo` ignore el flag `activo`.
Debe caer un test que desactive un `JrvCargo` y compruebe que deja de aparecer.

---

## RF-ELE-03 — Un cargo de nivel PARROQUIA sólo aplica a parroquias rurales

GIVEN un cargo de nivel PARROQUIA (Vocal de Junta Parroquial Rural)
AND una JRV en una parroquia de tipo URBANA
THEN esa combinación **no** debería existir en `JRV_CARGO`

**Éste es el requisito que hoy nadie comprueba.** `infra/sql/03-seed.sql` documenta su
`JRV_CARGO` como «todos los cargos en todas las JRVs (cross join)», lo que produce
exactamente la combinación imposible.

GIVEN el seed de desarrollo
THEN o bien respeta esta regla
OR está declarado explícitamente como datos de prueba que no la respetan, y la validación
vive en el endpoint de configuración que usa el MASTER

Lo que no vale es el estado actual: una regla escrita en `CLAUDE.md` que ningún código aplica
y que un seed contradice en silencio.

---

## RF-ELE-04 — Un candidato pertenece a un cargo y a un partido

GIVEN un candidato
THEN tiene un cargo y un partido, ambos obligatorios

`ActaDetalle` registra votos por candidato; `dashboard/resultados` agrupa por
`candidato.cargo` y muestra `candidato.partido.siglas`. Un candidato sin cargo rompe la
agregación.

**Mutación**: hacer opcional la FK de `Candidato` a `Cargo`.
Debe caer un test que intente persistir un candidato sin cargo.

---

## RF-ELE-05 — Los votos de un acta son de candidatos de su mismo cargo

GIVEN un acta del cargo X
WHEN se registran sus detalles
THEN todos los candidatos referenciados pertenecen al cargo X

`ActaService.registrar` resuelve cada candidato por id y **no comprueba su cargo**. Nada
impide registrar en un acta de Alcalde los votos de un candidato a Prefecto: la suma
cuadraría, la validación pasaría, y el resultado del dashboard quedaría mal atribuido sin
ningún error visible.

**Mutación**: si existe la comprobación, quitarla — debe caer su test.
**Si no existe la comprobación**, este requisito es un hallazgo, no una verificación:
anotarlo y proponerlo. Es el hueco §4.1 entre dos módulos que se prueban por separado.

---

## RF-ELE-06 — El par (JRV, cargo) es único en `JRV_CARGO`

GIVEN una JRV y un cargo
THEN existe como mucho una fila que los relacione

La clave compuesta lo garantiza a nivel de esquema. Es la base sobre la que descansa la
unicidad de acta por `jrv_id + cargo_id` de T-BE-06.

---

## RNF-ELE-01 — Los DTOs exponen `publicId`

GIVEN cualquier respuesta con cargo, partido o candidato
THEN el identificador es el `publicId`, no el `Long` interno.
