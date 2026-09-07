# Design — T-BE-16 · Observaciones en evidencia

---

## D0 — La decisión que falta, y por qué no la tomo yo

**Pendiente del usuario**: los valores definitivos de `TipoObservacion`.

**Por qué no elijo placeholders para desbloquear.** Es tentador: el task doc ya propone cinco
valores y el código compilaría hoy mismo. Pero un enum de dominio no es un detalle de
implementación:

- se persiste como texto y viaja al frontend, así que cambiarlo después es una migración de
  datos sobre filas que ya clasificaron evidencias reales;
- las categorías las va a usar un veedor a las 3 de la mañana, con prisa, en un móvil. Si no
  son las palabras que esa persona usa, marcará «Otra» siempre y el campo no servirá para
  priorizar nada;
- el proceso electoral ocurre **una vez**. No hay una segunda vuelta para reclasificar.

**Alternativa descartada — usar una tabla `tipo_observacion` en vez de un enum**, para poder
añadir categorías sin tocar código y así no bloquear. Resuelve el problema equivocado: el
riesgo no es no poder añadir una categoría, es que el set inicial no se parezca a la realidad
del veedor. Una tabla editable sólo mueve la decisión al día de la elección, que es el peor
momento para tomarla.

Ver D2 para cuándo la tabla sí sería la opción correcta.

---

## D1 — Cómo se guardan las observaciones

**Decisión**: tabla junction `evidencia_observacion` (`evidencia_id`, `tipo`, `creado_en`),
no una columna `jsonb` en `evidencia`.

**Alternativa descartada — columna `jsonb` con la lista**, que es lo que propone el task doc
(`T-BE-16.md:94-96`). Tres problemas concretos:

1. **Consultar por categoría se vuelve incómodo.** `/novedades` va a querer «todas las
   evidencias con FOTO_BORROSA». Con `jsonb` eso es un operador de contención sin índice
   útil; con una junction es un `WHERE tipo = ?`.
2. **La validación de «máximo 2» queda sólo en el setter de Java.** Cualquier escritura que
   no pase por ese setter —una migración, un fix manual— mete tres sin que nada lo impida.
   Con una tabla se puede añadir la restricción en la base.
3. `@Column(columnDefinition = "jsonb")` ata la entidad a PostgreSQL. El propio task doc lo
   admite en su tabla de errores comunes y propone `@ElementCollection` como alternativa
   portátil — que es, en la práctica, la junction de esta decisión.

**Coste aceptado**: una tabla más y un `@OneToMany`. A cambio, «máximo 2» puede dejar de ser
una promesa de la capa de aplicación.

---

## D2 — El máximo de 2 se valida en el servicio, y además en la base

**Decisión**: la validación vive en `EvidenciaService`, y la base la respalda.

**Alternativa descartada — validarlo sólo en el setter de la entidad**, como propone
`T-BE-16.md:100-106`. Un setter protege el camino que pasa por el setter. No protege un
`saveAll`, ni una migración, ni un endpoint futuro que construya la entidad de otra forma. La
regla queda cierta «casi siempre», que es la peor clase de regla: pasa los tests y falla en
producción.

**Cómo se respalda en la base**: con la junction de D1, una restricción que impida más de dos
filas por `evidencia_id`. Si el motor lo pone difícil, un trigger; si eso se descarta, queda
**escrito en el `design.md` que la garantía es sólo de aplicación** — que es una situación
aceptable siempre que esté declarada y no escondida en un comentario optimista.

**Alternativa descartada — no poner límite y dejar que el frontend lo controle.** El límite
existe para que la clasificación sirva: si un veedor marca las cinco categorías, no ha
priorizado nada. Y una regla que sólo vive en el cliente no es una regla.

---

## D3 — Sólo el OBSERVER dueño escribe; el resto lee

**Decisión**: la comprobación de autoría se hace en el servicio, comparando el `usuario_id`
de la evidencia con el del llamante — no sólo con `@PreAuthorize("hasRole('OBSERVER'))`.

**Alternativa descartada — confiar en el `@PreAuthorize` de rol**, que es lo que el task doc
enseña (`T-BE-16.md:156`). `hasRole('OBSERVER')` autoriza a **cualquier** OBSERVER, incluido
uno que no subió esa foto. Es el mismo patrón que `ActaService.corregir` ya resuelve bien
comparando `registradaPor`: aquí hay que hacer lo mismo, y el task doc lo dice de pasada en
su ejemplo pero lo deja fuera de la anotación.

**Alternativa descartada — apoyarse en RLS para el aislamiento.** Hoy no está demostrado que
RLS esté actuando: `RlsInterceptor` no tiene tests, hace `SET LOCAL` fuera de la transacción
y se traga las excepciones (ver `t-be-15-tests-integracion`, RF-TEST-05). Apoyar un control de
acceso en una defensa no verificada es apoyarlo en nada.

---

## D4 — Marcar observaciones se audita

**Decisión**: la operación registra en `AuditLogService`.

Es una afirmación del OBSERVER sobre la calidad de una prueba, y va a decidir el orden en que
se revisan los casos. Quién dijo qué, y cuándo, tiene que constar.

**Nota**: `EvidenciaService` hoy **no audita nada** —ni upload ni delete—. Ese hueco lo repara
`t-be-14-storage-acta` (D5). Este change hereda el `AuditLogService` ya inyectado allí; si se
ejecuta antes, tiene que inyectarlo él.
