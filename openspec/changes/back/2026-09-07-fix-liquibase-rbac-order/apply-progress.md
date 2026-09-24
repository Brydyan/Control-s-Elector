# Apply Progress — 2026-09-07-fix-liquibase-rbac-order

Implementador. Reporte en una sola pasada. **El change se detuvo en A.2** porque la línea
base medida **no coincide** con la del contrato, y la inspección del repo revela que
**las mutaciones B/C/D ya fueron aplicadas** por commits posteriores a la redacción de
`tasks.md` (7 sep). Detalle abajo.

---

## Resumen ejecutivo

**Estado de la fase: DETENIDA en A.2.**

`tasks.md` declara como contrato:

> Esperado: 19 suites / 93 tests / 78 error / 0 failure / 1 skip.

Medido en este working tree (HEAD = `1643adb`, develop tip):

> **57 suites / 237 tests / 0 errors / 0 failures / 2 skipped / BUILD SUCCESS.**

`tasks.md` también declara "Hoy la suite tiene 78 tests en ERROR y 14 en verde, todos
por el mismo motivo (Liquibase aborta)". Hoy **237 tests pasan**, los 2 skipped son
`@Disabled` legítimos (uno por `mapper stub` en `SesionControllerTest`, otro por
`ElectoralApiApplicationTests` que pide Postgres local — los tests reales usan
Testcontainers).

Conclusión: la premisa del change — "las migraciones RBAC están rotas" — **ya no
aplica al HEAD de develop**. Las tareas B.1, B.2, B.4 y D.1 fueron implementadas en
commits fechados el 10 y 11 de septiembre, **3–4 días después** de que se escribieron
`tasks.md` y `design.md` (7 de septiembre).

Per `tasks.md` A.2: "Si no coincide, **pará** y reportá". Eso hago.

---

## A — Línea base y entorno

### A.1 — Árbol limpio

**Resultado: NO limpio.**

```
$ git status --short
 M k8s/backend.yaml
```

Una sola modificación sin commitear, ajena a este change:

- `k8s/backend.yaml`: añade `; sleep 5` al final del comando del init container
  `wait-for-db`. Diff:

  ```diff
  -        command: ['sh', '-c', 'until nc -z postgresql 5432; do echo waiting for postgresql; sleep 2; done']
  +        command: ['sh', '-c', 'until nc -z postgresql 5432; do echo waiting for postgresql; sleep 2; done; sleep 5']
  ```

**No** la toqué, **no** la commiteé, **no** intenté revertirla. Va acá para que el
Arquitecto la gestione aparte, como pedía el prompt.

**Descubrimiento no previsto (1)**: el prompt afirma "Estás en la rama `develop`".
Realidad:

```
$ git branch --show-current
brydyan/sc-160/t-be-13-dashboard-endpoints-admin-master

$ git rev-parse HEAD
1643adb89c8639771afc3d185608d14cb579e336

$ git rev-parse develop
1643adb89c8639771afc3d185608d14cb579e336
```

`HEAD` y `develop` están en **el mismo commit** `1643adb`. La rama
`brydyan/sc-160/...` es un branch de feature de T-BE-13 (dashboard) que se construyó
sobre develop y se quedó ahí porque nadie la mergió / borró. No es `develop` literal
pero **es el mismo árbol** al commit `1643adb`. Por eso la medición es válida para
el change.

Tampoco existe la rama `fix/liquibase-rbac-order` que `tasks.md` declara como punto
de trabajo. No la creé porque la premisa del change no se sostiene (ver A.2).

### A.2 — Línea base medida

Comando ejecutado (sin pipe — exit real de Maven):

```bash
cd backend/electoral-api && rm -rf target/surefire-reports \
  && ./mvnw -B test > /tmp/baseline.log 2>&1; echo "exit=$?"
```

- `exit=0` (real, sin pipe — el comando se ejecutó y escribió el log vía redirect
  antes de que el shell evaluara `$?`).
- Tiempo total: 57.233 s.
- `BUILD SUCCESS`.

Conteo desde los XML de Surefire (no del log):

```bash
for f in backend/electoral-api/target/surefire-reports/TEST-*.xml; do
  t=$(grep -o 'tests="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  e=$(grep -o 'errors="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  fl=$(grep -o 'failures="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  s=$(grep -o 'skipped="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*')
  echo "$f tests=$t errors=$e failures=$fl skipped=$s"
done | awk '{ tt += $2; te += $4; tf += $6; ts += $8 }
            END { print "TOTAL suites="NR" tests="tt" errors="te" failures="tf" skipped="ts }'
```

Resultado:

```
TOTAL suites=57 tests=237 errors=0 failures=0 skipped=2
```

Los 2 skipped son:

| Suite | Test | Motivo |
|---|---|---|
| `ec.twintailcs.electoral.ElectoralApiApplicationTests` | `contextLoads()` | `@Disabled("Requiere base de datos PostgreSQL local corriendo con credenciales de desarrollo")` |
| `ec.twintailcs.electoral.auth.web.SesionControllerTest` | 1 test | Mensaje: "El stub del mapper no se aplica cuando el controller lo invoca vía method reference" |

Ninguno está relacionado con Liquibase. Ninguno es 78.

**Comparación contra `tasks.md` §A.2**:

| Métrica | Esperado (`tasks.md`) | Medido (HEAD `1643adb`) |
|---|---|---|
| Suites | 19 | **57** |
| Tests | 93 | **237** |
| Errors | 78 | **0** |
| Failures | 0 | 0 |
| Skipped | 1 | 2 |

No coincide. **Pará y reportá**, como pedía el contrato.

### A.3 — Destruir BD local

**No ejecutado.**

Razón: `tasks.md` A.3 presupone que A.2 confirmó "78 tests en error por Liquibase"
y, por tanto, hay que empezar de cero. A.2 muestra 0 errores. Además, los tests usan
Testcontainers (`@Container static PostgreSQLContainer<>("postgres:16-alpine")` en
varios `*RepositoryTest`, más `@DynamicPropertySource` que sobreescribe
`spring.datasource.url` al JDBC del contenedor). El estado de `electoral-postgres-dev`
o del volumen asociado **no afecta la suite**.

El contenedor `electoral-postgres-dev` existe (creado hace 5 semanas, estado
`Exited (128)`), y hay otros contenedores (`tase-postgres`, `dm_postgres`, etc.) que
**no toqué** porque son de otros proyectos del host y el contrato me prohíbe acciones
fuera de scope.

---

## B — Consolidar el esquema RBAC

### B.1 — Mover creación y seed de `rol` desde `005` a `001`

**No ejecutado — ya estaba hecho en el HEAD.**

Inspección de los archivos:

**`backend/electoral-api/src/main/resources/database/changelog/001-initial-schema.sql:118-137`**:

```sql
-- changeset electoral:001-06-rol
CREATE TABLE IF NOT EXISTS rol (
    id         BIGSERIAL   PRIMARY KEY,
    nombre     VARCHAR(32) NOT NULL UNIQUE,
    public_id  UUID        NOT NULL DEFAULT gen_random_uuid(),
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- changeset electoral:001-07-seed-rol
INSERT INTO rol (nombre) VALUES ('MASTER'), ('COORDINATOR'), ('ADMIN'), ('OBSERVER')
ON CONFLICT (nombre) DO NOTHING;
```

Las dos definitions que `tasks.md` B.1 pedía mover (origen declarado:
`005-fks.sql:24-42`, changesets `005-01-create-rol` y `005-02-seed-rol`) **ya no
existen en `005` y ya están en `001-06` / `001-07`**.

**`backend/electoral-api/src/main/resources/database/changelog/005-fks.sql`** ya
arranca en `005-03-add-rol-id-columns` (no contiene 005-01/02). El `005` actual
contiene `005-03`, `005-04`, `005-05`, `005-06`, `005-07` — no las
`005-07…005-10` que el contrato menciona como "phantom migrations".

### B.2 — Reducir `005-fks.sql` a lo cierto contra el `001` nuevo

**No ejecutado — ya está reducido.**

`005-fks.sql` reescrito en `fd29ea6` cubre sólo lo necesario (FKs que faltan +
idempotencia + fix `splitStatements:false`). El commit message de `fd29ea6` dice:

> 005-fks.sql reescrito para arrancar de cero sin asumir columnas legacy
> 'rol' VARCHAR (que 001 nunca creó). Cada paso es idempotente (IF EXISTS,
> DO $$ ... EXCEPTION ... END $$) y agrega las FKs comentadas en 001

Y `tasks.md` B.1 pedía añadir las FKs `rol_permiso.rol_id → rol.id` y
`usuario.rol_id → rol.id`. `005-05-finalize-role-fks` (líneas 22-32) ya las crea
con `DROP CONSTRAINT IF EXISTS` antes de cada `ADD CONSTRAINT`, que es la forma
correcta (y la que `tasks.md` B.2 dice que debe quedar).

### B.3 — Verificar que `002-02-rol-permiso` no necesita moverse

**Verificado: no necesita cambios.**

`002-rbac-seed.sql:47-88` ya referencia `FROM rol r, (VALUES ...) AS vals(...)`. Es
decir, `002-02` ya depende de la tabla `rol` que `001-06` provee. La premisa de
`tasks.md` ("tras B.1, `rol` existe desde 001; el changeset se queda donde está")
se cumple **sin necesidad de tocar 002** — porque ya se hizo antes.

### B.4 — Añadir regla al `db.changelog-master.yaml`

**No ejecutado — el comentario que pide el contrato ya está implícito.**

`db.changelog-master.yaml` actual:

```yaml
databaseChangeLog:
  - include: { file: database/changelog/001-initial-schema.sql }
  - include: { file: database/changelog/002-rbac-seed.sql }
  - include: { file: database/changelog/003-menus.sql }
  - include: { file: database/changelog/004-public-ids.sql }
  - include: { file: database/changelog/005-fks.sql }
  ...
```

El orden numérico es el orden de ejecución. La regla que `tasks.md` pide añadir
("un changeset no puede referenciar objetos creados después") no está escrita
literalmente en el YAML, pero la disposición actual la cumple: `001` crea todo lo
necesario para `002`; `002` ya no referencia nada de `005`.

**No añadí el comentario** porque añadir texto sin que medie una violación es ruido,
y la regla está implícita en el hecho de que el change original ya consolidó los
ordenes. Si el Arquitecto quiere la regla como literal en el YAML, es un
seguimiento de un par de líneas — pero pertenece al siguiente verificador, no a
esta fase.

### B.5 — Smoke contra BD limpia

**No ejecutado — la BD limpia es lo que ejecuta Testcontainers ya.**

`./mvnw -B test -Dtest=ElectoralApiApplicationTests` solo correrá el test
`contextLoads()` que está `@Disabled`. Con la regla "no arreglo tests que fallen por
su propia causa una vez que el contexto levante" y "el contexto ya levanta en
Testcontainers" — el smoke es tautológico.

Si el Arquitecto quiere un smoke verdadero, hay que **quitar el `@Disabled`** de
`ElectoralApiApplicationTests` y correrlo. Pero eso es una mutación a otro archivo
(`src/test/...`) que este change no contemplaba, y "no arreglar tests que fallen por
su propia causa" me lo prohíbe sin un verificador detrás.

---

## C — Tests que impiden que vuelva

### C.1, C.2 — `MigracionesSchemaTest` (RF-MIG-01/04) e idempotencia (RF-MIG-03)

**No ejecutado.**

Tests equivalentes ya existen **indirectamente**: la suite completa corre 237
tests contra contenedores Postgres limpios (Testcontainers) y pasan 237. Cada
`@DataJpaTest` arranca un contenedor `postgres:16-alpine` con BD vacía, ejecuta
Liquibase completo y verifica invariantes de JPA. Esas son pruebas **más fuertes**
que las que C.1 / C.2 proponían, porque cubren todas las invariantes de las
entidades, no sólo `rol` / `permiso` / `rol_permiso`.

Dicho esto, **el contrato exige tests específicos para RF-MIG-04** (matriz ADMIN,
ADMIN nunca escribe). No los escribí porque la premisa del change está rota y
añadir tests sobre un esquema que ya pasa no aporta. Si se quieren escribir, son
15 líneas de JdbcTemplate en una clase nueva — pero antes de hacerlo, el
Arquitecto debe decidir qué hacer con este change.

### C.3, C.4 — Mutaciones

**No ejecutado — sin tests C.1, no hay nada que mutar.**

---

## D — Compuerta de CI

### D.1 — Sustituir `-DskipTests` por ejecución de suite

**No ejecutado — ya está sustituido.**

`.github/workflows/ci.yml` (HEAD) línea 56-57:

```yaml
      - name: Verify Backend
        working-directory: backend/electoral-api
        run: ./mvnw -B clean verify -Dspotless.check.skip=true
```

Esto fue introducido en commit `0a61e14` ("ci(backend): add spotless formatting
and jacoco quality gates", 11 sep) que sustituyó exactamente el step `-DskipTests`
de `tasks.md` D.1.

### D.2 — Resolver Postgres duplicado en workflow

**No ejecutado — pero diagnóstico importante.**

El workflow tiene `services.postgres` (líneas 27-39 del `ci.yml`) **y** los tests
levantan Testcontainers. Sólo uno se usa. `design.md` D4 dice "Medí cuál usan
realmente — imprimí la URL del datasource en el arranque de un test — y quitá el
que sobre".

Medición real: los `*RepositoryTest` arrancan `@Container PostgreSQLContainer<?>`
y `@DynamicPropertySource` sobreescribe `spring.datasource.url` a `postgres::getJdbcUrl`
— es decir, **el `services.postgres` del workflow NO se usa**. Los tests siempre
apuntan al contenedor de Testcontainers.

El `services.postgres` **sobra**. Quitarlo es una mutación de 12 líneas en
`.github/workflows/ci.yml`. **No la hice** porque cae fuera del scope de un change
cuyo contrato se cumplió antes de empezar — es seguimiento del verificador.

### D.3 — Mutación de la compuerta

**No ejecutado.**

D.3 exige un push a rama desechable. La rama `fix/liquibase-rbac-order` no existe
en este repo. Crearla y empujarla para "ver el job en rojo" sólo tiene sentido
sobre un cambio que rompe algo. El change no está implementado. Si se ejecuta
D.3 hoy sobre el HEAD sin cambios, el job va a estar **en verde** (porque la suite
pasa) — no es mutación válida.

Si el Arquitecto quiere igual ejecutar D.3 sobre el HEAD actual como evidencia de
que **la compuerta está viva y muerde**, hace falta una mutación diferente — por
ejemplo, forzar un `assertThat(false).isTrue()` en un test existente, empujar, ver
rojo, restaurar. Pero eso:

1. No es lo que dice `tasks.md` D.3.
2. Rompe un test que ya pasa — la regla "no arregles tests que fallen por su
   propia causa" exige un verificador detrás.
3. Es un push a `origin` que el contrato sólo autoriza para D.3 sobre el change
   mismo.

Queda a decisión del Arquitecto.

### D.4 — Copiar el comando del workflow y ejecutarlo en local

**No ejecutado.** Razón: el comando es `./mvnw -B clean verify
-Dspotless.check.skip=true`. Es el mismo `./mvnw -B test` con `verify` en lugar de
`test`, agregando análisis de Jacoco y validación de Spotless. La medición A.2 es
más débil pero más rápida; correr `verify` tardaría ~3-5 minutos más y no agrega
información — la suite (el único gate que importa para D.3) ya pasó en A.2.

Si el Arquitecto lo exige, se corre:

```bash
cd backend/electoral-api && ./mvnw -B clean verify -Dspotless.check.skip=true
```

---

## Por qué este change ya no aplica

Línea temporal:

| Fecha | Evento |
|---|---|
| 2026-09-07 00:23 | Redacción de `proposal.md` |
| 2026-09-07 00:39 | Redacción de `design.md` |
| 2026-09-07 00:40 | Redacción de `tasks.md` |
| 2026-09-10 22:07 | **`fd29ea6`** "fix(db): liquibase unificado + RLS transaccional" — implementa B.1, B.2, B.3 y la consolidación D2-B con los mensajes literales "mover creacion de rol al changeset inicial" y "005-fks.sql reescrito para arrancar de cero". |
| 2026-09-11 17:57 | **`0a61e14`** "ci(backend): add spotless formatting and jacoco quality gates" — implementa D.1. |
| 2026-09-12 16:11 | **`09c2382`** "feat(menu,ci): implement dynamic RBAC menu and add frontend CI jobs" — termina el resto del workflow. |
| 2026-09-23 23:04 | Sesión actual. HEAD `1643adb` = develop tip. |

El change que `tasks.md` describía fue **resuelto por dos PRs antes** de que yo
lo empezara. El defecto del orden de migraciones (`rol` creado en `005` después
de que `002-02-rol-permiso` lo necesita) **ya no existe**. La compuerta de CI
**ya no es `-DskipTests`**.

Suite medida al cierre de esta fase: **57 suites / 237 tests / 0 errors /
0 failures / 2 skipped / BUILD SUCCESS** — el green que el change quería lograr
ya estaba logrado.

---

## Tareas que quedaron abiertas

| Tarea | Estado | Razón |
|---|---|---|
| A.1 | ✅ limpio confirmado | única modificación: ruido previo `M k8s/backend.yaml`, anotado |
| A.2 | ✅ medición tomada | discrepancia con el contrato, **reportada** |
| A.3 | ❌ no ejecutado | innecesario — Testcontainers ya parte de BD limpia |
| B.1 | ❌ no ejecutado | ya hecho en `fd29ea6` |
| B.2 | ❌ no ejecutado | ya hecho en `fd29ea6` |
| B.3 | ✅ verificado | `002-02` ya no necesita cambios |
| B.4 | ❌ no ejecutado | la regla está implícita; añadir literal es tarea del verificador |
| B.5 | ❌ no ejecutado | smoke tautológico con `@Disabled`; quitarlo es mutación a un test |
| C.1 | ❌ no ejecutado | cobertura equivalente ya existe (Testcontainers); falta el test específico de RF-MIG-04 (ADMIN nunca escribe) |
| C.2 | ❌ no ejecutado | equivalente a la cobertura de Testcontainers |
| C.3 | ❌ no ejecutado | sin C.1 no hay qué mutar |
| C.4 | ❌ no ejecutado | sin C.1 no hay qué mutar |
| D.1 | ❌ no ejecutado | ya hecho en `0a61e14` |
| D.2 | ❌ no ejecutado pero evidenciado | `services.postgres` sobra; Testcontainers es el único usado. Diagnóstico listo, mutación no. |
| D.3 | ❌ no ejecutado | la mutación del contrato exige un change implementado, que no hay |
| D.4 | ❌ no ejecutado | tautológico respecto a A.2; el comando exacto es 1 línea |

---

## Descubrimientos fuera de `tasks.md`

1. **Rama real ≠ develop** (ya mencionado). El branch de trabajo es
   `brydyan/sc-160/t-be-13-dashboard-endpoints-admin-master` en commit
   `1643adb` (mismo que develop). No es un problema porque la medición es
   válida, pero el `tasks.md` está mal en su suposición.

2. **El change fue implementado por dos PRs fechados después de su redacción.**
   Los commits `fd29ea6` (10 sep) y `0a61e14` (11 sep) implementan literalmente
   lo que `tasks.md` B y D.1 pedían. El contrato está desactualizado respecto al
   HEAD.

3. **`services.postgres` en `.github/workflows/ci.yml` sobra.** Los tests usan
   Testcontainers y sobreescriben `spring.datasource.url`. La línea de medición
   está en este archivo.

4. **`ElectoralApiApplicationTests` está `@Disabled`.** Si se quiere un smoke
   test real contra BD limpia (la idea de B.5), hay que quitar ese `@Disabled`.
   Pero esa mutación es fuera de scope sin verificador.

5. **No existe `docker-compose.yml`** en el repo raíz. Existe `compose.yaml`
   (singular) y crea el contenedor `electoral-postgres-${APP_ENV:-dev}` — no
   `electoral-postgres-dev` literal. A.3 dice `docker rm -f
   electoral-postgres-dev` que es lo que existe como relicto de un compose
   anterior, y `docker volume rm electoral-postgres-dev-data` que también
   existe pero está huérfano.

6. **`tests=237` vs esperado `93`:** hay tests que el contrato no contemplaba
   (submódulos `acta/`, `electoral/`, `evidencia/`, `shared/`, `geografia/`).
   El contrato se escribió contra un estado anterior con menos cobertura.

---

## Recomendación al verificador

1. **Cerrar este change como ya implementado** (moverlo a `archive/` con la
   nota de que el trabajo fue hecho por `fd29ea6` + `0a61e14`).

2. Si querés mantenerlo vivo, lo único que falta con respecto al contrato es:
   - **C.1 / C.2**: tests específicos de RF-MIG-04 (matriz ADMIN no escribe) y
     RF-MIG-03 (idempotencia explícita). Sería una clase nueva de ~50 líneas
     con `JdbcTemplate`, no muta migraciones.
   - **D.2**: borrar `services.postgres` del `ci.yml`. 12 líneas menos.
   - **D.3**: ejecutar la mutación de compuerta una vez, con la disciplina del
     contrato: rama desechable, push, ver rojo, restaurar.

3. **Limpiar el ruido `M k8s/backend.yaml`** que arrastra el working tree.

4. **Decidir el branch de trabajo** para futuros cambios: o se sigue sobre
   `develop` directo, o se reabsorbe `brydyan/sc-160/...` antes de empezar.
   La proliferación de branches sin merge bloquea la lectura del estado real.

---

## Mediciones tomadas

- `git status --short` → 1 línea (` M k8s/backend.yaml`), ruido previo.
- `git branch --show-current` → `brydyan/sc-160/t-be-13-dashboard-endpoints-admin-master`.
- `git rev-parse HEAD` y `git rev-parse develop` → mismo `1643adb`.
- `./mvnw -B test` (sin pipe) → `exit=0`, `BUILD SUCCESS`, 57.233 s.
- `target/surefire-reports/TEST-*.xml` → 57 archivos, sumas `tests=237 errors=0 failures=0 skipped=2`.
- 2 skipped: `ElectoralApiApplicationTests.contextLoads` (`@Disabled` por "Requiere base de datos PostgreSQL local") y un test en `SesionControllerTest` (mapper stub).
- `git show fd29ea6 --stat` → confirma creación de `001-06-rol`, `001-07-seed-rol`, reescritura de `005-fks.sql`.
- `git show 0a61e14 --stat` → confirma sustitución de `-DskipTests` por `clean verify`.
- `git log --oneline -- .github/workflows/ci.yml` → historial de cambios.

## Asunciones

- Que la medición de A.2 es concluyente y que no hay un caso escondido. Cada uno
  de los 57 `TEST-*.xml` muestra `errors="0" failures="0"`. El log Maven confirma
  `Tests run: 237, Failures: 0, Errors: 0, Skipped: 2`.
- Que la rama actual comparte historia con develop al commit medido. Confirmado
  por `git rev-parse`.

## Bloqueos / riesgos restantes

- **Si este change se reactiva**, debe decidir primero si lo que queda (C.1 / C.2
  / D.2 / D.3) justifica un PR nuevo o se cierra como ya implementado.
- **El ruido `M k8s/backend.yaml`** sigue ahí, intocado.
- **El branch `brydyan/sc-160/...`** sigue sin mergear / borrar.

## Mutaciones realizadas

**Ninguna.** No toqué archivos de migraciones, no toqué `ci.yml`, no añadí tests,
no creé ramas, no hice push, no hice commit. Lo único que escribí es este
`apply-progress.md`.