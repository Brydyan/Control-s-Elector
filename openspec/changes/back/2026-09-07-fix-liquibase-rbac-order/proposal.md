# Proposal — Reparar el orden de migraciones RBAC y la compuerta de CI

Fecha: 2026-09-07 · Rama base: `develop` (`108819d`) · Arquitecto: agente principal

---

## Qué se construye

Dos cosas que son la misma cosa: **que la suite de tests pueda correr, y que alguien la corra.**

1. El esquema Liquibase se aplica sin error sobre una base de datos limpia.
2. La compuerta de CI ejecuta los tests en vez de saltárselos.

## Por qué

Medido el 2026-09-07 sobre `develop`, commit `108819d`:

```
./mvnw -B test    19 suites / 93 tests / 78 ERROR / 1 skip
```

Los 78 fallan todos por lo mismo: el `ApplicationContext` no levanta porque Liquibase aborta.

```
Migration failed for changeset database/changelog/002-rbac-seed.sql::002-02-rol-permiso
Caused by: liquibase.exception.DatabaseException: ERROR: relation "rol" does not exist
```

Los 14 tests que pasan son los que no necesitan contexto de Spring (`JwtServiceTest`,
`ConfiguracionServiceTest`, `UuidV7GeneratorTest`, dos de DTO). Es decir: **hoy no hay
ni un solo test de integración ejecutándose.** `CoordinatorControllerTest` y
`ObserverControllerTest` —los 22 tests que cubren el ciclo de vida del acta y el RBAC—
están en error, no en verde.

Nadie lo notó porque **la compuerta de merge no los corre**. `.github/workflows/ci.yml`:

```yaml
- name: Build Backend (skip tests for now)
  run: ./mvnw clean package -DskipTests
```

Y porque en la base de datos de desarrollo, ya migrada, el defecto es invisible: sólo aparece
contra un contenedor limpio (Testcontainers). Es el patrón §4.5 del playbook —comportamiento
que depende de dónde corre— combinado con el §4.6 —una compuerta que promete una garantía
que no tiene.

### La causa raíz, con archivo y línea

`001-initial-schema.sql` fue reescrito en su sitio (commits `3009a15`, `d97c8c4`, `14d5021`)
para añadir `public_id` y `rol_id`. `005-fks.sql` no se actualizó y sigue escrito como la
migración desde el esquema **anterior**. Quedaron tres incoherencias:

| # | Dónde | Qué pasa en BD limpia |
|---|---|---|
| 1 | `002-rbac-seed.sql:47` inserta en `rol_permiso(public_id, rol_id, permiso_id)` haciendo `FROM rol r` | La tabla `rol` se crea en `005-fks.sql:25`, tres changelogs después. **Aborta aquí** |
| 2 | `005-fks.sql:73` hace `ALTER TABLE rol_permiso ADD COLUMN rol_id BIGINT` | `001-initial-schema.sql:54` ya crea `rol_permiso.rol_id BIGINT NOT NULL`. Añadir una columna existente falla |
| 3 | `005-fks.sql:79` y `:93` leen y borran `rol_permiso.rol` (varchar) | Esa columna no existe en el `001` actual |

El fallo #1 es el que se ve, porque aborta antes. Los #2 y #3 aparecen apenas se arregle el
primero — por eso este change los trata juntos y no de a uno.

Para `usuario` **no hay incoherencia**: `001:24` sí crea `rol VARCHAR(32) NOT NULL` y los
changesets `005-03` … `005-06` lo migran correctamente. Se deja como está.

## Alcance

- `backend/electoral-api/src/main/resources/database/changelog/` — orden y contenido de los
  changesets de RBAC.
- `.github/workflows/ci.yml` — la compuerta corre los tests.
- Un test que demuestre que el esquema se aplica limpio, para que el defecto no vuelva.

## Fuera de alcance

- **No** se tocan los changesets `006` … `009` ni el esquema electoral. El defecto está en
  el bloque RBAC (`001`/`002`/`005`).
- **No** se arregla ningún test que falle por una razón propia. Primero el contexto levanta;
  lo que quede rojo después se diagnostica aparte, con su número medido.
- **No** se implementa T-BE-13 ni ninguna funcionalidad nueva. Este change existe para que
  las siguientes se puedan verificar.
- **No** se toca `frontend/`.
- **No** se habilita el job de frontend en CI: hoy sólo hay 2 specs de scaffold, y una
  compuerta que corre tests decorativos es peor que ninguna (§4.6).

## Criterio de cierre

1. `./mvnw -B test` contra contenedor limpio: **0 errores de `ApplicationContext`**.
2. El total de tests **no baja** de 93. Si baja, algo se dejó de ejecutar.
3. CI ejecuta los tests y **falla** si un test falla — demostrado rompiendo un test a
   propósito y viendo la compuerta en rojo.
4. Aplicar el changelog completo dos veces seguidas sobre la misma BD no da error
   (idempotencia).

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Las BD ya migradas (dev, staging) tienen checksums de `002-02` y `005-07`…`005-10`. Editarlos en su sitio provoca *checksum mismatch* | Decisión D2 en `design.md`. Hay que confirmar con el usuario si las BD existentes se recrean o se conservan |
| Al levantar el contexto aparecen fallos reales hasta ahora ocultos por el error de arranque | Es el resultado esperado, no un problema del change. Se miden y se reportan; no se arreglan aquí |
| El job de CI con tests tarda: Testcontainers levanta Postgres por contexto | Medir el tiempo real antes de optimizar. Particionar, nunca recortar (§5 del playbook) |
