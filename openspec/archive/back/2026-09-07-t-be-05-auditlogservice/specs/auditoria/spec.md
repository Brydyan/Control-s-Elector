# Spec — Auditoría (retrospectivo)

Dominio: `auditoria`. Change: `2026-09-07-t-be-05-auditlogservice`.

---

## RF-AUD-01 — Toda acción que cambia datos queda registrada

GIVEN una acción que crea, modifica o elimina datos del proceso electoral
THEN queda una fila en `audit_log` con: usuario, acción, entidad, id de entidad, detalle,
IP y momento

Inventario medido el 2026-09-07:

| Acción | ¿Auditada? |
|---|---|
| Registrar acta | sí (`REGISTRO_ACTA`) |
| Marcar en disputa | sí (`ACTA_EN_DISPUTA`) |
| Corregir acta | sí (`ACTA_CORREGIDA`) |
| Aprobar acta | sí (`ACTA_APROBADA`) |
| Anular acta | sí (`ACTA_ANULADA`) |
| **Subir evidencia** | **no** |
| **Borrar evidencia** | **no** |
| **Cambiar estado de evidencia** | **no** |
| Login / logout | verificar |
| Configuración preelectoral (cargos, partidos, candidatos) | verificar |

Los tres huecos de evidencia se reparan en `t-be-14-storage-acta`. Borrar una evidencia
destruye la foto del acta física: es la operación que más necesita rastro y la que menos
tiene.

---

## RF-AUD-02 — La IP del cliente se registra

GIVEN cualquier acción auditada
THEN la fila guarda la IP extraída del `HttpServletRequest`

**Lo que hay que verificar, no asumir**: qué IP se guarda cuando la API corre detrás del
proxy de Railway. Si se lee `request.getRemoteAddr()` sin mirar `X-Forwarded-For`, **todas
las filas de producción tendrán la IP del proxy** y el campo no servirá para nada.

En local no se nota: no hay proxy delante, así que la IP es la real. Es §4.5 del playbook —
comportamiento que depende de dónde corre, con el lado permisivo en desarrollo.

**Comprobación**: enviar una petición con `X-Forwarded-For` y ver qué queda en la fila.

---

## RF-AUD-03 — La escritura pasa siempre por `AuditLogService`

GIVEN cualquier servicio del sistema
THEN escribe en `audit_log` únicamente a través de `AuditLogService`

`CLAUDE.md`: «Flujo siempre: `AuditLogService → JPA → PG`. Nunca directo».

**Hoy esto es una convención, no una garantía.** Nada impide inyectar `AuditLogRepository` y
escribir directo, y ningún test lo detectaría.

**Propuesta para hacerlo verificable**: un test de arquitectura que falle si alguna clase
distinta de `AuditLogService` referencia `AuditLogRepository`. Es la clase de compuerta que
sí detecta la **ausencia** de lo que falta, en lugar de comprobar la presencia de lo que hay.

---

## RF-AUD-04 — Un fallo de auditoría no pasa desapercibido

GIVEN una acción auditada donde la escritura del `audit_log` falla
THEN el fallo es visible: o la operación falla, o queda registrado en el log de aplicación

Lo que no puede ocurrir es que la acción se complete y la auditoría desaparezca en silencio.
Es el mismo defecto que `RlsInterceptor` tiene hoy con su `catch (Exception e) { warn }`: una
garantía que se desactiva sola sin que nadie se entere.

**Decisión a documentar**: ¿la auditoría es parte de la transacción de negocio —si falla,
falla todo— o es best-effort? Las dos son defendibles. Lo que no vale es que nadie lo haya
decidido y el comportamiento sea accidental.

---

## RF-AUD-05 — El detalle de la acción incluye lo que hace falta para reconstruirla

GIVEN una anulación de acta
THEN el detalle registrado incluye el motivo

GIVEN un registro de acta
THEN el detalle incluye JRV, cargo y total de sufragantes

Es lo que hoy hace `ActaService`. La prueba de que sirve es poder responder «¿qué pasó con el
acta de la JRV 47?» leyendo sólo `audit_log`.

---

## RNF-AUD-01 — Los tests de auditoría se aíslan entre sí

GIVEN dos tests que cuentan filas de `audit_log`
WHEN se ejecutan en cualquier orden
THEN dan el mismo resultado

Si el arnés no trunca `audit_log` entre tests, estos tests pasan o fallan según el orden. Ver
`t-be-15-tests-integracion`, RNF-TEST-02: la pregunta que hay que hacerle al arnés es **qué
no limpia**.
