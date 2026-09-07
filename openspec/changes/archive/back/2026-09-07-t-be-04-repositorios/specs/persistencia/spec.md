# Spec — Persistencia (retrospectivo)

Dominio: `persistencia`. Change: `2026-09-07-t-be-04-repositorios`.

---

## RF-REP-01 — `findActaActivaConLock` bloquea de verdad

GIVEN dos transacciones concurrentes que buscan el acta activa de la misma JRV y cargo
WHEN ambas ejecutan `findActaActivaConLock`
THEN la segunda espera (o falla) hasta que la primera termine
AND no se crean dos actas para el par

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Acta a WHERE a.jrv = :jrv AND a.cargo = :cargo AND a.estado != 'ANULADA'")
Optional<Acta> findActaActivaConLock(@Param("jrv") Jrv jrv, @Param("cargo") Cargo cargo);
```

**Lo que hay que comprobar, no asumir**: que el método se llama **dentro** de una
transacción. `ActaService.registrar` está anotado `@Transactional`, así que en principio sí —
pero un `@Lock` invocado fuera de transacción no bloquea nada y no da ningún error.

**Mutación**: sustituir la llamada por `findActaActiva` (la gemela sin `@Lock`).
Debe caer el test de concurrencia. Si no cae, el test es secuencial y la garantía de unicidad
—la que impide dos resultados oficiales para la misma mesa— no está probada.

---

## RF-REP-02 — «Acta activa» excluye ANULADA

GIVEN un acta ANULADA para una JRV y cargo
WHEN se busca el acta activa de ese par
THEN no se encuentra, y registrar una nueva se permite

Es lo que hace posible rehacer un acta después de anularla. Sin esta cláusula, un error
irreversible dejaría esa mesa sin poder registrarse nunca más.

**Mutación**: quitar `AND a.estado != 'ANULADA'` de las dos queries.
Debe caer el test de re-registro tras anulación.

---

## RF-REP-03 — Los filtros opcionales de `findWithFilters` son independientes

GIVEN `findWithFilters(estado, jrvId, cargoId, registradaPorId)`
THEN cada parámetro `null` significa «no filtres por esto»
AND combinarlos aplica todas las condiciones no nulas

```sql
WHERE (:estado IS NULL OR a.estado = :estado)
  AND (:jrvId IS NULL OR a.jrv.id = :jrvId)
  AND (:cargoId IS NULL OR a.cargo.id = :cargoId)
  AND (:registradaPorId IS NULL OR a.registradaPor.id = :registradaPorId)
```

**Escenarios que hay que probar, uno por parámetro más uno combinado.** Un filtro que se
ignora en silencio devuelve datos de más — y en el listado de coordinación, eso es mostrar
actas de un OBSERVER cuando se pidieron las de otro.

**Mutación**: quitar la cláusula de `registradaPorId`.
Debe caer el test de filtrado por OBSERVER.

---

## RF-REP-04 — `findWithFilters` no filtra por cantón, y hay quien lo espera

GIVEN `documents/task-control/backend.md:192`, que promete filtrar el listado por cantón
THEN esa capacidad **no existe** en `findWithFilters`

Queda registrada aquí como parte del contrato incumplido del repositorio. La decisión
—implementarlo o retirar la promesa— se toma en `t-be-12-cierre-coordinacion`, tarea C.2.

---

## RF-REP-05 — `findWithFilters` ordena de forma estable

GIVEN dos actas registradas en el mismo instante
WHEN se listan
THEN el orden entre ellas es determinista

Hoy el `ORDER BY` es sólo por fecha descendente. Con dos actas del mismo instante —posible en
una carga masiva—, el orden lo decide el motor y puede cambiar entre llamadas. Una lista
paginada que reordena entre páginas repite y omite filas.

**No es urgente sin paginación**, pero queda anotado: el día que T-BE-13 o el frontend
paginen, esto se convierte en un defecto real.

---

## RF-REP-06 — Los repositorios muertos no sobreviven

GIVEN `RefreshTokenRepository`
THEN o lo usa algún flujo vivo, o se borra junto con su entidad
(ver `t-be-01-entidades-base`, RF-USR-05)

GIVEN `ObserverJrvAsignacionRepository`, marcado `@Deprecated` tras decidirse que el OBSERVER
elige JRV libremente
THEN queda documentado por qué sigue existiendo, o se borra

Un repositorio deprecado que nadie borra acaba usándose por alguien que no leyó la anotación.
`ActaService` conserva la dependencia inyectada (`asignacionRepository`) sin usarla: una
dependencia muerta en el constructor del servicio más crítico.

---

## RNF-REP-01 — Las queries de agregación excluyen ANULADA

GIVEN cualquier query nueva que cuente o sume actas
THEN excluye las ANULADA

Es la regla de `CLAUDE.md` («Actas ANULADA se excluyen de estadísticas del dashboard»)
aplicada al nivel donde de verdad se puede olvidar. Cada query de T-BE-13 lleva su propio
test de exclusión precisamente porque esta regla se repite y basta con omitirla una vez.
