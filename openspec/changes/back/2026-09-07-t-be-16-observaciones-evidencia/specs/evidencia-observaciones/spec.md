# Spec — Observaciones en evidencia (delta)

Dominio: `evidencia-observaciones`. Change: `2026-09-07-t-be-16-observaciones-evidencia`.

Los requisitos de abajo **no dependen** del set de valores del enum. Se pueden escribir y
revisar hoy; sólo su ejecución espera la decisión de `design.md` D0.

---

## RF-OBS-00 — El set de categorías está decidido antes de escribir código

GIVEN el enum `TipoObservacion`
THEN sus valores son los que definió el usuario, no los placeholders del task doc

`T-BE-16.md:80-86` propone `FOTO_BORROSA`, `DATOS_NO_COINCIDEN`, `FIRMA_FALTANTE`,
`NUMERO_INCORRECTO`, `OTRA` como ejemplos sacados de los mockups. **No son un set acordado.**

Un enum se persiste como texto: elegirlo mal es una migración de datos sobre evidencias ya
clasificadas, y el proceso electoral ocurre una sola vez.

**Este requisito bloquea todos los demás.**

---

## RF-OBS-01 — Sólo el OBSERVER dueño marca observaciones

GIVEN una evidencia subida por el OBSERVER A
WHEN el OBSERVER B llama `PATCH /api/v1/evidencias/{id}/observaciones`
THEN recibe `403 Forbidden`
AND las observaciones de la evidencia no cambian

GIVEN el OBSERVER A sobre su propia evidencia
THEN la operación se acepta

`hasRole('OBSERVER')` **no basta**: autoriza a cualquier OBSERVER, incluido el que no subió
esa foto. La comprobación es de autoría, comparando el `usuario_id` de la evidencia con el
del llamante — igual que `ActaService.corregir` compara `registradaPor`.

**Mutación**: quitar la comprobación de autoría dejando sólo el `@PreAuthorize` de rol.
Debe caer el test de OBSERVER ajeno → 403.

---

## RF-OBS-02 — Máximo 2 categorías

GIVEN una petición con 3 o más categorías
THEN la respuesta es `400 Bad Request` y no se persiste ninguna

GIVEN una petición con 0, 1 o 2
THEN se acepta

GIVEN una petición con la misma categoría repetida dos veces
THEN se rechaza, o se deduplica a una — pero no se guardan dos filas iguales

El límite existe para que la clasificación sirva: quien marca todas las categorías no ha
priorizado nada.

**Mutación**: subir el límite a 5 en el servicio. Debe caer el test de 3 categorías → 400.

---

## RF-OBS-03 — La validación está en el servicio, no sólo en el setter

GIVEN una escritura que no pasa por el setter de la entidad
WHEN intenta persistir 3 observaciones
THEN falla igualmente

Un setter protege el camino que pasa por el setter. No protege un `saveAll`, ni una
migración, ni un endpoint futuro. Ver `design.md` D2.

**Mutación**: dejar la validación **sólo** en el setter, quitándola del servicio.
Debe caer un test que persista saltándose el setter. Si ese test no existe, la garantía es
más débil de lo que el spec afirma, y hay que decirlo.

---

## RF-OBS-04 — Marcar observaciones queda auditado

GIVEN cualquier cambio de observaciones
THEN queda una fila en `audit_log` con el usuario, la acción, la entidad `evidencia`, su id
y la IP

Es una afirmación de una persona sobre la calidad de una prueba, y ordena la cola de
revisión. Quién lo dijo y cuándo tiene que constar.

**Mutación**: quitar la llamada a `AuditLogService`. Debe caer el test que cuenta filas de
`audit_log`.

**Nota**: `EvidenciaService` hoy no audita **ninguna** operación. Ese hueco lo repara
`t-be-14-storage-acta`.

---

## RF-OBS-05 — Una evidencia sin observaciones es el caso normal

GIVEN una evidencia recién subida
THEN su lista de observaciones está vacía, no es `null`
AND el endpoint de lectura la devuelve como lista vacía

GIVEN una evidencia con observaciones
WHEN se envía una lista vacía
THEN se borran las anteriores y la operación se acepta

Marcar observaciones es un paso **opcional** del flujo de subida. La mayoría de las
evidencias no tendrán ninguna, y ése es el camino que más se recorre.

---

## RF-OBS-06 — Todos los roles de revisión pueden leerlas

GIVEN una evidencia con observaciones
THEN COORDINATOR, MASTER y ADMIN pueden leerlas
AND ADMIN **no** puede escribirlas

`ADMIN` nunca escribe (`CLAUDE.md`, Roles).

**Mutación**: añadir ADMIN a los roles autorizados del `PATCH`.
Debe caer el test de ADMIN → 403.
