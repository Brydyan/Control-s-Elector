# Spec — Modelo de acta (retrospectivo)

Dominio: `acta-modelo`. Change: `2026-09-07-t-be-03-entidades-actas`.

---

## RF-ACT-01 — Los estados del acta son exactamente seis

GIVEN el enum `EstadoActa`
THEN sus valores son BORRADOR, ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA, ANULADA
AND se persisten como texto (`@Enumerated(EnumType.STRING)`), no como ordinal

**Por qué el ordinal es peligroso**: guardado como número, insertar un estado nuevo en medio
del enum reinterpreta silenciosamente todas las filas existentes. Un acta APROBADA pasaría a
ser otra cosa sin que nada falle.

**Mutación**: cambiar a `EnumType.ORDINAL`. Debe caer un test que persista un acta y la lea
comprobando el valor en la columna.

---

## RF-ACT-02 — `ANULADA` es terminal

GIVEN un acta en estado ANULADA
THEN ninguna operación la saca de ese estado

Hoy se cumple **por ausencia**: ningún método de `ActaService` acepta ANULADA como estado de
partida. Funciona, pero no hay una regla explícita que lo defienda del día que alguien añada
una transición.

**Mutación**: ampliar el guard de `ActaService.corregir` para aceptar también ANULADA.
Debe caer un test que intente corregir un acta anulada.

Si no existe ese test, la terminalidad no está protegida: sólo está siendo cierta.

---

## RF-ACT-03 — Anular no destruye datos

GIVEN un acta APROBADA con N detalles de votos
WHEN MASTER la anula
THEN el estado pasa a ANULADA
AND se guarda `motivoAnulacion`
AND los N `ActaDetalle` **siguen existiendo**

`CLAUDE.md`: «Datos nunca se borran al anular — solo cambia el estado». Un acta anulada es
prueba de que hubo un problema; borrar sus votos borraría la evidencia de qué se corrigió.

**Mutación**: añadir un `actaDetalleRepository.deleteByActa(acta)` en `ActaService.anular`.
Debe caer el test que cuenta detalles después de anular.

---

## RF-ACT-04 — `ActaDetalle` guarda los votos, `Acta` no

GIVEN un acta
THEN sus votos por candidato viven en `acta_detalle`, una fila por candidato
AND `Acta` guarda sólo `totalSufragantes`, `votosBlancos` y `votosNulos`

`CLAUDE.md` lo dice explícitamente: «No hay `votos_candidato_a/b` en ACTA».

**Verificación estructural**: ningún campo de `Acta` contiene votos de un candidato concreto.

---

## RF-ACT-05 — La fecha de registro tiene un solo nombre

GIVEN la entidad `Acta`
THEN su campo de fecha de registro se llama de **una** forma, y los documentos usan esa

Estado medido el 2026-09-07 — tres nombres para el mismo dato:

| Fuente | Nombre |
|---|---|
| `ActaRepository.findWithFilters` (`ORDER BY`) | `a.fechaRegistro` |
| `documents/task-control/backend.md:46` | `registrada_en` |
| `documents/task-control/backendtask/T-BE-13.md:375` | `getRegistradaEn()` |

Dos de los tres están mal. Quien escriba el dashboard contra el nombre equivocado lo
descubrirá al compilar — o no, si acierta con otro getter que exista.

---

## RF-ACT-06 — Cada cambio de estado deja rastro en `audit_log`

GIVEN cualquier transición de estado
THEN existe una fila en `audit_log` con usuario, acción, entidad `acta`, id e IP

Flujo obligatorio: `API → AuditLogService → JPA → PG`. Nunca escritura directa.

**Mutación**: escribir en `AuditLogRepository` directamente desde un servicio, saltándose
`AuditLogService`. Ningún test lo detectaría hoy — la regla es de convención, no de código.
Anotarlo como limitación conocida, o proponer cómo hacerla verificable.

---

## RF-ACT-07 — Las notificaciones acompañan a los cambios que exigen acción

GIVEN un acta que pasa a EN_DISPUTA
THEN el OBSERVER que la registró recibe una notificación con el motivo

GIVEN un acta que pasa a ANULADA
THEN el OBSERVER recibe una notificación con el motivo

GIVEN un acta que pasa a APROBADA
THEN **hoy no se genera ninguna notificación**

`ActaService.aprobar` no llama a `notificacionService`, mientras que `marcarEnDisputa` y
`anular` sí. `backend.md:117` promete «Notificación automática al cambiar estado», sin
excepciones.

**La pregunta a responder**: ¿es deliberado (sólo se notifica lo que exige acción del
OBSERVER) o es un olvido? Las dos respuestas son defendibles; lo que no se sostiene es que
el documento prometa una cosa y el código haga otra sin que nadie lo haya decidido.

**Mutación**: quitar `notificacionService.notificar` de `marcarEnDisputa`.
Debe caer un test que cuente notificaciones tras disputar.

---

## RNF-ACT-01 — `Acta` expone `publicId`

GIVEN cualquier respuesta de la API que incluya un acta
THEN el identificador que viaja es el `publicId`

**Nota de estado**: `CoordinatorController` devuelve hoy `ResponseEntity<Acta>` —la entidad
JPA cruda, no un DTO—, mientras que `ObserverController` mapea a `ActaResponseDto`. Son dos
criterios en el mismo dominio (§4.2), y el de coordinación expone el modelo interno con sus
relaciones perezosas. Se trata en `t-be-12-cierre-coordinacion`.
