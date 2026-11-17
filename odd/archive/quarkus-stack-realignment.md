# Realineación del stack de PRs Quarkus (#150 y #151)

Los dos PRs del corte a Quarkus quedaronapilados sobre ramas que implementaron **la misma
migración por separado**, así que git ve dos implementaciones paralelas yPR #150 arrastra 296
archivos en conflicto. Este feature reapila el stack: reconstruye #150 con su trabajo real y
vuelve a colgar #151 encima.

## Specs

Las cadenas del operador son textuales. No se reinterpretan.

- **S1** — "ok mira, vamos primero a revisar el pr 151 que es la migracion a quarkus para terminar de hacer eso y ahi seguimso arreglando otras cosas ok ?"
- **S2** — "el 150 es el que mas conflictos tiene necesitamos arreglar ese y subir al 151"
- **S3** — "Sí, rebasear los 10 únicos"
- **S4** — "Investigar antes de decidir"

### Specs derivadas de la evidencia

No son requisitos inventados: son las condiciones que hacen que el reapilado sea correcto. Cada
una es una regresión si se ignoran.

- **S5** — `feat/backend-quarkus-cutover` se reconstruye sobre `test/backend-quarkus-coverage`
  conservando **solo** sus commits únicos. Los 32 commits con patch-id idéntico a la base se
  descartan: no aportan nada que la base no tenga ya.
- **S6** — El contenido de los 10 commits únicos se preserva. Un cherry-pick que需要 resolver
  porque el contexto cambió se documenta; no se descarta en silencio.
- **S7** — La reescritura de historia de las ramas publicadas se hace con `--force-with-lease`,
  nunca con `--force`, y sobre un respaldo local previo.
- **S8** — `feat/backend-native-image` (#151) queda apilada sobre el #150 reconstruido. El
  fallo `Backend Quarkus Tests` (Spotless: `No such reference 'origin/develop'`) lo causa que
  #151 ramificó de un #150 anterior al commit `289b9bb`, que ya lo arregla: reapilar lo resuelve
  sin tocar el workflow.
- **S9** — El único arreglo de código propio que #151 necesita es la imagen base del runtime
  nativo. `quay.io/quarkus/ubi-quarkus-runtime-image:jdk-21` **no existe** (el repo no es
  público y el tag no está); el build muere con `401 UNAUTHORIZED` antes de compilar.
- **S10** — La imagen base del runtime nativo debe cumplir lo que el propio `Dockerfile.native`
  afirma en su comentario y nunca se verificó: traer **shell** y **curl** (el `HEALTHCHECK`
  usa `curl -sf`), **sin JDK**, y ser **UBI 9** (Quarkus ≥3.19 produce ejecutables glibc UBI9; en
  UBI8 no corren). La decisión se toma sobre evidencia ejecutada, no sobre el comentario.

## Tasks

- **T1** — Reconstruir `feat/backend-quarkus-cutover` sobre `test/backend-quarkus-coverage` con los 10 commits únicos (cherry-pick). Ruta: inline. Sin commit propio: el resultado son los commits reapilados. (S3, S5, S6)
- **T2** — Verificar que #150 queda sin conflictos y hacer push con `--force-with-lease`. Ruta: inline. (S7)
- **T3** — Verificar empíricamente (curl/shell/JDK/UBI9) las imágenes candidatas y corregir `Dockerfile.native`. Ruta: inline. (S4, S9, S10)
- **T4** — Re-apilar `feat/backend-native-image` (#151) sobre el #150 reconstruido. Ruta: inline. (S1, S8)
- **T5** — Push de #151 y verificación de CI. Ruta: inline. (S1)

### Frontera de la rebanada

Fuera de este feature: el login local (`DataSeeder` vs `APP_SEED_ADMIN_EMAIL`), el
`proxy.conf.json` que apunta a `:8081` donde nada escucha, y cualquier trabajo de frontend.
Son pendientes del operador, decisiones tomadas aparte, y no se mezclan con el reapilado: un
diff que mezcla migración de runtime con fix de login no es revisable.

## Log

### L1 — 2026-10-22 — Pedido

> "ok mira, vamos primero a revisar el pr 151 que es la migracion a quarkus para terminar de hacer eso y ahi seguimso arreglando otras cosas ok ?"

> "el 150 es el que mas conflictos tiene necesitamos arreglar ese y subir al 151"

### L2 — 2026-10-22 — La causa de los 296 conflictos

El merge-base entre `test/backend-quarkus-coverage` y `feat/backend-quarkus-cutover` es
`98966ef`. Ambas ramas implementaron la migración Quarkus desde ese mismo punto, con hashes
distintos:

| Trabajo | commit en la base (#140) | commit en el head (#150) |
|---|---|---|
| modulo de auth completo | `66f174f` | `a3a471f` |
| modulo de actas | `274b9d3` | `eaa2366` |
| dashboard y evidencias S3 | `f02cf97` | `f942baa` |
| configuracion preelectoral y menus | `3cfdf3e` | `41b66c8` |
| foundation de autorizacion y datos | `840420f` | `828f1e1` |

`git cherry` confirma que son el **mismo patch**: 32 commits del #150 tienen patch-id idéntico a
uno de la base. Solo 10 son únicos:

```
0e2324b  que el modulo sea el dueno del changelog de Liquibase
ec679fb  adoptar spotless para no perder el gate de formato
3a076d4  que compose levante Quarkus
7691a36  que CI, CD y k8s apunten a Quarkus
6ec0876  docs: que la documentacion describa el backend que existe
6b16a6e  docs: que los runbooks apunten al backend que existe
357cdd4  borrar el backend Spring Boot          <- el nucleo (-27.887 lineas / 306 archivos)
e32402e  docs(odd): registrar la verificacion del corte (L6)
2e020f1  docs(odd): registrar la entrega del PR #150 (L16)
289b9bb  fix(ci): que el job de tests del modulo traiga el baseline de spotless
```

Por eso la estrategia no es resolver 296 conflictos a mano: es descartar los 32 duplicados y
quedarse con los 10 que sí aportan.

### L3 — 2026-10-22 — Los dos checks rojos de #151

- `Backend Quarkus Tests` → `No such reference 'origin/develop'`. Spotless corre con
  `ratchetFrom origin/develop` y el job hace checkout sin `fetch-depth: 0`, así que el
  baseline no existe. **Ya está arreglado en `289b9bb`, que pertenece al #150 y el #151 no
  tiene** porque ramificó antes. Reapilar lo resuelve sin editar el workflow.
- `Backend Native Image` → `401 UNAUTHORIZED` en
  `quay.io/quarkus/ubi-quarkus-runtime-image:jdk-21`. Ese repositorio no es público; todos sus
  tags devuelven 401. Es un nombre de imagen que no existe, no un tag vencido.

### L4 — 2026-10-22 — Estado previo de la rama

`feat/backend-native-image` (`feat/backend-quarkus-cutover`, tip `289b9bb`) tiene respaldo local
en `backup/quarkus-cutover-pre-rebase`.

### L5 — 2026-11-15 — Verificacion: el reapilado ya estaba ejecutado

Al abrir este ODD, las ramas sobre las que operan sus cinco tasks **ya no existian**: ni
`feat/backend-quarkus-cutover` (el #150), ni `feat/backend-native-image` (el #151), ni el
respaldo `backup/quarkus-cutover-pre-rebase`. La base del reapilado,
`origin/test/backend-quarkus-coverage`, sigue como ref remoto pero **no tiene ancestro
comun con HEAD**: es una linea de historia abandonada. El stack se reconstruyo, se mergeo,
y las ramas se borraron despues del merge.

Los 10 commits unicos de L2 no existen con esos hashes —los objetos ya no estan en el
repo— pero **si existen con hashes nuevos**, con los mismos mensajes y en el mismo orden.
Esa es la huella de una reescritura de historia, o sea del cherry-pick que T1 describe:

| L2 (hash viejo) | HEAD (hash nuevo) |
|---|---|
| `0e2324b` | `4ebb4ce` |
| `ec679fb` | `e8d5e35` |
| `3a076d4` | `b108ced` |
| `7691a36` | `63a8686` |
| `6ec0876` | `525f0e9` |
| `6b16a6e` | `83776a6` |
| `357cdd4` | `32156be` |
| `e32402e` | `6f2801e` |
| `2e020f1` | `74df4d7` |
| `289b9bb` | `2b39eb8` |

Los diez estan en la historia de HEAD, ninguno se discardo.

**T3 se volvio a correr hoy**, porque es el unico task con un artefacto verificable: la
evidencia se saco de la API HTTP de los registries, sin Docker (token anonimo → manifest
→ config → contenido de las capas).

- **S9 reproducido**: `quay.io/quarkus/ubi-quarkus-runtime-image:jdk-21` → HTTP 401;
  `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25` → HTTP 200.
- **S10 reproducido**: la imagen de runtime en uso (`registry.access.redhat.com/ubi9/ubi-minimal:9.7`)
  trae `curl`, `bash` y `sh`, y **no** trae `java`; la candidata descartada
  (`quay.io/quarkus/ubi9-quarkus-micro-image:2.0`) trae shell pero **no** trae `curl`,
  o sea que su `HEALTHCHECK` nunca habria pasado. Las dos afirmaciones que el comentario
  del `Dockerfile.native` hacia y nunca se habian medido, son ciertas.

Lo unico que queda **no verificable** es **S7** (el `--force-with-lease` y el respaldo
previo): el ref del respaldo ya no existe y el reflog local no lo conserva. No es que
haya fallado; es que no queda evidencia. Queda anotado como tal, no como cumplido.

La frontera de la rebanada, revisada: los dos puntos que el ODD dejaba afuera ya estan
cerrados. El login local (`DataSeeder` vs `APP_SEED_ADMIN_EMAIL`) lo cerro el ODD
`quarkus-deps-jdk25`, y el `proxy.conf.json` ahora apunta a `:8080`, no a `:8081`.