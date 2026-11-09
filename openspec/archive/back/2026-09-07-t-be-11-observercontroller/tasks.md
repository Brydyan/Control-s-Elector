# Tasks: 2026-09-07-t-be-11-observercontroller

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-11.md`
Rama: `verify/t-be-11-observercontroller`, sacada de `develop`.

**Change retrospectivo**: verifica. Excepción: C, que corrige documentos antes de que el
frontend se escriba contra ellos.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Verificar por mutación

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. `ObserverControllerTest`: 11 tests, esperado
  0 error tras el change bloqueante. Si siguen en ERROR, parar.

- [ ] **A.2** — RF-OBS-API-02. Quitá `@PreAuthorize("@authz.has('acta:upload')")` de
  `registrarActa`. Debe caer el test de COORDINATOR o ADMIN → 403.

- [ ] **A.3** — RF-OBS-API-03. Pasá `null` como `observerId` en `misActas`.
  Debe caer el test de aislamiento entre OBSERVERs.

  Sin esa garantía, cada veedor vería las actas de toda la provincia.

- [ ] **A.4** — RF-OBS-API-04. Cambiá `EstadoActa.EN_DISPUTA` por `null` en
  `correccionesPendientes`. Debe caer su test.

- [ ] **A.5** — RF-OBS-API-05. Cambiá el `false` por `true` en la llamada a
  `actaService.corregir` de `corregirActa`.
  Debe caer el test de corrección de un acta ajena.

  **Es un solo literal.** Ningún error de compilación, y convierte a cualquier OBSERVER en
  corrector universal. Si esta mutación no hace caer nada, la garantía no está probada donde
  se puede romper.

- [ ] **A.6** — RF-OBS-API-06. Quitá el filtro
  `.filter(a -> a.getEstado() != EstadoActa.ANULADA)` de `cargosDeJrv`.
  Debe caer un test que anule un acta y compruebe que su cargo vuelve a estar disponible.

---

## B · Medir `GET /jrvs` antes de opinar

- [ ] **B.1** — RF-OBS-API-08. Cargar ~450 JRVs con actas (Santa Elena 200, La Libertad 150,
  Salinas 100 — las cifras de `CLAUDE.md`) y **medir**:

  - tiempo de respuesta de `GET /api/v1/observer/jrvs`;
  - número de consultas SQL emitidas (activá el log de Hibernate o un contador).

  Anotá los dos números en `apply-progress.md`.

  **Lo que espero encontrar**: ~900 consultas. `toJrvConEstadoDto` llama a
  `findByJrvIdAndActivo` y a `findByJrvId` **por cada JRV**, y trae todas las actas de la mesa
  a memoria para contar cargos distintos.

  **Trampa**: con las 15 JRVs del seed son 30 consultas y no se nota nada. Hay que cargar
  datos realistas o esta tarea no mide nada.

- [ ] **B.2** — Con el número delante, decidir:
  - si es aceptable, anotarlo y seguir;
  - si no, abrir un change de optimización (una consulta agregada en vez de N+1) — **no lo
    arregles aquí**: cambiar esa consulta necesita su propio test de equivalencia de
    resultados.

  Es el endpoint que abre la app del veedor, en red móvil, el día de la elección. La decisión
  merece el número.

---

## C · Corregir los documentos antes de que el frontend se escriba

- [ ] **C.1** — RF-OBS-API-01. Corregir las rutas en `documents/task-control/backend.md`
  (líneas 177-181) y en la cabecera de `COMPLETADO-T-BE-11.md`:

  ```
  POST   /api/v1/observer/actas                      (no POST /api/actas)
  PUT    /api/v1/observer/actas/{id}/corregir        (no PATCH /api/actas/{id})
  GET    /api/v1/observer/mis-actas
  GET    /api/v1/observer/correcciones-pendientes
  GET    /api/v1/observer/jrvs
  GET    /api/v1/observer/jrv/{jrvId}/cargos         (no GET /api/jrv/{id}/cargos)
  POST   /api/v1/observer/actas/{id}/imagen          (hoy 501)
  ```

  **Por qué ahora y no al final**: T-FE-04 … T-FE-07 se van a escribir leyendo estos
  documentos. Cada ruta mal escrita es una tarde de depuración en el frontend.

- [ ] **C.2** — Arreglar la contradicción del propio archivo: se llama
  `COMPLETADO-T-BE-11.md` y su línea 3 dice «❌ NO IMPLEMENTADO — Falta ObserverController».

  Poné el estado **con la medición al lado**: «✅ COMPLETADO — 11 tests en verde, N garantías
  verificadas por mutación», y anotá la excepción real: el endpoint de imagen devuelve `501`
  hasta que cierre `t-be-14-storage-acta`.

- [ ] **C.3** — RF-OBS-API-07. Comprobar que el Javadoc de `JrvConEstadoDto` deja claro que
  el resumen de cargos es **global por JRV**, no filtrado por el OBSERVER que pregunta.

  Es correcto que sea global —dos veedores en la misma mesa deben ver el mismo estado— y es
  justo la clase de cosa que alguien «arregla» a filtrado por usuario dentro de seis meses.

---

## Qué NO hacer en esta fase

- **No** implementar `POST /actas/{id}/imagen`: es `t-be-14-storage-acta`.
- **No** tocar `ActaService`: es T-BE-06 y T-BE-07.
- **No** optimizar `GET /jrvs` sin haber medido (B.1). Y si hay que optimizarlo, va en su
  propio change con test de equivalencia.
- **No** cambiar el resumen global de `GET /jrvs` a filtrado por usuario. Es una decisión
  tomada; documentala (C.3), no la revoques.
- **No** cambiar las rutas del código para que coincidan con los documentos. El código es la
  autoridad: se corrigen los documentos.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    ObserverControllerTest: 11 tests + los añadidos, 0 error
    total: no baja respecto de la línea base
```

Las cinco mutaciones de A anotadas con el nombre del test que cayó, y los dos números de B.1
(tiempo y consultas) escritos tal como salieron.
