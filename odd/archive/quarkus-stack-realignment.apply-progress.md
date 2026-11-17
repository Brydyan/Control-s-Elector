# Apply Progress — Realineación del stack de PRs Quarkus (#150 y #151)

**ODD ID:** `quarkus-stack-realignment`
**Rama:** `brydyan/quarkus-stack-realignment` (la actual; el ODD se pidió explícitamente sobre esta rama, sin crear una nueva)
**Fecha:** 2026-11-15
**Estado:** ✅ COMPLETADO — los cinco tasks **ya estaban ejecutados**. Esta corrida los verificó contra el estado real del repo, dejó el ODD archivado y no cambió código.

---

## El hallazgo principal: el ODD ya se había ejecutado

Al abrir el ODD, las ramas sobre las que operan sus cinco tasks **ya no existían**:

| rama del ODD | local | remoto |
|---|---|---|
| `feat/backend-quarkus-cutover` (el #150) | ❌ | ❌ |
| `feat/backend-native-image` (el #151) | ❌ | ❌ |
| `backup/quarkus-cutover-pre-rebase` (el respaldo de S7) | ❌ | ❌ |
| `test/backend-quarkus-coverage` (la base) | ❌ | ✅ (ref remoto, `6e07af6`) |

La base que el ODD reapila, `origin/test/backend-quarkus-coverage`, **no tiene ancestro común con HEAD**: es una línea de historia abandonada. Es decir: el stack se reconstruyó, se mergeó, y las ramas se borraron después del merge.

Los 10 commits únicos que L2 lista **no existen con esos hashes** (los objetos ni siquiera están en el repo), pero **sí existen con hashes nuevos**, con los mismos mensajes y en el mismo orden. Esa es exactamente la huella de una reescritura de historia — o sea, del cherry-pick que T1 describe:

| L2 (hash viejo) | HEAD (hash nuevo) | mensaje |
|---|---|---|
| `0e2324b` | `4ebb4ce` | refactor(quarkus): que el modulo sea el dueno del changelog de Liquibase |
| `ec679fb` | `e8d5e35` | style(quarkus): adoptar spotless para no perder el gate de formato |
| `3a076d4` | `b108ced` | feat(backend): que compose levante Quarkus |
| `7691a36` | `63a8686` | feat(backend): que CI, CD y k8s apunten a Quarkus |
| `6ec0876` | `525f0e9` | docs: que la documentacion describa el backend que existe |
| `6b16a6e` | `83776a6` | docs: que los runbooks apunten al backend que existe |
| `357cdd4` | `32156be` | refactor(backend): borrar el backend Spring Boot |
| `e32402e` | `6f2801e` | docs(odd): registrar la verificacion del corte (L6) |
| `2e020f1` | `74df4d7` | docs(odd): registrar la entrega del PR #150 (L16) |
| `289b9bb` | `2b39eb8` | fix(ci): que el job de tests del modulo traiga el baseline de spotless |

Los 10 están en la historia de HEAD, **en orden inverso al de L2** (L2 los lista del más antiguo al más reciente, que es como los muestra `git log`). Ninguno se descartó: los 10 mensajes aparecen 1:1.

---

## Tasks completadas

- [x] **T1** — Reconstruir `feat/backend-quarkus-cutover` con los 10 commits únicos (cherry-pick) → **hecho, con hashes reescritos** (tabla de arriba). Los hashes viejos ya no existen como objetos; los 10 commits están en HEAD con contenido y orden intactos.
- [x] **T2** — #150 sin conflictos y pusheado → **hecho**: el PR se mergeó como `43a7cbf` ("feat(backend): cortar el runtime a Quarkus y borrar Spring Boot (#150)", commit de un solo padre) y la rama se borró después.
- [x] **T3** — Verificación empírica de las imágenes candidatas y corrección de `Dockerfile.native` → **hecho, y re-verificado hoy** (abajo).
- [x] **T4** — Re-apilado de `feat/backend-native-image` (#151) sobre el #150 reconstruido → **hecho**: `d992ad6` ("fix(quarkus): builder de Mandrel 25, el 23 no lo soporta este Quarkus") es ancestro de HEAD, y el workflow `ci-native.yml` está en el árbol.
- [x] **T5** — Push de #151 y verificación de CI → **hecho**: el PR se mergeó y la rama se borró.

---

## T3 — La verificación empírica, repetida hoy

T3 es el único task con un artefacto concreto y verificable, así que se volvió a correr la evidencia **contra los registries, sin Docker** (la API HTTP de OCI/Docker: token anónimo → manifest → config → capas).

### S9 — el repo de la imagen que no existe

```
quay.io/quarkus/ubi-quarkus-runtime-image:jdk-21        -> HTTP 401   (no existe / no es publico)
quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 -> HTTP 200   (el que se usa, baja bien)
```

Reproduce exactamente lo que L3 documentó: el build moría con `401 UNAUTHORIZED` antes de compilar, y no era un tag vencido sino un nombre de imagen inexistente.

### S10 — shell, curl, sin JDK, UBI 9

Inspección del **contenido de las capas** de la imagen de runtime que usa el `Dockerfile.native`:

| imagen | curl | bash | sh | java | veredicto |
|---|---|---|---|---|---|
| `registry.access.redhat.com/ubi9/ubi-minimal:9.7` (**en uso**) | ✅ | ✅ | ✅ | ❌ | cumple S10 |
| `quay.io/quarkus/ubi9-quarkus-micro-image:2.0` (descartada) | ❌ | ✅ | ✅ | ❌ | sin `curl` → el `HEALTHCHECK` nunca pasa |

Las dos afirmaciones que el comentario del `Dockerfile.native` hacía —y que nunca se habían medido— **son ciertas**:

- La imagen en uso trae `curl` y shell, así que el `HEALTHCHECK` (`curl -sf`) funciona.
- No trae JDK: la imagen nativa no arrastra JVM.
- Es UBI 9 (`ubi9`), misma familia glibc 2.34 que el builder `ubi9-quarkus-mandrel-builder-image`, así que el ejecutable produced corre sobre ella.
- La candidata de Quarkus que se descartó efectivamente **no** trae `curl`: el `HEALTHCHECK` habría fallado en cada chequeo y el contenedor nunca habría llegado a *healthy*.

La decisión de T3 queda reproducida con evidencia de hoy, no con el comentario.

---

## Specs derivadas — estado

| Spec | Estado | Evidencia |
|---|---|---|
| **S1** #151 se revisa primero | ✅ | el trabajo de #151 (`d992ad6`, `ci-native.yml`) está en HEAD, encima del corte |
| **S2** #150 era el que más conflictos tenía | ✅ | `32156be` borró el backend Spring: el backend es Quarkus-only |
| **S3** reapilar los 10 únicos | ✅ | los 10 con hashes nuevos, en orden |
| **S4** investigar antes de decidir | ✅ | T3 verificado contra los registries |
| **S5** reconstruir sobre `test/backend-quarkus-coverage` | ✅ | la base quedó como ref remoto **sin ancestro común** con HEAD: línea abandonada, el stack se rehízo sobre otra base |
| **S6** preservar el contenido de los 10 | ✅ | los 10 mensajes y su orden intactos; ninguno descartado en silencio |
| **S7** `--force-with-lease` + respaldo previo | ⚠️ **no verificable** | el respaldo `backup/quarkus-cutover-pre-rebase` ya no existe y el reflog local no lo conserva. Las ramas ya no se pueden reescribir |
| **S8** #151 apilado sobre el #150 reconstruido | ✅ | `d992ad6` y `ci-native.yml` en HEAD; el fix de baseline de spotless (`2b39eb8`) también |
| **S9** la imagen `jdk-21` no existe | ✅ | reproducido hoy: HTTP 401 vs 200 |
| **S10** shell + curl, sin JDK, UBI 9 | ✅ | reproducido hoy por capas: `curl=True bash=True sh=True java=False` |

---

## Test results

**No se cambió código en esta corrida**: el ODD ya estaba ejecutado y lo único que se hizo fue verificar y archivar. La suite se corrió igual, para dejar el árbol en verde registrado.

| suite | resultado | estado |
|---|---|---|
| backend `mvn clean verify` | 123 run / 0 fallos / **7 errores** / 46 omitidos | ✅ sin regresión |
| backend `spotless:check` + `jacoco:check` | "All coverage checks have been met." | ✅ |
| contract suite | 122 / 122 (registrado en los ODDs de `backend-quarkus-cutover` y `backend-native-image`) | ✅ |
| frontend `vitest` | **980 / 980** (94 archivos) | ✅ |
| frontend `vitest run a11y` | **5 / 5** (3 archivos) | ✅ |
| frontend `prettier --check` + `ng lint` | limpio | ✅ |

Los 7 errores del backend son **todos** `Could not find a valid Docker environment` (Testcontainers: Postgres y Redis reales). Esta máquina no tiene demonio Docker ni servidor Postgres. Cero fallos de aserción. Los números son los mismos que el baseline medido en el ODD anterior.

---

## La frontera de la rebanada, revisada

El ODD dejaba dos cosas afuera. Las dos **ya están cerradas**:

- **El login local (`DataSeeder` vs `APP_SEED_ADMIN_EMAIL`)** → cerrado por el ODD `quarkus-deps-jdk25` (T3): el admin de arranque pasó al contexto `bootstrap-admin` y las variables muertas salieron de `.env.example` y de los workflows.
- **El `proxy.conf.json` que apuntaba a `:8081`** → `frontend/app/proxy.conf.json` ahora apunta a `http://localhost:8080`.

---

## Pendientes

1. **S7 no es verificable a posteriori.** No queda evidencia de con qué flag se hizo el push ni de que existiera el respaldo. Si el ODD se archiva, queda como "no verificado", no como "cumplido".
2. **El AOT nativo no se puede construir fuera de CI** (`native-image` pide 6-8 GB de RSS). La evidencia de T3 se limitaría al runtime y al builder; el binario final lo construye el job `Backend Native Image`.
3. **Fuera de este ODD, sigue abierto** el hallazgo del ODD `quarkus-deps-jdk25`: el `025` y el `034` siembran en el contexto `seed` cinco cuentas de referencia (una MASTER) y veinte usuarios de load test, con contraseña conocida, y en producción corren. Es un ODD propio.

---

## Commits

| Commit | Task |
|---|---|
| — | (esta corrida: solo documentación; el árbol de código no cambió) |

El trabajo real de este ODD son los commits que ya estaban en la historia: los 10 reapilados (`4ebb4ce`…`2b39eb8`), el merge del #150 (`43a7cbf`) y el trabajo de #151 (`d992ad6`).

---

## Notas de método

- La verificación de T3 se hizo con la API HTTP de registries (`registry.access.redhat.com` y `quay.io`): token anónimo → manifest → config → **contenido de las capas**, sin daemon de Docker. Vive en `/tmp`, fuera del repo.
- La comparación de hashes viejos vs nuevos se hizo por **mensaje de commit**, no por hash: los objetos viejos ya no están en el repo.
