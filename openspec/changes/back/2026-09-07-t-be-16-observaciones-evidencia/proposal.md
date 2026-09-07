# Proposal — T-BE-16 · Observaciones en evidencia

Origen: `documents/task-control/backendtask/T-BE-16.md` · Área: back · Fecha: 2026-09-07

**Estado: BLOQUEADO por una decisión de producto, no por código.**

---

## Qué se construye

Que el OBSERVER, al terminar de subir la foto del acta, pueda marcar **1 o 2 categorías de
observación** (foto borrosa, datos que no coinciden…), para que COORDINATOR y ADMIN prioricen
qué revisar primero.

## Por qué está bloqueado

El task doc marca el punto exacto (`T-BE-16.md:67`):

> **⚠️ CATEGORÍAS TBD:** El usuario definirá el set específico antes de implementar.

Los valores que aparecen en el doc —`FOTO_BORROSA`, `DATOS_NO_COINCIDEN`, `FIRMA_FALTANTE`,
`NUMERO_INCORRECTO`, `OTRA`— son **placeholders pedagógicos** sacados de los mockups de
COORDINATOR, no un set acordado.

Esto no es una formalidad. Un enum se persiste como texto en la base y viaja al frontend:

- inventarlo hoy es una migración de datos mañana, sobre filas que ya clasificaron evidencias
  reales;
- si el set cambia después de la elección, las observaciones ya registradas quedan con
  categorías que ya no existen;
- la elección ocurre una vez. No hay una segunda pasada para reclasificar.

**La pregunta abierta**: ¿cuáles son las categorías definitivas de `TipoObservacion`?
Es la única que bloquea; todo lo demás del change está decidido.

## Alcance, cuando se desbloquee

- Enum `TipoObservacion` con los valores que decida el usuario.
- Persistencia de 0, 1 o 2 observaciones por evidencia.
- `PATCH /api/v1/evidencias/{id}/observaciones` — sólo el OBSERVER dueño.
- Validación de máximo 2, en el servicio y no sólo en el setter.
- Migración Liquibase.
- Tests con sus mutaciones.

## Fuera de alcance

- **No** se toca el flujo de subida existente. Marcar observaciones es un paso posterior y
  opcional: una evidencia sin observaciones es el caso normal.
- **No** se implementa la parte de `/novedades` que las expone. `T-BE-13` entrega ese endpoint
  sólo con actas EN_DISPUTA, con un campo `tipo` preparado para que añadirlas sea aditivo.
- **No** se toca el frontend (el paso extra de la UI es de T-FE).

## Lo que sí se puede hacer ahora, sin la decisión

Preparar el terreno y dejar la pregunta por escrito donde no se pierda. Eso es este change
hoy: `proposal.md`, `design.md` con las dos formas de modelarlo evaluadas, y `spec.md` con
los requisitos que **no** dependen del set de valores (autoría, máximo 2, auditoría, RLS).

`tasks.md` está escrito y **no se ejecuta** hasta que el enum esté decidido.

## Criterio de cierre

0. **El usuario ha definido las categorías.** Sin esto, ninguna de las siguientes aplica.
1. Un OBSERVER marca hasta 2 observaciones en **su** evidencia; en la de otro recibe `403`.
2. Enviar 3 categorías devuelve `400`, y la validación está en el servicio.
3. La operación queda en `audit_log`.
4. Cada garantía tiene su mutación ejecutada, con el nombre del test que cayó.

## Depende de

Una decisión del usuario (bloqueante), y `back/2026-09-07-fix-liquibase-rbac-order`.

Conviene además ir **después** de `t-be-14-storage-acta`: ese change repara la compensación
de S3 y la auditoría de `EvidenciaService`, que es el mismo servicio que aquí se extiende.
