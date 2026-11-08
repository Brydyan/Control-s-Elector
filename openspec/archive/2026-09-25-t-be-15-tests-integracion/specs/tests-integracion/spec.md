# Spec — Tests de integración (delta)

Dominio: `tests-integracion`. Change: `2026-09-07-t-be-15-tests-integracion`.

Cada requisito describe una **garantía del sistema** y el test que la protege. Un requisito
sin mutación ejecutada no está cumplido.

---

## RF-TEST-01 — Unicidad de acta por `jrv_id + cargo_id`, bajo concurrencia real

GIVEN dos peticiones simultáneas de registro para la misma JRV y el mismo cargo
WHEN ambas se ejecutan en hilos distintos, arrancados a la vez
THEN exactamente **una** crea el acta
AND la otra recibe `JrvCargoYaRegistradoException`
AND en la base queda **una sola** fila activa para ese par

GIVEN un acta ANULADA para ese par
WHEN se registra una nueva
THEN se permite: `findActaActivaConLock` excluye ANULADA

**Mutación**: cambiar `findActaActivaConLock` por `findActaActiva` (sin `@Lock`).
Debe caer este test. **Si no cae, el test es secuencial disfrazado de concurrente** y la
garantía de unicidad no está probada. Ver `design.md` D2.

---

## RF-TEST-02 — El invariante de votos llega hasta el HTTP

GIVEN un acta donde `SUM(detalles.votos) + votosBlancos + votosNulos ≠ totalSufragantes`
WHEN se envía a `POST /api/v1/observer/actas`
THEN la respuesta es un error de cliente y **no** se crea ninguna fila en `acta`
AND tampoco en `acta_detalle`

GIVEN un acta donde la suma cuadra
THEN la respuesta es `201 Created`

Se verifica el mismo invariante en `corregir`, no sólo en `registrar`: son dos caminos hacia
la misma regla, y hoy sólo uno tiene la validación probada.

**Mutación**: invertir el `!=` de la comprobación en `ActaService.registrar`.
Debe caer el test de suma inválida. Repetir en `corregir` — es §4.2: la misma regla en dos
sitios, y hay que comprobar que los dos la aplican.

---

## RF-TEST-03 — El ciclo de vida completo del acta

GIVEN un acta recién registrada
THEN su estado es ENVIADA

WHEN COORDINATOR la disputa con motivo → EN_DISPUTA
WHEN el OBSERVER que la registró la corrige → CORREGIDA
WHEN COORDINATOR la aprueba → APROBADA
WHEN MASTER la anula con motivo → ANULADA

AND en cada transición queda una fila en `audit_log`
AND la disputa y la anulación generan una `notificacion` para el OBSERVER
AND desde ANULADA no se admite ninguna transición

El test recorre el camino entero en una sola prueba. Probar cada transición por separado, con
el estado anterior fijado a mano, deja sin comprobar que la salida de un paso es entrada
válida del siguiente — que es exactamente el hueco del §4.1.

**Mutación**: permitir EN_DISPUTA en el guard de `ActaService.aprobar`.
Debe caer el test de ciclo completo.

---

## RF-TEST-04 — Un OBSERVER no accede a las actas de otro

GIVEN dos OBSERVERs, A y B, y un acta registrada por A
WHEN B consulta `GET /api/v1/observer/mis-actas`
THEN el acta de A **no** aparece

GIVEN un acta EN_DISPUTA registrada por A
WHEN B intenta corregirla
THEN recibe `403 Forbidden` (`ActaService.corregir` comprueba `registradaPor`)

**Mutación 1**: quitar la comprobación
`!esCoordinator && !acta.getRegistradaPor().getId().equals(usuarioId)` de
`ActaService.corregir`. Debe caer el test de corrección ajena.

**Mutación 2**: desactivar `RlsInterceptor`. Debe caer el test de aislamiento del listado.
**Si no cae, RLS no está actuando** — ver RF-TEST-05.

---

## RF-TEST-05 — RLS está realmente activa durante una petición

GIVEN una petición autenticada en curso
WHEN se consulta `SELECT current_setting('app.current_user_id', true)` en la misma
transacción que usan los repositorios
THEN devuelve el id interno del usuario, **no** `null`

Éste es el requisito que decide si la seguridad de segunda línea existe o es decorativa.
Hoy hay tres señales de que puede no estar actuando:

1. `RlsInterceptor` **no tiene ningún test** que lo cubra.
2. Ejecuta `SET LOCAL` desde un filtro de servlet, **fuera** de la transacción que abrirá
   después el repositorio. `SET LOCAL` sólo vive dentro de su transacción.
3. **Se traga cualquier excepción** y deja pasar la petición: si la variable no se establece,
   no hay error visible, hay ausencia silenciosa de RLS.

Mientras tanto, `ActaService.registrar:77-82` afirma por comentario que «RLS PostgreSQL +
permisos JWT son el control de acceso vigente» tras haberse quitado la comprobación de
pre-asignación de JRV.

Si este requisito falla, es **CRITICAL**: el comentario documenta una defensa que no existe.
No se arregla en este change — se reporta en `fixes-required.md` con su reproducción.

---

## RF-TEST-06 — El filtro por OBSERVER del listado global

GIVEN actas de dos OBSERVERs distintos
WHEN COORDINATOR llama `GET /api/v1/coordinator/actas?registradaPorId={idDeA}`
THEN sólo aparecen las de A

**Mutación**: quitar la cláusula `(:registradaPorId IS NULL OR ...)` de `findWithFilters`.
Debe caer este test.

---

## RNF-TEST-01 — La suite corre contra PostgreSQL, no contra H2

GIVEN cualquier test de integración de este change
THEN se ejecuta contra un contenedor PostgreSQL 16 con el changelog de Liquibase aplicado

H2 no tiene RLS, se comporta distinto con `SELECT FOR UPDATE`, y `create-drop` salta
Liquibase. Cuatro de los seis requisitos de arriba pasarían sin medir nada.
Ver `design.md` D1.

---

## RNF-TEST-02 — El arnés aísla los tests entre sí

GIVEN dos tests que cuentan filas de `audit_log` o `notificacion`
WHEN se ejecutan en cualquier orden
THEN dan el mismo resultado

La pregunta que hay que hacerle al arnés es **qué no limpia**. Si no trunca `audit_log` y
`notificacion`, los tests de auditoría pasan o fallan según el orden — y un test que depende
del orden es un test que no protege nada.
