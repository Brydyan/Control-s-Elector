# Design — T-BE-12 · Cierre de coordinación

---

## D1 — El código es la autoridad; el task doc se corrige

**Decisión**: donde `T-BE-12.md` y `CoordinatorController` difieren, se corrige el documento.
No se toca el controlador.

**Alternativa descartada — alinear el código con el documento.** El doc propone
`/api/coordinator`, `SecurityContextHolder.getPrincipal()` casteado a `Long`, y
`findWithFilters` de tres parámetros. Las tres cosas son peores que lo que hay: el prefijo
`/api/v1` es la convención del resto de la API, el principal real es un `UserPrincipal` con
`publicId` UUID (castear a `Long` reventaría), y el cuarto parámetro `registradaPorId` es el
que `backend.md:194` pide para filtrar por OBSERVER. Alinear hacia el doc sería una regresión
escrita a propósito.

**Lo que queda escrito**: los task docs de `documents/task-control/` son material pedagógico,
no especificación. Cuando uno contradice al código, gana el código y el doc se corrige en el
mismo change.

---

## D2 — Verificar por mutación antes de renombrar a `COMPLETADO-`

**Decisión**: el renombrado (tarea D.1) va **después** de las mutaciones, no antes.

**Alternativa descartada — renombrar ya, porque el código evidentemente existe.** Es
exactamente lo que produjo el estado actual: `backend.md` marca T-BE-12 como COMPLETO, el
PR #66 está mergeado, y sin embargo los 11 tests que supuestamente lo prueban llevan semanas
en ERROR sin que nadie lo notara. «Existe el controlador» y «funciona la garantía» son
afirmaciones distintas, y sólo la segunda justifica el prefijo.

Es el mismo defecto que T-IN-11, donde una casilla `[x]` decía «correr tests» mientras la
compuerta corría con `-DskipTests`.

---

## D3 — El filtro por cantón se decide midiendo, no ahora

**Decisión**: se deja como decisión explícita del Builder en la tarea C.2, con las dos rutas
descritas y la obligación de anotar cuál eligió.

**Por qué no lo decido aquí**: depende de si el frontend lo va a consumir. `T-FE-08`
(«Vista global de actas») lista los filtros «estado, cantón, JRV, cargo, registrada_por» —
si esa pantalla se construye, el filtro hace falta; si se recorta, sobra. Elegir ahora sería
adivinar el alcance de un change de otra área.

**Lo que no se acepta**: dejarlo como está. Hoy `backend.md:192` promete una capacidad que
`findWithFilters` no tiene. Una promesa sin implementación es peor que la ausencia de las
dos: el frontend la va a llamar.

---

## Riesgo asumido

Al levantar el `ApplicationContext` por primera vez en semanas, es probable que aparezcan
fallos reales en estos 11 tests que hoy están tapados por el error de arranque. **Es el
resultado esperado, no un problema del change.** Se miden, se anotan con su número, y si
alguno bloquea se abre `fixes-required.md`. Lo que no se hace es ajustar el test para que
pase.
