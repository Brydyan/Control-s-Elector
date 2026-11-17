# Dependencias, JDK 25 y bootstrap del admin por migración

PR apilado sobre `feat/backend-quarkus-cutover` (#150). Tres frentes que el backend
nativo dejó al descubierto, más el bug de idempotencia del seeding que llevaba meses
latente.

## Specs

Las cadenas del operador son textuales. No se reinterpretan.

- **S1** — "no puedo aprobrar mi propio pr asi que las siguiente modificaciones laremos con punta en el pr 150 osea un stack pr"
- **S2** — "dejar de usar seed, mejor usar pura migracion y en la migracion usar insert para datos"
- **S3** — Auditar las dependencias existentes.
- **S4** — Mover todo a Java 25.
- **S5** — Filtro por path del job nativo.

### Specs derivadas de la evidencia

Cada una es una regresión si se ignora. No son requisitos inventados: son comportamientos
que el sistema tiene o tuvo y que este feature debe preservar o corregir explícitamente.

- **S6** — El admin de arranque existe exactamente una vez. El bug actual crea uno nuevo en
  **cada reinicio** (verificados 17 en la base local entre `admsismas@` y
  `admsismas16@control.ttcs`), porque `DataSeeder` compara contra un email que
  `EmailGenerator.generar()` devuelve por contrato libre.
- **S7** — La contraseña del admin de arranque **no** es una credencial conocida y
  versionada. Es la razón por la que este feature no es un `INSERT` más: el hash no puede
  ser el de una contraseña que cualquiera que lea el repo pueda usar.
- **S8** — El usuario sembrado es un artefacto de **desarrollo**. En un entorno real el
  admin se provisions con credenciales propias; la migración no debe crear la puerta de
  entrada a producción.
- **S9** — Mover a Java 25 no rompe la suite de contrato ni el AOT. El ejecutable nativo se
  compila con el builder de Mandrel 25 (JDK 25) desde `12a4264`, así que el módulo ya se
  compila con 25 sin que nada lo diga; lo que cambia es el resto del toolchain.
- **S10** — El job nativo sigue reportando un status para cada PR. Si se filtra por paths y
  el check es *required*, un PR que no toque el backend queda bloqueado para siempre
  esperando un status que nunca llega. El filtro y la regla de required se cambian juntos o
  no se cambian.

## Tasks

- **T1** — Auditar dependencias:Extensions de Quarkus presentes y las 3 directas no-Quarkus. Ruta: inline. (S3)
- **T2** — Mover el toolchain a Java 25: 5 jobs de CI, `maven.compiler.release` en 2 poms, Dockerfile. Ruta: inline. (S4, S9)
- **T3** — Quitar `DataSeeder` y sembrar el admin por migración. Ruta: inline. (S2, S6, S7, S8)
- **T4** — Filtro por path del job nativo. Ruta: inline. (S5, S10)
- **T5** — Cierre: `clean verify`, spotless, contract suite. Ruta: inline.

### Frontera de la rebanada

Fuera: el tamaño de la imagen nativa (422 MB, no bloquea), la limpieza de los 17 admins
duplicados de la base local (es borrar datos y va aparte), y el `proxy.conf` que apunta a
`:8081` (frontend, otro PR).

## Log

### L1 — 2026-10-22 — Pedido

> "no puedo aprobrar mi propio pr asi que las siguiente modificaciones laremos con punta en el pr 150 osea un stack pr"

> "dejar de usar seed, mejor usar pura migracion y en la migracion usar insert para datos"

Seleccionados ademas: auditar dependencias, mover todo a Java 25, filtro por path del job nativo.

### L2 — 2026-10-22 — Por que el PR va apilado y no sobre develop

`#139` y `#140` ya estan mergeados en `develop` (`26e2a3b`). El `#150` sigue abierto y
`BLOCKED / MERGEABLE / REVIEW_REQUIRED`, y el operador no puede aprobar su propio PR: es
una regla de GitHub, no un permiso que falte. El PR apilado nasce de
`origin/feat/backend-quarkus-cutover` (`e9b6d83`).

Los hashes de la rama del #150 cambiaron entre el push y este trabajo. Verificado que el
**arbol es identico**: `git diff 9670f6f origin/feat/backend-quarkus-cutover` vacio. Solo
cambio la fecha de commit. El CI verde sigue siendo valido para ese contenido.

### L3 — 2026-10-22 — El bug del seeding, con evidencia

`DataSeeder.run()` hacia:

```java
String emailGenerado = emailGenerator.generar(adminNombre, adminApellido, "MASTER");
if (usuarios.findByEmail(emailGenerado).isPresent()) return;
```

`EmailGenerator.generar()` devuelve **por contrato** el primer email que no existe
(`admsismas`, luego `admsismas2`, ...), asi que el `findByEmail` posterior nunca lo
encuentra: la guarda es inalcanzable y cada arranque inserta un admin nuevo. El log del
seeder delata el sintoma (`'admsismas16@control.ttcs'`; un numero alto en el sufijo es la
senal).

Ademas `EmailGenerator` tiene `MAX_RETRIES = 100` y lanza `IllegalArgumentException` al
agotar los sufijos, asi que en el reinicio ~101 el backend dejaria de arrancar.

Dos bugs mas en el mismo lugar:
- `app.seed.admin-email` esta declarada en `application.properties` y pasada por `.env`,
  `.env.example` y `compose.yaml`, pero `DataSeeder` **nunca la lee**. La credencial
  documentada (`admin@control-elector.local`) nunca existio; el admin real es
  `admsismas@control.ttcs`.
- `002-rbac-seed.sql` ya anticipo esta discusion y documento una "Variante B" — sembrar el
  admin en SQL con un hash pre-generado — marcadas "DESHABILITADO - documentacion" y
  "NO usar en prod". Este feature es esa variante, y S7 es la condicion que esa nota pide.

### L4 — 2026-10-22 — Estado real de las dependencias (para T1)

El modulo tiene **13 extensions de Quarkus**: REST, Jackson, Hibernate ORM Panache, JDBC
PostgreSQL, Liquibase, Hibernate Validator, SmallRye Health, SmallRye OpenAPI, AWT,
Agroal, Narayana JTA, Caffeine y OpenPDF (quarkiverse).

Solo **3 dependencias directas no son de Quarkus**:

| dependencia | para que | hay extension equivalente |
|---|---|---|
| `com.nimbusds:nimbus-jose-jwt` | JWT | no |
| `software.amazon.awssdk:s3` | storage | `quarkus-amazon-s3`, descartada a proposito |
| `org.mindrot:jbcrypt` | hashing de password | no aplica, es una clase |

El pom documenta por que se descarta la extension de S3: "el backend Spring construye el
S3Client a mano y el contrato tiene que ser el mismo". Es una decision coherente con el
strangler y no se toca sin evidencia en contra.

Contexto que hay que tener presente al auditar: las dos veces que "se uso Quarkus en vez de
la libreria cruda" en este repo, el resultado fue **eliminar** una dependencia
(`uuid-creator` → `Style.VERSION_7`) o **hacerla explicita** (`ApacheHttpClient`), nunca
agregar una. Que T1 devuelva "no cambiar nada" es un resultado valido.

### L5 — 2026-11-15 — Auditoria de dependencias rehecha (T1)

La auditoria de L4 se escribio el 2026-10-22. Entre esa fecha y hoy entraron cuatro
dependencias nuevas por features que llegaron despues (invitaciones, compliance de
monitoring y de rate limiting), asi que el numero de L4 quedo viejo. La auditoria se
volvio a correr contra el pom real.

**Extensions de Quarkus declaradas: 11 directas** (10 de `io.quarkus` + 1 de
`quarkiverse`). L4 contaba 13 porque incluia cuatro que hoy llegan **transitivas** y no
declaradas: AWT, Agroal, Narayana JTA y Caffeine.

| extension | para que |
|---|---|
| `quarkus-rest` | REST |
| `quarkus-rest-jackson` | Jackson |
| `quarkus-hibernate-orm-panache` | ORM Panache |
| `quarkus-jdbc-postgresql` | JDBC PostgreSQL |
| `quarkus-liquibase` | Liquibase |
| `quarkus-hibernate-validator` | Bean Validation |
| `quarkus-smallrye-health` | Health en `/actuator/health` |
| `quarkus-smallrye-openapi` | OpenAPI |
| `quarkus-mailer` | SMTP (nueva, T-AUTH-INV-01) |
| `quarkus-redis-client` | Redis (nueva, T-COMPLIANCE-06) |
| `quarkus-openpdf` (quarkiverse) | PDF del expediente |

**Dependencias directas no-Quarkus: 5** (L4 decia 3).

| dependencia | para que | hay extension equivalente | veredicto |
|---|---|---|---|
| `com.nimbusds:nimbus-jose-jwt` | JWT | no | se queda |
| `software.amazon.awssdk:s3` | storage | `quarkus-amazon-s3`, descartada a proposito | se queda |
| `software.amazon.awssdk:apache-client` | cliente HTTP del SDK | n/a (es una clase) | se queda |
| `org.mindrot:jbcrypt` | hashing de password | no | se queda |
| `io.sentry:sentry` | error tracking | **no**: `quarkus-sentry` no esta en el BOM 3.40.1 | se queda |

Dos se eliminaron. Eran **peso muerto**, no alternativas a una extension:

- **`io.sentry:sentry-logback`** — Quarkus no usa Logback como backend de logging (usa
  JBoss Logging), asi que el appender de Logback no tendria donde engancharse. Y la
  dependencia tampoco arrastra Logback: su pom solo declara el SDK, asi que el appender
  queda sin su clase base. Verificado en el arbol de dependencias: `ch.qos.logback:*` no
  aparece. Cero referencias en el codigo.
- **`com.github.vladimir-bukhtoyarov:bucket4j-core`** — `RateLimitingService` implementa
  la ventana fija a mano sobre `RedisDataSource`. Cero imports de `io.github.bucket4j` en
  `src/` (main y test). El javadoc del servicio decia "Usa Bucket4j"; el codigo nunca lo
  uso. Se corrigio el javadoc tambien, porque un comentario que miente es un defecto.

Sobre el S3: la decision de no usar `quarkus-amazon-s3` sigue siendo coherente con el
strangler y no se toca sin evidencia en contra. El `apache-client` es la version
*explicita* de la misma politica: el artifact ya venia transitivo y se declara para poder
referenciarlo desde el codigo sin depender del ServiceLoader.

Resultado: **18 dependencias directas de compile -> 16**. Once son extensions y cinco son
librerias crudas que no tienen alternativa en la plataforma.

### L6 — 2026-11-15 — Toolchain Java 25 verificado (T2)

T2 ya venia aplicado desde el PR #152. Se reverifico cada superficie contra el repo:

| superficie | estado |
|---|---|
| `backend/electoral-api-quarkus/pom.xml` | `maven.compiler.release=25` |
| `backend/contract-tests/pom.xml` | `maven.compiler.release=25` |
| `.github/workflows/ci.yml` | 4 jobs con `java-version: '25'` |
| `.github/workflows/ci-native.yml` | 1 job con `java-version: '25'` |
| `.github/workflows/load-test-baseline.yml` | 1 job con `java-version: '25'` |
| `Dockerfile` | builder `maven:3.9-eclipse-temurin-25`, runtime `eclipse-temurin:25-jre-noble` |
| `Dockerfile.native` | `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25` |

El task decia "5 jobs de CI". Hoy son **6**: entre la fecha del ODD y ahora se sumo el
job de `load-test-baseline.yml`, que tambien compila y arranca el backend.

Un comentario quedo viejo y se corrigio: `Dockerfile.native` decia que la imagen JVM
era `maven:3.9-eclipse-temurin-21` cuando hace tiempo que es `-25`. No cambiaba el
build —es un comentario— pero un comentario que describe la imagen equivocada manda
a diagnosticar el error equivocado.

Sobre **S9** (mover a Java 25 no rompe la suite ni el AOT): el modulo compila y corre
la suite unitaria sobre `openjdk 25.0.5` (83 tests, 0 fallos). El AOT nativo no se puede
construir fuera de CI: el `native-image` pide 6-8 GB de RSS y la imagen de builder no
corre sin Docker. Lo construye el job `Backend Native Image`, que ya usa el builder de
Mandrel 25.

### L7 — 2026-11-15 — El admin de arranque se creaba en produccion (T3)

La migracion `026-admin-inicial.sql` ya existia y el `DataSeeder` ya no estaba, pero
**S8 no se cumplia**: el admin de arranque, con su contrasena conocida y versionada, se
seguia creando en staging y produccion.

La causa era el contexto. El admin vivia en `context: seed`, y el default de la app era
`quarkus.liquibase.contexts=${QUARKUS_LIQUIDASE_CONTEXTS:seed}`. Nadie seteaba la variable
—ni compose, ni k8s, ni railway.json—, asi que en todos los entornos corria `seed`, y con
el corria el admin. El comentario del propio changelog decia que la cuenta se podia
excluir con `QUARKUS_LIQUIDASE_CONTEXTS=vacio`.

**Eso es falso, y se verifico contra la API de Liquibase 5.0.3, no de memoria:** con
`Contexts` vacio, `ContextExpression.matches()` devuelve `true` para cualquier contexto.
Una lista de contextos de runtime vacia no excluye nada: corre TODO. Para excluir un
changeset hay que pasar una lista **no vacia** que no contenga su contexto.

Medido con una sonda que parsea el changelog y simula el gate:

| contextos de runtime | corren | se salta |
|---|---|---|
| (vacio) | 89 / 89 | — |
| `seed` | 88 / 89 | `026-01-admin-inicial` |
| `bootstrap-admin` | 87 / 89 | `025-01-data-seed-dev`, `034-01-k6-load-test-users` |
| `seed,bootstrap-admin` | 89 / 89 | — |

#### El arreglo

El admin recibe **su propio contexto**, `bootstrap-admin`, separado del `seed`:

- `026-admin-inicial.sql` pasa a `context: bootstrap-admin`.
- El default de la app sigue siendo `seed`: produccion corre los datos de referencia y
  **no** crea el admin. El default es fail-closed para la cuenta.
- `compose.yaml` (stack local) y los jobs de CI que inician sesion como MASTER piden
  `seed,bootstrap-admin`.
- `k8s/configmap.yaml` lo dice de forma explicita: `QUARKUS_LIQUIDASE_CONTEXTS: "seed"`.

El `seed` no se puede apagar en produccion: es el unico que trae los datos de referencia
que el producto necesita (cargos, partidos, candidatos, JRVs, geografia). Por eso el
admin necesitaba un contexto aparte, y no un "seed" apagado.

#### El checksum no se movio

Cambiar el `context` de un changeset **no** altera su checksum: el contexto es del
changeset, no del cambio, y el checksum se calcula sobre el SQL. Verificado: los 89
checksums del changelog quedaron **identicos** antes y despues.

Si habria que editar el *cuerpo* de un changeset ya aplicado, en cambio, el checksum
cambia y las bases ya migradas fallan la validacion. Por eso, al corregir comentarios
del `018-notification-seed.sql` y del bloque final del `002-rbac-seed.sql`, esas
ediciones se **revirtieron**: la sonda mostro que cambiaban el checksum de
`018-01-notification-seed` y de `002-02-rol-permiso`. Los comentarios que quedan
*antes* de la linea `-- changeset` si se pueden tocar sin riesgo. Es la misma politica
que el `027-repara-hash-cuentas-referencia.sql` documenta para el `025`.

#### Lo que tambien se limpio (T3)

- `EmailGenerator.generarBase()` era publico solo porque el `DataSeeder` lo usaba. Sin
  llamadores, paso a `private`.
- `APP_SEED_ADMIN_EMAIL` / `APP_SEED_ADMIN_PASSWORD` no los leia nadie: `DataSeeder` era
  el unico que las miraba y ya no existe. Se quitaron de `.env.example`, de `ci.yml`, de
  `ci-native.yml` y de `load-test-baseline.yml`.
- La documentacion que describia al `DataSeeder` y al email `admsismas@control.ttcs`
  (que no existia desde el corte a Quarkus) se actualizo en `documents/CREDENCIALES.md`,
  `documents/QA-BACKEND-VERIFICACION.md`, `backend/contract-tests/README.md` y los mocks
  de E2E.
- El test negativo de login de la suite de contrato usaba `admsismas@control.ttcs`: un
  email que no existe, asi que el 401 no probaba lo que decia probar. Ahora usa la
  cuenta real, para que el 401 sea por la contrasena y no por el usuario inexistente.

#### Lo que queda abierto, y NO es de este feature

El `025-data-seed.sql` siembra, en el mismo contexto `seed`, cinco cuentas de referencia
—`mastersolon@example.local` (MASTER), `coordinatorcortes@example.local`,
`adminpalma@example.local` y dos OBSERVER— con la contrasena `admin12345`, y el
`034-k6-load-test-users.sql` siembra veinte cuentas mas con `SecurePass123!`, una de ellas
ADMIN. En produccion, con `seed` corrido, esas cuentas tambien existen.

Eso es **la misma clase de defecto que S8**, pero de otro feature: son datos de
referencia y de load test, mezclados en changesets que ya corrieron en todas las bases.
Arreglarlo en serio pide partir el changeset `025` (que no se puede editar sin romper el
checksum), decidir que datos de referencia se quedan para produccion y borrar las
filas que ya quedaron — y borrar datos esta fuera de la frontera de esta rebanada, por
la misma razon que los 17 admins duplicados.

Queda como **hallazgo abierto**, con decision del operador: es un ODD propio, no un
detalle de este.

### L8 — 2026-11-15 — El filtro por path del job nativo, verificado (T4)

El filtro ya estaba aplicado desde `736980d`. Se reverifico el par completo —filtro
**y** regla de required— porque **S10** dice que se cambian juntos o no se cambian.

El filtro esta en `.github/workflows/ci-native.yml`, en `pull_request` y en `push`:

```
backend/electoral-api-quarkus/**   # el modulo: pom, src, Dockerfile.native, mvnw, .mvn
backend/contract-tests/**           # la suite que corre contra el binario nativo
.github/workflows/ci.yml
.github/workflows/ci-native.yml
```

No falta nada de lo que puede cambiar el resultado del build nativo: no hay pom
agregador en la raiz (los unicos dos poms estan bajo `backend/`) y el wrapper de
Maven vive dentro del modulo. El `paths` de `ci.yml` esta de mas —el workflow
`ci.yml` no interviene en el build nativo—, pero un filtro mas ancho solo cuesta
tiempo de CI; lo que no puede pasar es que falte algo.

El otro lado del par:

| workflow | filtro de paths en el trigger | checks requeridos |
|---|---|---|
| `ci.yml` | **NO** (el filtro va en el job `changes`) | si — 6 checks |
| `ci-native.yml` | si | **no** |

Por eso el par se sostiene. El `ci.yml` no puede filtrar en el trigger: un workflow
que no arranca deja sus checks en "Pending" para siempre y bloquea el PR. El
`ci-native.yml` si filtra, y a cambio no tiene ningun check requerido, asi que un PR
que no toca el backend no queda esperando un reporte que nunca llega.

Lo unico que **no** se pudo verificar desde el repo es el estado real del ruleset de
`develop` en GitHub: la API de rulesets responde 404 sin autenticacion de admin. La
afirmacion "el job nativo no es un check requerido" sale del header del propio
`ci-native.yml` y de la lista de los 6 checks requeridos que documenta `ci.yml`, no de
una lectura del ruleset. Si alguien lopromociona a requerido, hay que quitarle el
filtro en el mismo cambio.