# Spec — Migraciones de esquema (delta)

Dominio: `migraciones`. Change: `2026-09-07-fix-liquibase-rbac-order`.

---

## RF-MIG-01 — El changelog se aplica completo sobre una base de datos vacía

GIVEN una base de datos PostgreSQL 16 recién creada, sin tabla `databasechangelog`
WHEN se aplica `classpath:database/db.changelog-master.yaml` completo
THEN todos los changesets terminan en estado `EXECUTED` o `MARK_RAN`
AND ningún changeset queda en `FAILED`
AND el arranque de la aplicación no lanza `MigrationFailedException`

**Escenario que hoy falla** — es el defecto:

GIVEN una base de datos vacía
WHEN Liquibase llega a `002-rbac-seed.sql::002-02-rol-permiso`
THEN hoy aborta con `ERROR: relation "rol" does not exist`
AND tras este change, no aborta

---

## RF-MIG-02 — Ningún changeset referencia objetos creados después de él

GIVEN el orden de `include` declarado en `db.changelog-master.yaml`
WHEN un changeset del changelog N referencia una tabla o columna
THEN esa tabla o columna fue creada por un changeset del changelog M con M ≤ N

Corolario operativo: si un changeset necesita algo de un changelog posterior, el changeset
se mueve hacia adelante. **No** se reordenan los `include` — el orden numérico es la
garantía que lee el próximo desarrollador.

---

## RF-MIG-03 — La aplicación repetida del changelog es idempotente

GIVEN una base de datos con el changelog ya aplicado por completo
WHEN se aplica el changelog una segunda vez
THEN no se produce ningún error
AND no se duplican filas en `permiso`, `rol` ni `rol_permiso`

---

## RF-MIG-04 — El seed de RBAC produce la matriz de permisos completa

GIVEN el changelog aplicado sobre una base de datos vacía
THEN existen exactamente 4 filas en `rol`: MASTER, COORDINATOR, ADMIN, OBSERVER
AND existen 13 filas en `permiso`
AND cada fila de `rol_permiso` tiene `rol_id` no nulo y referencia una fila real de `rol`
AND MASTER tiene los 13 permisos
AND ADMIN no tiene ningún permiso cuya acción sea `upload`, `create`, `update`, `delete`,
    `aprobar`, `rechazar` ni `revoke`

El último es la traducción a datos de la regla de negocio «`ADMIN` nunca escribe»
(`CLAUDE.md`, Roles). Si el seed le concediera un permiso de escritura, el guard de la capa
API sería la única defensa.

---

## RF-MIG-05 — El contexto de Spring levanta en los tests de integración

GIVEN un test anotado con `@SpringBootTest` o `@DataJpaTest` y un contenedor Postgres limpio
WHEN se ejecuta la suite
THEN el `ApplicationContext` se carga sin `BeanCreationException` en `liquibase`
AND el número de tests en estado ERROR por fallo de contexto es **0**

Línea base medida el 2026-09-07: 78 de 93 tests en ERROR por esta causa.

---

## RNF-MIG-01 — La compuerta de CI ejecuta la suite y falla cuando un test falla

GIVEN un pull request contra `main` o `develop` que toca `backend/**`
WHEN corre el workflow `CI`
THEN el job ejecuta los tests del backend, no sólo el empaquetado
AND si un test falla, el job termina en rojo

**Verificación de esta garantía**: se rompe un test a propósito y se comprueba que el job
falla. Una compuerta que nunca se vio fallar no es una compuerta (§4.6 del playbook).
