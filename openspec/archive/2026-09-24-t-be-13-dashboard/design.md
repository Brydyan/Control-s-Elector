# Design — T-BE-13 · Dashboard

---

## D1 — La exclusión de ANULADA vive en la query, no en el servicio

**Decisión**: cada query de agregación lleva su `WHERE ... estado != 'ANULADA'` (o
`countByEstado` sobre estados concretos, que nunca incluye ANULADA).

**Alternativa descartada — traer todo y filtrar en Java.** Funciona con 2700 actas y deja de
funcionar sin avisar cuando alguien añade un endpoint nuevo y olvida el `.filter(...)`. El
filtro en la query es más difícil de omitir accidentalmente: si falta, el test de exclusión
falla en ese endpoint concreto.

**Alternativa descartada — una vista SQL `acta_activa` que ya excluya ANULADA.** Es la opción
más robusta —la regla queda en un solo sitio— pero introduce un objeto de base de datos que
Liquibase tiene que versionar y que Hibernate tiene que mapear, justo en el change donde
estamos reparando el orden de las migraciones. Se reevalúa cuando el esquema esté estable.

**Consecuencia aceptada**: la regla queda repetida en cada query, así que cada query necesita
su propio test de exclusión. Están enumerados en `tasks.md`, uno por endpoint.

---

## D2 — `ADMIN` no puede escribir: se garantiza por forma, no por guard

**Decisión**: el `DashboardController` expone **únicamente** `@GetMapping`. No hay un
`@PreAuthorize` que impida a ADMIN escribir, porque no hay nada que escribir.

**Alternativa descartada — exponer los endpoints y proteger la escritura con guards de rol.**
Un guard se puede quitar en un refactor y nadie lo nota hasta que ADMIN aprueba un acta. Un
método que no existe no se puede llamar. La regla «ADMIN nunca escribe» de `CLAUDE.md` se
sostiene mejor por ausencia que por permiso denegado.

**Lo que hay que vigilar**: el día que alguien añada un `@PostMapping` a este controlador,
esta decisión deja de protegerlo. Queda escrito en el propio archivo.

---

## D3 — `/novedades` entrega sólo actas, no evidencias

**Decisión**: `NovedadDto` se diseña con un campo `tipo` que hoy vale siempre
`"ACTA_EN_DISPUTA"`, y el endpoint devuelve únicamente actas EN_DISPUTA.

**Alternativa descartada — esperar a T-BE-16 y entregar el endpoint completo.** T-BE-16 está
bloqueada por una decisión que no es técnica: el enum `TipoObservacion` está marcado
«TBD — valores a definir por el usuario». Acoplar un endpoint de dashboard a una decisión de
producto pendiente bloquea las tres vistas que sí se pueden entregar.

**Alternativa descartada — inventar los valores del enum para desbloquear.** Los placeholders
del task doc (`FOTO_BORROSA`, `DATOS_NO_COINCIDEN`…) son ejemplos pedagógicos, no un set
acordado. Un enum se persiste como texto en la base: inventarlo hoy es una migración de datos
mañana.

El campo `tipo` existe desde el principio precisamente para que añadir
`"EVIDENCIA_CON_OBS"` después sea aditivo y no rompa al frontend.

---

## D4 — El tiempo transcurrido se calcula en UTC, no en la zona del runtime

**Decisión**: `minutosTranscurridos` se calcula con `Instant.now()` contra el instante
almacenado, sin pasar por `ZoneId.systemDefault()`.

**Alternativa descartada — el `ZoneId.systemDefault()` que propone el task doc
(`T-BE-13.md:374-377`).** Es el patrón §4.5 del playbook en estado puro: un valor que forma
parte del producto —cuánto lleva un acta sin resolver— no puede depender de la zona horaria
de la máquina que lo calcula. Railway corre en UTC, la máquina del desarrollador en
`America/Guayaquil` (UTC−5). El mismo acta mostraría urgencias distintas según quién
consulta, y en local el error se cancela solo, así que nadie lo ve.

**Cómo se verifica**: correr el test con `TZ=UTC` y con `TZ=America/Guayaquil` y comprobar
que da el mismo número. Está en `tasks.md`.

---

## D5 — Sin paginación, con el número medido

**Decisión**: los endpoints devuelven listas completas.

**Motivo, con la cuenta delante**: `CLAUDE.md` estima ~450 JRVs (200 + 150 + 100) y 6 cargos
como techo → ~2700 actas. `/resultados` agrupa por candidato, no por acta, así que devuelve
decenas de filas. `/progreso` devuelve 3 filas, una por cantón.

**Alternativa descartada — paginar desde el principio.** Complica el contrato del frontend
para un problema que los números dicen que no existe. Si la medición real desmiente la
estimación, se abre un change de paginación **con el número medido delante**, no con una
sospecha.

**La condición que lo invalida**: si `/novedades` o `/resumen` superan ~1000 filas en
producción. Queda anotado como lo que hay que mirar el día de la elección.
