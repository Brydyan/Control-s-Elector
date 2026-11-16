# Apply Progress — Dependencias, JDK 25 y bootstrap del admin por migración

**ODD ID:** `quarkus-deps-jdk25`
**Rama:** `brydyan/sc-510/odd-e2e-local-fix` (la actual; el ODD se pidió explícitamente sobre esta rama, sin crear una nueva)
**Fecha:** 2026-11-15
**Estado:** ✅ COMPLETADO — T1–T5 ejecutados. Con un hallazgo abierto que queda para decisión del operador (ver abajo).

---

## Tasks completadas

- [x] **T1** — Auditoría de dependencias: **18 dependencias directas de compile → 16**. Se eliminaron dos que eran peso muerto (`io.sentry:sentry-logback` y `bucket4j-core`). Inventario y veredicto por dependencia en el ODD (L5).
- [x] **T2** — Toolchain Java 25 verificado en las 6 superficies: 2 poms, 6 jobs de CI, `Dockerfile` y `Dockerfile.native`. Un comentario viejo en `Dockerfile.native` corregido.
- [x] **T3** — `DataSeeder` fuera (ya lo estaba) y **el admin de arranque ya no se crea en staging ni producción**. El admin pasó a su propio contexto de Liquibase, `bootstrap-admin`.
- [x] **T4** — Filtro por path del job nativo verificado, con el coupling de required checks.
- [x] **T5** — Cierre: `clean verify`, `spotless:check`, `jacoco:check` y verificación de consistencia de la suite de contrato.

---

## Qué cambió, en una línea por tarea

### T1 — Auditoría de dependencias

La auditoría de L4 (2026-10-22) quedó vieja: entre esa fecha y hoy entraron cuatro dependencias por features posteriores. Se volvió a correr contra el pom real.

| dependencia | veredicto |
|---|---|
| `com.nimbusds:nimbus-jose-jwt` | se queda — sin extension equivalente |
| `software.amazon.awssdk:s3` | se queda — `quarkus-amazon-s3` descartada a propósito (strangler) |
| `software.amazon.awssdk:apache-client` | se queda — explícita para la imagen nativa |
| `org.mindrot:jbcrypt` | se queda — sin extension equivalente |
| `io.sentry:sentry` | se queda — `quarkus-sentry` **no está** en el BOM 3.40.1 |
| `io.sentry:sentry-logback` | **ELIMINADA** — Quarkus no usa Logback; la dependencia ni siquiera arrastra Logback. Cero referencias |
| `com.github.vladimir-bukhtoyarov:bucket4j-core` | **ELIMINADA** — `RateLimitingService` implementa la ventana fija a mano; cero imports de `io.github.bucket4j` |

De paso se corrigió el javadoc de `RateLimitingService`, que decía "Usa Bucket4j" cuando el código nunca lo usó.

### T3 — El admin de arranque, que sí se creaba en producción

Esta es la parte que no estaba hecha. La migración `026-admin-inicial.sql` ya existía y el `DataSeeder` ya no estaba, pero **S8 no se cumplía**: el admin con su contraseña conocida y versionada se seguía creando en todos los entornos.

La causa era el contexto, y el comentario del changelog era **falso**. Afirmaba que la cuenta se podía excluir con `QUARKUS_LIQUIDASE_CONTEXTS=vacio`. Verificado contra la API de Liquibase 5.0.3:

```
runtime=[]            match(seed)=true   match(bootstrap-admin)=true   <- vacio NO excluye nada
runtime=[seed]        match(seed)=true   match(bootstrap-admin)=false
runtime=[seed,boot]   match(seed)=true   match(bootstrap-admin)=true
```

Con `Contexts` vacío, `ContextExpression.matches()` devuelve `true` para cualquier contexto. Una lista vacía no excluye nada: corre todo.

**El arreglo:** el admin recibe su propio contexto, `bootstrap-admin`, separado del `seed`.

| superficie | valor | efecto |
|---|---|---|
| `application.properties` (default) | `seed` | producción corre los datos de referencia y **no** crea el admin — fail-closed |
| `compose.yaml` (stack local) | `seed,bootstrap-admin` | E2E y contrato inician sesión como MASTER |
| `ci.yml` (`backend-contract`) | `seed,bootstrap-admin` | la suite de contrato necesita el MASTER |
| `ci-native.yml` | `seed,bootstrap-admin` | idem contra el binario nativo |
| `load-test-baseline.yml` | `seed` | los usuarios de k6 vienen del `seed`; el admin de arranque no se usa |
| `k8s/configmap.yaml` (producción) | `seed` | explícito: la cuenta se provisions a mano |

El `seed` **no** se puede apagar en producción: es el único que trae los datos de referencia que el producto necesita (cargos, partidos, candidatos, JRVs, geografía). Por eso el admin necesitaba un contexto aparte, y no un "seed" apagado.

**Verificación del checksum:** los 89 checksums del changelog quedan **idénticos**. Cambiar el `context` de un changeset no altera su checksum — el contexto es del changeset, no del cambio. Al editar el *cuerpo* de un changeset ya aplicado el checksum **sí** cambia, así que las correcciones de comentario en `018-notification-seed.sql` y en el bloque final de `002-rbac-seed.sql` se revirtieron: la sonda mostraba que cambiaban el checksum de `018-01` y `002-02`. Los comentarios *antes* de la línea `-- changeset` sí se pueden tocar sin riesgo.

**Limpieza de lo que dejó el `DataSeeder`:**

- `EmailGenerator.generarBase()` era público solo por el seeder → `private`.
- `APP_SEED_ADMIN_EMAIL` / `APP_SEED_ADMIN_PASSWORD` no los leía nadie → fuera de `.env.example`, `ci.yml`, `ci-native.yml` y `load-test-baseline.yml`.
- Documentación que describía al `DataSeeder` y al email `admsismas@control.ttcs` (inexistente desde el corte a Quarkus) actualizada en `documents/CREDENCIALES.md`, `documents/QA-BACKEND-VERIFICACION.md`, `backend/contract-tests/README.md` y los mocks de E2E.
- El test negativo de login de la suite de contrato usaba un email **inexistente**: el 401 no probaba lo que decía probar. Ahora usa la cuenta real, así que el 401 es por la contraseña.

---

## Test results

### Backend (`backend/electoral-api-quarkus`)

| suite | antes | después | estado |
|---|---|---|---|
| `mvn clean verify` | 123 run / 0 fallos / **7 errores** / 46 omitidos | 123 run / 0 fallos / **7 errores** / 46 omitidos | ✅ sin regresión |
| `mvn test` (sin `*IT`) | 83 run / 0 fallos / **1 error** / 12 omitidos | 83 run / 0 fallos / **1 error** / 12 omitidos | ✅ sin regresión |
| `mvn spotless:check` | — | BUILD SUCCESS | ✅ |
| `mvn verify` (fase verify) → `jacoco:check` | — | "All coverage checks have been met." | ✅ |

**Los 7 errores son todos `Could not find a valid Docker environment`** (Testcontainers: Postgres y Redis reales). No hay ni un fallo de aserción.

El "antes" se midió **contra un worktree limpio en `e4847a5`** (el commit previo a este trabajo), con el mismo comando y el mismo entorno: el resultado es idéntico, 123 / 0 / 7 / 46. La comparación no es una estimación.

### Suite de contrato (`backend/contract-tests`)

**No se pudo ejecutar en esta máquina:** necesita un backend vivo contra un Postgres real, y esta máquina no tiene demonio Docker ni servidor Postgres. `mvn test-compile` sí pasa.

Lo que sí se verificó, mecánicamente, con un chequeo que lee los archivos y compara:

| chequeo | resultado |
|---|---|
| la migración 026 crea `admin@control-elector.local` | ✅ |
| la suite de contrato usa el mismo email que crea la migración | ✅ |
| 026 tiene contexto propio `bootstrap-admin` | ✅ |
| `ci.yml` (backend-contract) pide `bootstrap-admin` | ✅ |
| `ci-native.yml` pide `bootstrap-admin` | ✅ |
| `k8s` (producción) corre solo `seed` | ✅ |
| el default de la app es `seed` (fail-closed para el admin) | ✅ |
| `compose` local pide `bootstrap-admin` | ✅ |
| el test negativo de login usa la cuenta real | ✅ |
| no quedan referencias a `APP_SEED_ADMIN_*` | ✅ |

**10 / 10.** La suite tiene que correr en CI, contra la misma API que producción.

### Frontend

| suite | resultado | estado |
|---|---|---|
| `vitest` (suite unitaria) | **980 / 980** | ✅ |
| `vitest run a11y` | **5 / 5** (3 archivos) | ✅ |
| `prettier --check "src/**/*.{ts,html,scss}"` | limpio | ✅ |
| `ng lint` | limpio | ✅ |

---

## Commits

| Commit | Task |
|---|---|
| `a0aee03` `chore(backend): auditar dependencias y quitar dos dependencias muertas (T1)` | T1 |
| `303eefb` `build(backend): verificar el toolchain Java 25 completo (T2)` | T2 |
| `8d57521` `fix(backend): el admin de arranque ya no se crea en staging ni produccion (T3)` | T3 |
| `d16b550` `docs(odd): verificar el filtro por path del job nativo y su coupling con required (T4)` | T4 |

Todos en la rama actual, formato convencional, sin co-author de IA.

---

## Hallazgo abierto — para decisión del operador

**No es de este feature y no se arregló acá.** Se documenta porque es la misma clase de defecto que S8 y porque el arreglo en serio excede la frontera de esta rebanada.

El `025-data-seed.sql` siembra, en el mismo contexto `seed`, cinco cuentas de referencia con contraseña `admin12345`:

| usuario | rol |
|---|---|
| `mastersolon@example.local` | **MASTER** |
| `coordinatorcortes@example.local` | COORDINATOR |
| `adminpalma@example.local` | ADMIN |
| `observervera@example.local` | OBSERVER |
| `observerquimis@example.local` | OBSERVER |

Y el `034-k6-load-test-users.sql` siembra veinte cuentas más con `SecurePass123!`, una de ellas ADMIN. Con `seed` corrido —que es lo que corre en producción— **esas cuentas también existen**.

El hash de las cinco primeras lo repara el `027-repara-hash-cuentas-referencia.sql` a un BCrypt **válido** de `admin12345`, o sea que la contraseña funciona.

Arreglarlo en serio pide tres cosas que no se pueden hacer en esta rebanada:

1. **Partir el changeset `025`**, que ya corrió en todas las bases y cuyo checksum no se puede tocar (misma política que el `027`).
2. **Decidir qué datos de referencia se quedan en producción** — el `025` mezcla geografía y ballots con usuarios de prueba y con actas de ejemplo en distintos estados.
3. **Borrar las filas** que ya quedaron en las bases existentes. Borrar datos está explícitamente fuera de la frontera de esta rebanada, por la misma razón que los 17 admins duplicados de la base local.

Es un ODD propio, no un detalle de este. El hallazgo queda escrito en el ODD (L7).

---

## Pendientes operativos (fuera del código)

1. **Las bases que ya corrieron `026` siguen teniendo la fila.** Cambiar el contexto impide que se cree en adelante; no borra lo que ya está. En las bases de staging/producción donde el admin ya existe, hay que **borrarlo o rotarle la contraseña** a mano.
2. **La regla de required checks** del ruleset de `develop` no se pudo leer desde el repo (la API de rulesets responde 404 sin autenticación de admin). La afirmación de que "Backend Native Image" no es un check requerido sale de la documentación del propio workflow. Si alguien lo promueve a requerido, hay que quitarle el filtro de paths en el mismo cambio (S10).
3. **El AOT nativo** no se construyó aquí: `native-image` pide 6-8 GB de RSS y la imagen de builder no corre sin Docker. Lo construye el job `Backend Native Image` de CI, que ya usa el builder de Mandrel 25.

---

## Notas de método

- La verificación del gate de contextos de Liquibase se hizo con una **sonda** que parsea el changelog con la misma librería que corre en producción (`liquibase-core` 5.0.3, del `classpath` del módulo) y simula el gate con `ContextExpression.matches()`. No abre conexión a la base. Vive en `/tmp`, fuera del repo: no es parte del entregable.
- El baseline de tests se midió con `git worktree` sobre `e4847a5`, no con `git stash`: el stash llegó a registrar unos archivos del ODD como borrados y el `pop` los elimino del arbol de trabajo. Con worktree el arbol de trabajo no se toca.
