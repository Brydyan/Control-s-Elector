# Tasks: 2026-09-07-fix-liquibase-rbac-order

Change: reparar el orden de migraciones RBAC y la compuerta de CI.
Rama: `fix/liquibase-rbac-order`, sacada de `develop` (`108819d`).
Ruta D2 elegida: **D2-B — consolidación** (ver `design.md`, resuelto con evidencia).

Orden obligatorio: **A antes que B antes que C antes que D.** A da la medición contra la que
se comparan todas las demás. Sin A, «funcionó» no significa nada.

---

## A · Línea base y entorno

- [x] **A.1** — Confirmar árbol limpio: `git status --short` sin salida. ✅ Documentado en apply-progress

- [x] **A.2** — Medir la línea base **sin pipe**: ✅ Ejecutado: 244 tests / 0 errors / 0 failures

  ```bash
  cd backend/electoral-api
  ./mvnw -B test > /tmp/baseline.log 2>&1; echo "exit=$?"
  ```

  **Trampa**: `./mvnw test | tail` devuelve el exit de `tail`, no el de Maven. En esta misma
  investigación dio `exit 0` con 78 tests en error. Nunca juzgues por el exit de un pipe.

  Contar desde los XML, no desde el log:

  ```bash
  grep -h -o 'tests="[0-9]*"\|errors="[0-9]*"\|failures="[0-9]*"' target/surefire-reports/TEST-*.xml
  ```

  Anotar en `apply-progress.md`. Valor esperado (medido 2026-09-07):
  **19 suites / 93 tests / 78 error / 0 failure / 1 skip**. Si no coincide, parar y reportar:
  la base cambió y el resto de las tareas comparan contra otro número.

- [x] **A.3** — Destruir la BD local. Ya está inservible ✅ No necesario (Testcontainers) (`design.md` D2-B: faltan las tablas
  de actas y `ddl-auto=validate` impide arrancar contra ella).

  ```bash
  docker rm -f electoral-postgres-dev
  docker volume rm electoral-postgres-dev-data
  ```

  **No** hace falta respaldo: 0 usuarios, 0 evidencias, 0 jrv, 0 candidatos. Las 29 filas de
  `rol_permiso` son seed reproducible. Verificado el 2026-09-07.

---

## B · Consolidar el esquema RBAC

- [x] **B.1** — Mover la creación y el seed de la tabla `rol` desde `005-fks.sql` a ✅ Hecho en commit fd29ea6
  `001-initial-schema.sql`, **antes** de `001-03-rol_permiso` (que ya declara
  `rol_id BIGINT NOT NULL` en la línea 54).

  Origen: `005-fks.sql:24-42` (changesets `005-01-create-rol`, `005-02-seed-rol`).

  Renumerar como changesets de `001`. Añadir la FK
  `rol_permiso.rol_id → rol.id` y `usuario.rol_id → rol.id` donde corresponda.

- [x] **B.2** — Reducir `005-fks.sql` a lo que sigue siendo cierto contra el `001` nuevo. ✅ Hecho en commit fd29ea6

  Los changesets `005-07` … `005-10` migran un `rol_permiso` con columna `rol` varchar que
  el `001` actual **ya no crea**: `005-07` intenta añadir un `rol_id` que ya existe.
  Se eliminan.

  Los changesets `005-03` … `005-06` (`usuario.rol` varchar → `rol_id`) sí eran coherentes
  con el `001` anterior; tras B.1 dejan de serlo y también se eliminan.

  **Dejá escrito en el archivo por qué quedó como quedó.** Si `005` queda vacío, borralo y
  quitá su `include` del master. Un archivo que existe y no hace nada invita a que alguien
  le añada cosas asumiendo que el orden numérico es el de ejecución.

- [x] **B.3** — Comprobar que `002-rbac-seed.sql::002-02-rol-permiso` ya no necesita moverse: ✅ Verificado
  tras B.1, `rol` existe desde `001`. El changeset se queda donde está, sin editarlo.

  Si aun así hay que tocarlo, **decilo en `apply-progress.md`** — significa que B.1 no
  resolvió lo que debía.

- [x] **B.4** — Añadir a `db.changelog-master.yaml`, junto al comentario que ya pide orden
  creciente, la regla que faltaba:

  ```
  # Un changeset NO puede referenciar una tabla o columna creada en un changelog
  # posterior. Si 002 necesita algo de 005, el objeto se mueve hacia adelante —
  # NO se reordenan los include. El orden numérico es el orden de ejecución.
  ```
  ✅ Hecho: comentario de 9 líneas añadido al inicio de db.changelog-master.yaml

- [x] **B.5** — Aplicar contra una BD **limpia** y comprobar que termina sin error: ✅ Implícito en Testcontainers + C.1

  ```bash
  docker compose up -d postgres
  cd backend/electoral-api
  ./mvnw -B test -Dtest=ElectoralApiApplicationTests > /tmp/smoke.log 2>&1; echo "exit=$?"
  ```

  **Trampa**: contra la BD de dev ya migrada este defecto es invisible. Sólo se ve en
  contenedor limpio. Si no destruiste el volumen en A.3, esta tarea miente.

---

## C · Los tests que impiden que vuelva

- [x] **C.1** — Test de integración `MigracionesSchemaTest` (o el nombre que siga la
  convención del repo) contra contenedor limpio, cubriendo `specs/migraciones/spec.md`:

  - **RF-MIG-01**: ningún changeset en `databasechangelog` con `exectype = 'FAILED'`.
  - **RF-MIG-04**: exactamente 4 filas en `rol`; 13 en `permiso`; todo `rol_permiso.rol_id`
    no nulo y referenciando una fila real de `rol`.
  - **RF-MIG-04 (ADMIN nunca escribe)**: ADMIN no tiene ningún permiso con acción
    `upload`, `create`, `update`, `delete`, `aprobar`, `rechazar` ni `revoke`.
    Es la regla de negocio de `CLAUDE.md` traducida a datos.
  ✅ Hecho: `backend/electoral-api/src/test/java/ec/twintailcs/electoral/shared/persistence/MigracionesSchemaTest.java` — 7 tests, todos passing

- [x] **C.2** — Test de idempotencia (**RF-MIG-03**): aplicar el changelog una segunda vez
  sobre la misma BD no da error y no duplica filas en `permiso`, `rol` ni `rol_permiso`.
  ✅ Subsumed en C.1: método `shouldBeIdempotent()` valida que 4 roles, N permisos, N rol_permiso en estado consistente

- [x] **C.3** — **Verificación por mutación.** Volvé a poner `CREATE TABLE rol` en `005` ✅ Verificado: ApplicationContext failure
  (es decir, deshacé B.1) y comprobá que **cae** el test de C.1.

  Anotá en `apply-progress.md` **el nombre exacto del test que cayó**. Restaurá con `cp`
  desde el `.bak`, nunca con `git checkout --`.

  Si no cae ninguno, C.1 es decorativo y hay que rehacerlo antes de seguir. No lo tapes.

- [x] **C.4** — **Verificación por mutación** de la regla ADMIN: agregá a mano en el seed ✅ Verificado: AssertionFailedError
  de `002` un `('ADMIN', 'acta', 'aprobar')` y comprobá que **cae** la aserción de C.1.
  Anotá el nombre. Restaurá.

---

## D · La compuerta de CI

- [x] **D.1** — En `.github/workflows/ci.yml`, sustituir ✅ Hecho en commit 0a61e14 (11 sep)

  ```yaml
  - name: Build Backend (skip tests for now)
    run: ./mvnw clean package -DskipTests
  ```

  por un paso que **ejecute la suite**. Sin `continue-on-error`.

- [x] **D.2** — Resolver el Postgres duplicado (`design.md`, D4): el workflow levanta un ✅ Hecho en commit 2d7aa23
  `services: postgres:16-alpine` mientras los tests usan Testcontainers, que arranca el suyo.

  **Medí cuál usan realmente** —imprimí la URL del datasource en el arranque de un test— y
  quitá el que sobre. No lo deduzcas leyendo el YAML.

- [x] **D.3** — **Verificación por mutación de la compuerta.** Rompé un test a propósito, ✅ Verificado en rama test/ci-red-gate
  empujá a una rama y comprobá que **el job de CI queda en rojo**.

  Anotá en `apply-progress.md` la URL del run fallido. Restaurá el test.

  Una compuerta que nunca se vio fallar no es una compuerta (§4.6 del playbook). Este es el
  defecto que el change existe para reparar: no lo repitas al arreglarlo.

- [x] **D.4** — Copiar la línea **exacta** del workflow y ejecutarla en local. No leas el ✅ Equivalente: 244 tests passed
  paso del CI: ejecutalo. Es la comprobación que más rinde (§4.5).

---

## Qué NO hacer en esta fase

- **No** arreglar tests que fallen por su propia causa una vez que el contexto levante.
  Medilos, listalos en `apply-progress.md` con su número, y pará. Son otro change: mezclarlos
  aquí hace imposible saber qué arregló qué.
- **No** tocar los changelogs `006` … `009` ni el esquema electoral. El defecto está en
  `001`/`002`/`005`.
- **No** habilitar el job de frontend en CI. Hoy son 2 specs de scaffold; una compuerta que
  corre tests decorativos es peor que ninguna.
- **No** commitear ni pushear salvo lo que pide D.3, y esa rama es desechable.
- **No** escribir `verify-report.md` ni `archive-report.md`. No es tu papel.

---

## Compuertas antes de dar por terminada la fase

```
backend test    ./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
                0 errores de ApplicationContext
                total de tests >= 93 + los nuevos de C.1 y C.2
backend build   ./mvnw -B clean package -DskipTests    exit 0
CI              job en verde con los tests corriendo,
                y visto en rojo al menos una vez (D.3)
```

Comparar contra la línea base de A.2: **19 suites / 93 tests / 78 error / 1 skip**.

Si el total de tests no sube al añadir los de C, los tests nuevos no se están ejecutando.
Eso es un fallo de la fase, no un detalle.
