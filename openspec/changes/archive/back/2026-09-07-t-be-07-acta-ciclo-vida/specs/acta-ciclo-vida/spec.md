# Spec — Ciclo de vida del acta (retrospectivo)

Dominio: `acta-ciclo-vida`. Change: `2026-09-07-t-be-07-acta-ciclo-vida`.

---

## RF-CIC-01 — Sólo se disputan actas ENVIADA o CORREGIDA

GIVEN un acta en ENVIADA o CORREGIDA
WHEN se marca en disputa con motivo
THEN pasa a EN_DISPUTA

GIVEN un acta en EN_DISPUTA, APROBADA o ANULADA
THEN se lanza `ActaEstadoInvalidoException`

**Mutación**: quitar el guard de estado de `marcarEnDisputa`.
Debe caer el test de disputar un acta ya aprobada.

---

## RF-CIC-02 — Sólo se corrigen actas EN_DISPUTA

GIVEN un acta EN_DISPUTA
WHEN se corrige con datos válidos
THEN pasa a CORREGIDA

GIVEN un acta en cualquier otro estado
THEN se lanza `ActaEstadoInvalidoException`

**Mutación**: quitar el guard `if (acta.getEstado() != EstadoActa.EN_DISPUTA)`.
Debe caer el test de corregir un acta APROBADA.

---

## RF-CIC-03 — Un OBSERVER sólo corrige sus propias actas; un COORDINATOR, cualquiera

GIVEN un acta EN_DISPUTA registrada por el OBSERVER A
WHEN el OBSERVER B intenta corregirla
THEN se lanza `AccesoNoPermitidoException`

WHEN un COORDINATOR la corrige
THEN se acepta

La distinción vive en el parámetro `esCoordinator`:
`if (!esCoordinator && !acta.getRegistradaPor().getId().equals(usuarioId))`.
`ObserverController` pasa `false`; `CoordinatorController` pasa `true`.

**Mutación**: quitar la comprobación completa.
Debe caer el test de corrección ajena.

**Segunda mutación**: hacer que `ObserverController.corregirActa` pase `true`.
Debe caer el mismo test. Si no cae, el flag no está cubierto donde importa: un cambio de un
literal `false` a `true` convertiría a cualquier OBSERVER en corrector universal, sin ningún
error de compilación.

---

## RF-CIC-04 — La corrección revalida el invariante de votos

GIVEN una corrección donde `SUM(votos) + blancos + nulos ≠ totalSufragantes`
THEN se lanza `ActaInvalidaException` y el acta no cambia de estado

Es la misma regla que en `registrar`, escrita dos veces en el mismo archivo. **Las dos
necesitan su test**: es §4.2 del playbook — una regla aplicada en un sitio y en su vecino, y
basta con que una de las dos copias quede sin cubrir.

**Mutación**: invertir el `!=` **sólo en `corregir`**.
Debe caer un test específico de corrección inválida. Si sólo existe el de `registrar`, la
copia de `corregir` no está protegida.

---

## RF-CIC-05 — La corrección reemplaza los detalles, no los acumula

GIVEN un acta con 3 detalles
WHEN se corrige con 2 detalles
THEN quedan exactamente 2, no 5

`corregir` hace `actaDetalleRepository.deleteByActa(acta)` antes de insertar los nuevos.

**Mutación**: quitar el `deleteByActa`.
Debe caer un test que cuente detalles después de corregir. Sin ese test, una corrección
duplicaría los votos y la suma dejaría de cuadrar de forma invisible.

---

## RF-CIC-06 — Sólo se aprueban actas ENVIADA o CORREGIDA

GIVEN un acta ENVIADA o CORREGIDA
THEN aprobar la pasa a APROBADA

GIVEN un acta EN_DISPUTA
THEN aprobar lanza `ActaEstadoInvalidoException`

Aprobar un acta en disputa cerraría la disputa sin resolverla.

**Mutación**: ampliar el guard para aceptar EN_DISPUTA.
Debe caer el test de aprobar en disputa.

---

## RF-CIC-07 — Sólo MASTER anula, y sólo actas APROBADAS

GIVEN un acta APROBADA y un usuario MASTER
THEN anular la pasa a ANULADA, guardando `motivoAnulacion`, sin borrar detalles

GIVEN un acta en cualquier otro estado
THEN se lanza `ActaEstadoInvalidoException`

La restricción de rol vive en `CoordinatorController.anular`
(`@PreAuthorize("hasRole('MASTER')")`), no en el servicio. Ver
`t-be-12-cierre-coordinacion` RF-COORD-02.

**Mutación**: quitar el guard de estado de `ActaService.anular`.
Debe caer el test de anular un acta ENVIADA.

---

## RF-CIC-08 — Las notificaciones son coherentes con lo que se promete

GIVEN un acta que pasa a EN_DISPUTA
THEN el OBSERVER que la registró recibe una notificación con el motivo

GIVEN un acta que pasa a ANULADA
THEN el OBSERVER recibe una notificación con el motivo

GIVEN un acta que pasa a CORREGIDA o APROBADA
THEN **hoy no se genera ninguna**

`backend.md:117` promete «Notificación automática al cambiar estado», sin excepciones. El
código notifica dos de cuatro.

**A decidir por escrito**: o la regla es «se notifica lo que exige acción del OBSERVER»
—y entonces `backend.md` se corrige—, o faltan dos notificaciones. Las dos respuestas son
defendibles; el estado actual, en el que nadie lo ha decidido, no.

**Mutación**: quitar `notificacionService.notificar` de `marcarEnDisputa`.
Debe caer un test que cuente notificaciones tras disputar.

---

## RF-CIC-09 — El `audit_log` distingue quién corrigió

GIVEN una corrección
THEN la fila de `audit_log` permite saber si la hizo el OBSERVER dueño o un COORDINATOR

Hoy el detalle es `"Acta corregida por usuario " + usuarioId`, sin esa distinción. El
parámetro `esCoordinator` existe en el método y **no llega al registro**.

Cuando se impugne una corrección, la pregunta va a ser exactamente ésa: ¿la corrigió el
veedor que la registró, o la cambió un coordinador? Reconstruirlo después cruzando el id con
la tabla de roles funciona sólo si ese usuario no cambió de rol entretanto.
