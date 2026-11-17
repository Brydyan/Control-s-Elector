# Spec — Dashboard (delta)

Dominio: `dashboard`. Change: `2026-09-07-t-be-13-dashboard`.

---

## RF-DASH-01 — Acceso: ADMIN, MASTER y COORDINATOR; nadie más

GIVEN un usuario con rol ADMIN, MASTER o COORDINATOR
WHEN llama cualquier `GET /api/v1/dashboard/**`
THEN recibe `200 OK`

GIVEN un usuario con rol OBSERVER
THEN recibe `403 Forbidden`

GIVEN una petición sin token, o con token expirado
THEN recibe `401 Unauthorized`

**Mutación**: quitar el `@PreAuthorize` de clase del `DashboardController`.
Debe caer el test de OBSERVER → 403.

---

## RF-DASH-02 — El módulo es de sólo lectura

GIVEN el `DashboardController`
THEN **todos** sus métodos están anotados con `@GetMapping`
AND no existe ningún `@PostMapping`, `@PutMapping`, `@PatchMapping` ni `@DeleteMapping`

Es la traducción estructural de «ADMIN nunca escribe» (`CLAUDE.md`, Roles). La garantía no
está en un guard que se pueda borrar: está en que el método no existe.

**Verificación**: un test que recorra por reflexión los métodos del controlador y falle si
alguno lleva una anotación de escritura. Sobrevive al refactor que un guard no sobrevive.

---

## RF-DASH-03 — `GET /resumen` cuenta actas por estado, sin ANULADA

GIVEN actas en estados ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA y ANULADA
WHEN se llama `GET /api/v1/dashboard/resumen`
THEN la respuesta trae el conteo de los cuatro primeros
AND `totalActivas` es su suma
AND las ANULADA **no** están en ningún contador ni en el total

GIVEN que no hay ninguna acta
THEN todos los contadores son `0` y la respuesta es `200`, no un error

**Mutación**: añadir `countByEstado(EstadoActa.ANULADA)` al total.
Debe caer el test de exclusión de ANULADA en `/resumen`.

---

## RF-DASH-04 — `GET /resultados` suma votos por candidato, sin ANULADA

GIVEN dos actas del mismo cargo, una APROBADA con 90 votos para el candidato 1 y otra
ANULADA con 500 votos para el mismo candidato
WHEN se llama `GET /api/v1/dashboard/resultados`
THEN el candidato 1 aparece con **90** votos, no con 590

AND el resultado viene agrupado por cargo
AND dentro de cada cargo, ordenado por votos descendente

Éste es el requisito donde un fallo cambia un resultado electoral publicado. Un acta anulada
que sume votos es un resultado falso presentado como oficial.

**Mutación**: quitar `WHERE d.acta.estado != 'ANULADA'` de la query de agregación.
Debe caer el test de los 90 vs 590 votos.

---

## RF-DASH-05 — `GET /progreso` cuenta JRVs con acta, por cantón

GIVEN un cantón con 5 JRVs, de las cuales 2 tienen al menos un acta no anulada
WHEN se llama `GET /api/v1/dashboard/progreso`
THEN ese cantón reporta `totalJrvs = 5`, `jrvsConActa = 2`, `porcentaje = 40.0`

GIVEN una JRV cuya única acta está ANULADA
THEN esa JRV **no** cuenta como registrada

GIVEN un cantón sin ninguna JRV
THEN `porcentaje` es `0`, no una división por cero ni `NaN`

GIVEN una JRV con actas de tres cargos distintos
THEN cuenta **una** vez, no tres (`COUNT(DISTINCT jrv.id)`)

**Mutación 1**: cambiar `COUNT(DISTINCT a.jrv.id)` por `COUNT(a.jrv.id)`.
Debe caer el test de la JRV con tres cargos.
**Mutación 2**: quitar el filtro de ANULADA. Debe caer el test de la JRV con acta anulada.

---

## RF-DASH-06 — `GET /novedades` lista actas EN_DISPUTA con su antigüedad

GIVEN actas en estado EN_DISPUTA
WHEN se llama `GET /api/v1/dashboard/novedades`
THEN cada una aparece con `tipo = "ACTA_EN_DISPUTA"`, su JRV, su cargo, el OBSERVER que la
registró, el motivo y los minutos transcurridos desde su registro

GIVEN un acta en cualquier otro estado
THEN no aparece

En esta versión el endpoint **no** incluye evidencias con observaciones: depende de T-BE-16,
cuyo enum está sin definir. El campo `tipo` existe para que añadirlas después sea aditivo.

---

## RF-DASH-07 — El tiempo transcurrido no depende de dónde se calcula

GIVEN un acta registrada en un instante conocido
WHEN se calcula `minutosTranscurridos` con `TZ=UTC` y con `TZ=America/Guayaquil`
THEN el resultado es **el mismo número**

Un valor que forma parte del producto —cuánta urgencia tiene un caso— no puede cambiar según
la zona horaria del proceso que lo calcula. En local el error se cancela solo y no se ve;
en Railway, que corre en UTC, aparecería un desfase de 5 horas en la urgencia.

**Mutación**: reintroducir el `ZoneId.systemDefault()` del task doc (`T-BE-13.md:374-377`).
Debe caer el test que corre con las dos zonas.

---

## RNF-DASH-01 — Las cuatro respuestas son correctas con la base vacía

GIVEN una base de datos sin actas, sin cantones y sin candidatos
WHEN se llaman los cuatro endpoints
THEN los cuatro responden `200` con listas vacías o contadores en cero

Nunca `500`, nunca `NaN`, nunca división por cero. El dashboard se abre antes de que exista
el primer acta: ése es su estado inicial normal, no un caso límite.
