# Spec — Registro de acta (retrospectivo)

Dominio: `acta-registro`. Change: `2026-09-07-t-be-06-acta-registro-locking`.

---

## RF-REG-01 — Una sola acta activa por `jrv_id + cargo_id`

GIVEN dos peticiones simultáneas de registro para la misma JRV y el mismo cargo
THEN exactamente una crea el acta
AND la otra recibe `JrvCargoYaRegistradoException`

GIVEN un acta ANULADA para ese par
THEN registrar una nueva se permite

La garantía descansa en `findActaActivaConLock` (`@Lock(PESSIMISTIC_WRITE)`) llamado dentro
de la transacción de `registrar` (`@Transactional`).

**Mutación**: sustituir la llamada por `findActaActiva` (sin `@Lock`).
Debe caer el test de concurrencia. Un test secuencial pasa igual sin lock y no distingue una
cosa de la otra.

---

## RF-REG-02 — El invariante de votos se cumple o el acta no entra

GIVEN un acta donde `SUM(detalles.votos) + votosBlancos + votosNulos ≠ totalSufragantes`
THEN se lanza `ActaInvalidaException`
AND no queda ninguna fila en `acta` ni en `acta_detalle`

El mensaje detalla los cuatro sumandos y el total esperado, para que el OBSERVER pueda
corregir sin adivinar.

**Mutación**: invertir el `!=` de la comprobación.
Debe caer el test de suma inválida.

**Escenario que hay que incluir**: cero votos en todo. `0 + 0 + 0 = 0` cuadra con
`totalSufragantes = 0`. ¿Es un acta válida —una mesa que no se instaló— o un error? Hoy se
acepta. Decidirlo y documentarlo.

---

## RF-REG-03 — El acta nace en estado ENVIADA

GIVEN un registro correcto
THEN el acta se crea con estado ENVIADA y `registradaPor` = el OBSERVER autenticado

El estado BORRADOR existe en el enum y **ningún camino lo produce**. O el flujo de borrador
se implementa, o el valor sobra: un estado inalcanzable en una máquina de estados es una
promesa de comportamiento que no existe.

---

## RF-REG-04 — El registro queda auditado

GIVEN un registro correcto
THEN queda una fila `REGISTRO_ACTA` en `audit_log` con JRV, cargo y total de sufragantes

**Mutación**: quitar la llamada a `auditLogService.registrar`.
Debe caer el test que cuenta filas de `audit_log`.

---

## RF-REG-05 — El control de acceso al registro es el que se dice que es

GIVEN la decisión arquitectónica de 2026-07-11 (el OBSERVER elige JRV libremente, sin
pre-asignación)
THEN el control de acceso vigente es el permiso `acta:upload` del JWT **y** RLS en PostgreSQL

`ActaService.registrar:77-82` lo afirma por comentario. **Hay que comprobar las dos mitades:**

- **Permiso JWT**: `ObserverController.registrarActa` lleva
  `@PreAuthorize("@authz.has('acta:upload')")`. Verificable con un test de rol.
  **Mutación**: quitar la anotación → debe caer el test de COORDINATOR o ADMIN → 403.

- **RLS**: hoy **no está demostrada**. `RlsInterceptor` no tiene ningún test, ejecuta
  `SET LOCAL app.current_user_id` desde un filtro de servlet —fuera de la transacción que
  después abre el repositorio, y `SET LOCAL` sólo vive dentro de su transacción— y captura
  cualquier excepción dejando pasar la petición.

Si RLS no actúa, el comentario documenta una defensa inexistente y el control de acceso al
registro de actas es sólo el permiso del JWT. Eso puede ser aceptable — pero tiene que ser
una decisión, no un descubrimiento. La medición está en `t-be-15-tests-integracion`,
RF-TEST-05.

---

## RF-REG-06 — Los candidatos de un acta pertenecen a su cargo

GIVEN un acta del cargo X
THEN todos los candidatos de sus detalles pertenecen al cargo X

**Hoy no se comprueba.** `registrar` resuelve cada `candidatoId` con `findById` y lo asocia
sin mirar su cargo.

**Reproducción del defecto**: registrar un acta de Alcalde con los votos de un candidato a
Prefecto. La suma cuadra, la validación pasa, el acta se crea, y
`dashboard/resultados` —que agrupa por `candidato.cargo`— atribuye esos votos al cargo de
Prefecto. Ningún error, ningún log, un resultado mal atribuido.

Es el hueco §4.1: `ActaService` se prueba con sus DTOs y el dashboard con sus datos, y nadie
prueba que lo que sale de uno sea válido para el otro.

---

## RNF-REG-01 — La dependencia muerta sale del constructor

GIVEN `ActaService`
THEN no recibe por constructor repositorios que no usa

`ObserverJrvAsignacionRepository` sigue inyectado (`asignacionRepository`) y ya no se usa
desde que se quitó la comprobación de pre-asignación. Una dependencia muerta en el servicio
más crítico invita a que alguien la vuelva a usar sin conocer la decisión que la retiró.

Ver `t-be-04-repositorios`, tarea B.2.
