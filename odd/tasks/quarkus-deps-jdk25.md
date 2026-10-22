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