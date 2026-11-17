# Spec — Coordinación de actas (delta)

Dominio: `acta-coordinacion`. Change: `2026-09-07-t-be-12-cierre-coordinacion`.
Autoridad: el código (`CoordinatorController`, `ActaService`), no el task doc.

---

## RF-COORD-01 — Sólo COORDINATOR y MASTER acceden al módulo

GIVEN un usuario autenticado con rol OBSERVER o ADMIN
WHEN llama cualquier endpoint bajo `/api/v1/coordinator/**`
THEN recibe `403 Forbidden`

GIVEN un usuario con rol COORDINATOR o MASTER
THEN accede a todos los endpoints salvo el de anulación

**Mutación**: quitar `@PreAuthorize("hasAnyRole('COORDINATOR','MASTER')")` de la clase.
Debe caer el test de acceso denegado a ADMIN.

---

## RF-COORD-02 — Sólo MASTER anula

GIVEN un usuario con rol COORDINATOR
WHEN llama `DELETE /api/v1/coordinator/actas/{actaId}`
THEN recibe `403 Forbidden`

GIVEN un usuario con rol MASTER y un acta en estado APROBADA
THEN el acta pasa a ANULADA y la respuesta es `204 No Content`

Es la regla de `CLAUDE.md`: sólo MASTER anula actas APROBADAS. El `@PreAuthorize` de método
sobreescribe el de clase — si desaparece, COORDINATOR hereda el permiso de la clase.

**Mutación**: quitar `@PreAuthorize("hasRole('MASTER')")` del método `anular`.
Debe caer el test de COORDINATOR → 403.

---

## RF-COORD-03 — Sólo se anulan actas APROBADAS

GIVEN un acta en estado ENVIADA, EN_DISPUTA o CORREGIDA
WHEN MASTER intenta anularla
THEN `ActaService.anular` lanza `ActaEstadoInvalidoException`

ANULADA es final e irreversible. Los datos **no se borran**: sólo cambia el estado y se
guarda `motivoAnulacion`.

**Mutación**: quitar el guard `if (acta.getEstado() != EstadoActa.APROBADA)` de
`ActaService.anular`. Debe caer el test de anular ENVIADA.

---

## RF-COORD-04 — Las transiciones de estado respetan la máquina

GIVEN un acta
THEN sólo se admiten estas transiciones:

| Desde | Acción | Hasta |
|---|---|---|
| ENVIADA · CORREGIDA | `disputar` | EN_DISPUTA |
| EN_DISPUTA | `corregir` | CORREGIDA |
| ENVIADA · CORREGIDA | `aprobar` | APROBADA |
| APROBADA | `anular` (MASTER) | ANULADA |

Cualquier otra combinación lanza `ActaEstadoInvalidoException`.

**Mutación**: en `ActaService.aprobar`, ampliar el guard para aceptar EN_DISPUTA.
Debe caer el test de aprobar un acta en disputa.

---

## RF-COORD-05 — El motivo es obligatorio al disputar y al anular

GIVEN una petición de disputa o anulación con `motivo` vacío o ausente
THEN la respuesta es `400 Bad Request` por `@Valid` + `@NotBlank`

El motivo viaja a la notificación del OBSERVER y al `AuditLog`. Sin motivo, el OBSERVER
recibe «tu acta fue marcada en disputa» sin saber qué corregir.

**Mutación**: quitar `@NotBlank` de `DisputaDto.motivo`. Debe caer el test de motivo vacío.

---

## RF-COORD-06 — Toda acción de coordinación queda auditada

GIVEN cualquier disputa, corrección, aprobación o anulación
THEN se registra en `audit_log` vía `AuditLogService` con el `usuarioId` del actor, la
acción, la entidad `acta`, el `actaId` y la IP del `HttpServletRequest`

Flujo obligatorio: `API → AuditLogService → JPA → PG`. Nunca escritura directa.

**Mutación**: quitar la llamada a `auditLogService.registrar` de `ActaService.aprobar`.
Debe caer un test que cuente filas de `audit_log`. **Si no existe ese test, no existe la
garantía** — hoy ningún test verifica la auditoría.

---

## RF-COORD-07 — El filtro por cantón: existe o no se promete

`documents/task-control/backend.md:192` promete
`GET /api/actas` con filtro por **cantón**. `ActaRepository.findWithFilters` acepta
`estado`, `jrvId`, `cargoId` y `registradaPorId` — **cantón no está**.

GIVEN el requisito de filtrar por cantón
THEN o bien `findWithFilters` acepta `cantonId` y filtra por
`a.jrv.parroquia.canton.id`, con su test
OR la promesa desaparece de `backend.md`

Las dos cosas a la vez es lo que hay hoy: una capacidad documentada que no existe.

---

## RNF-COORD-01 — Los 11 tests existentes se ejecutan de verdad

GIVEN `CoordinatorControllerTest`
WHEN corre la suite
THEN los 11 tests terminan en PASS, no en ERROR

Línea base 2026-09-07: **11 tests / 11 ERROR** por fallo de `ApplicationContext`.
Un test en ERROR no prueba nada: ni siquiera llegó a ejecutar su aserción.
