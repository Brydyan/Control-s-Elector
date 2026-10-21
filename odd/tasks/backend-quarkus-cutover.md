# Corte de runtime a Quarkus y eliminación del backend Spring

Fase 3 del strangler: la migración de código ya está hecha y verificada (PR #139, 122/122 del
contrato contra las dos implementaciones). Este feature hace el corte de runtime real —compose,
CI, CD, k8s, docs— y elimina `backend/electoral-api/`. El paso de JVM a GraalVM nativo no entra
acá: es otro PR, después de #114.

## Specs

Las cadenas del operador están textuales. No se reinterpretan.

- **S1** — "si quisiera un pr, en el stack pr de los prs que dicen (backend), sore quarkus, para migrar todo a quarkus y que ya no tengmaos el backend de la JVM"
- **S2** — "este pr entra como un stack pr en la cadena de prs de backend"
- **S3** — "si de momento seria quarkus con jvm, y eliminar tanto electoral-api y como backend/electoral-api no?, y luego en otro pr mas adelante seria pasar de jvm a graalvm"
- **S4** — "Dentro de electoral-api-quarkus" (dueño del changelog de Liquibase)
- **S5** — El runtime local, CI, CD y k8s pasan a Quarkus. `backend/electoral-api/` deja de existir en el repositorio.
- **S6** — El contexto de build de la imagen del backend es el módulo `backend/electoral-api-quarkus/`, no `backend/`: la imagen no puede depender de que exista un módulo hermano.
- **S7** — `backend/electoral-api-quarkus/src/main/resources/database/` es el único dueño del changelog de Liquibase. Desaparece el `copy-resources` del `pom.xml` y el `COPY` del `Dockerfile`.
- **S8** — La imagen y el despliegue siguen exponiendo `/actuator/health` en el puerto principal: los probes y el proxy no cambian de contrato.
- **S9** — La suite de contrato (`backend/contract-tests/`) sobrevive a la eliminación de Spring y sigue siendo el gate: se ejecuta contra Quarkus.

### Specs derivadas de la evidencia (no del pedido, pero el corte no es correcto sin ellas)

Estas no son requisitos inventados: son el comportamiento que Spring tenía y Quarkus no, y que el
operador pidió preservar al decir "migrar todo". Cada una es una regresión si se ignoran.

- **S10** — Quarkus debe aplicar las migraciones al arrancar. Spring lo hacía siempre (`spring.liquibase.enabled=true`); Quarkus tiene `quarkus.liquibase.migrate-at-start=${QUARKUS_LIQUIDASE_MIGRATE_AT_START:false}` en OFF por defecto porque durante el strangler migraba Spring. Con Spring borrado y esto en OFF, un ambiente nuevo queda sin schema.
- **S11** — La primera vez contra una base vacía el arranque tiene que ser en dos fases: Liquibase crea el schema, Hibernate solo valida después. Con `quarkus.hibernate-orm.database.generation=validate` la base vacía revienta con `Schema validation: missing table [acta]`, porque la validación corre antes que la migración. Es el mismo bug que se corrigió en el job de CI (`758d80d`).
- **S12** — El health del backend Quarkus se sirve en `/actuator/health` del puerto principal por `shared/web/HealthAliasResource`, porque `quarkus.management.enabled=false` deja el management interface en su puerto propio (9000). El `HEALTHCHECK` del Dockerfile, el `healthcheck` de compose y los probes de k8s siguen apuntando a `/actuator/health` y no cambian.

## Tasks

| ID | Qué | Specs | Ruta | Commit |
|---|---|---|---|---|
| T+ | Adoptar `spotless` en el módulo Quarkus para no perder el gate de formato | S5 | inline | `ec679fb` |
| T1 | Mover `database/` al módulo Quarkus; sacar el `copy-resources` del pom, el `COPY` del Dockerfile y volver el contexto de build al módulo | S6,S7 | inline | `0e2324b` |
| T2 | `compose.yaml`: build del módulo, variables `QUARKUS_*`, `migrate-at-start` y arranque en dos fases | S5,S10,S11 | inline | `3a076d4` |
| T3 | `.env` y `.env.example`: las variables de Spring que ya no aplican, las de Quarkus que sí | S5 | inline | `3a076d4` |
| T4 | `ci.yml`: borrar los jobs de Spring y ajustar el contexto del build Quarkus | S5,S6 | inline | `7691a36` |
| T5 | `deploy.yml` y `railway.json`: que se construya y publique la imagen de Quarkus | S5,S6 | inline | `7691a36` |
| T6 | `k8s/`: `APP_JWT_SECRET`, `APP_JWT_REFRESH_PEPPER` y `migrate-at-start` en el ConfigMap | S5,S10 | inline | `7691a36` |
| T7 | Docs: `README.md`, README del módulo, `.claude/skills/`, y los seis runbooks de `documents/` | S5 | inline | `6ec0876`, `6b16a6e` |
| T8 | Borrar `backend/electoral-api/` completo | S5 | inline | `357cdd4` |
| T9 | Verificación end-to-end con Spring ya borrado | S9,S11 | inline | — |
| T10 | PR encadenado sobre `test/backend-quarkus-coverage` + entrada L16 | S1,S2 | inline | — |

**Estado:** T1-T9 hechas. T10 en curso.

### T9 — Verificación ejecutada (no declarada)

| Qué | Resultado |
|---|---|
| `mvn -B clean verify` en el módulo Quarkus | **BUILD SUCCESS** — 4/4 tests, spotless limpio en 174 archivos. Es el comando exacto del job `Backend Quarkus Tests`. |
| `docker build backend/electoral-api-quarkus` (contexto = módulo) | **SUCCESS**. El changelog viaja dentro de la imagen: 33 entradas en `database/` del jar de la app. |
| Arranque en dos fases contra base VACIA | 0 tablas → arrancada con `none` → **25 tablas, 64 changesets**, saludable → segunda fase con `validate` (los defaults, sin tocar nada): **OK**. Health `UP`. |
| Suite de contrato contra esa imagen | **122/122, BUILD SUCCESS**. Con `backend/electoral-api/` ya borrado del repo. |
| `docker compose config` + YAML de CI/CD/k8s | Parsean. |

**Dos corridas del harness con S3 roto, y por qué no cuentan como fallo del PR.** Los primeros
dos intentos dieron 22/122 en rojo: los 22 eran de evidencia (`subirEvidenciaValida` → 500) porque
el script no habia creado el bucket de RustFS. Ninguno era de otro modulo. La causa se confirmo
antes de tocar nada: la 22ava falla era la unica de `CoordinatorActasContractIT`, y todas las de
`EvidenciasContractIT` + `RbacContractIT` pasaban por la misma subida. Corregido el harness (bucket
con el mismo metodo que usa el job de CI, `amazon/aws-cli s3api create-bucket`), 122/122.

### Frontera de la rebanada

El guard de commits mide el diff contra `origin/develop`, que para esta rama es el diff de
toda la cadena (#139 + #140 + este PR) — de ahi el aviso de "17000 lineas". **El diff de este
PR es contra su base, `test/backend-quarkus-coverage`**: 8 commits. La cadena se revisa por
PR, no por rama, y cada PR se mira contra el suyo.

---

## Log

### L1 — 2026-10-21 — Pedido

> si quisiera un pr, en el stack pr de los prs que dicen (backend), sore quarkus, para migrar todo a quarkus y que ya no tengmaos el backend de la JVM

### L2 — 2026-10-21 — Tres decisiones tomadas antes de escribir una línea

1. **Runtime:** Quarkus en modo JVM. El nativo no entra en este PR; el paso a GraalVM va después,
   cuando #114 esté resuelto. Motivo: el bloqueo es un problema de investigación sobre
   OpenPDF/FOP, no de refactor, y meterlo acá agranda el PR y sube el riesgo del corte.
2. **Dueño del changelog:** dentro de `electoral-api-quarkus`. Se descarta el módulo compartido
   `backend/db-changelog`: con un solo consumidor es indirección sin destino.
3. **Posición en el stack:** tercer PR de la cadena de backend, base `test/backend-quarkus-coverage`
   (la punta de #140). El orden queda #139 → #140 → este.

**Lo que el pedido NO decide y queda para el operador:** `deploy.yml` dispara en cualquier push a
`develop` que toque `backend/**` y publica `control-electoral-backend:latest` sin gate humano. Cuando
esta cadena aterrice en `develop`, el CD publica la imagen de Quarkus. Se deja anotado en el cuerpo
del PR; la política de CD es del operador, no de este feature.

### L3 — 2026-10-21 — Las cuatro trampas del corte (evidencia, no opinión)

La migración de código está verde, pero el corte de runtime tiene cuatro diferencias de comportamiento
con Spring que no aparecen en el diff del módulo Quarkus, porque el módulo se escribió assuming que
Spring seguía siendo el dueño del runtime:

1. **Liquibase en OFF (S10).** `application.properties:30` — `migrate-at-start=${...:false}`. El
   comentario lo dice: "Durante el strangler el backend Spring es el que migra". Con Spring borrado
   nadie migra. Una base nueva arranca sin schema.
2. **El arranque en dos fases (S11).** `application.properties:48` — Hibernate valida antes de que
   Liquibase cree. Contra base vacía: `Schema validation: missing table [acta]`. Ya se resolvió en el
   job de CI (`758d80d`); hay que replicarlo en compose y k8s.
3. **`validate-on-migrate` en OFF.** `application.properties:37` — otro default del strangler: el
   checksum de este cliente podía diferir del de Spring sin que importara. Con un solo cliente queda
   sin verificar que nadie edite un changelog ya aplicado.
4. **El management interface.** `application.properties:11-13` — `management.enabled=false` con puerto
   9000: las rutas de management NO se montan en el principal aunque se configuren así. Por eso existe
   `HealthAliasResource`. Los probes no se tocan, pero hay que saber por qué.

**Lo que se verificó que NO es problema:** `/actuator/health` existe en el puerto principal
(`shared/web/HealthAliasResource.java:25`) y `JwtAuthFilter.java:38` lo exime del token, así que
compose, k8s y el Dockerfile siguen funcionando sin cambio de contrato (S12).

**Lo que se verificó que es un no-op preexistente (no lo introduce este PR):** el ConfigMap de k8s
define `JWT_SECRET` y `CORS_ALLOWED_ORIGINS`, y ninguno de los dos backends lee esas dos variables
(Spring leía `app.jwt.secret`, Quarkus `APP_JWT_SECRET`; ninguno de los dos declara CORS). Se anotan
como observación, no como tarea: arreglarlo acá mezclaría el corte con una corrección de despliegue
que ya estaba rota antes.

### L4 — 2026-10-21 — Tres cosas que aparecieron al ejecutar, y no estaban en el plan

**1. El gate de formato se perdía en silencio (commit `ec679fb`).** `backend-format` corría
`spotless:check` con `working-directory: backend/electoral-api`. Borrar ese directorio no rompe el
job: lo deja apuntando a un path inexistente. Sin `spotless` en el pom de Quarkus, el backend se
quedaba sin control de formato y nadie lo notaba hasta que alguien metiera un archivo con el
formato mal.

El módulo Quarkus **no** estaba formateado: 83 archivos con violaciones, 343 líneas. Va en su
propio commit porque mezclado con el corte el diff deja de poder revisarse de un vistazo. El
primer conteo dio 4 archivos porque Maven trunca la salida de error del plugin; el número real
salió al ejecutar `spotless:apply`.

**2. El nombre de la variable del JWT en k8s era un agujero de seguridad (commit `7691a36`).** No
era un rename. Spring leía `app.jwt.secret=${JWT_SECRET:...}` y el ConfigMap de k8s definía
`JWT_SECRET`. Quarkus lee `app.jwt.secret=${APP_JWT_SECRET:dev-only-change-in-prod-32chars-min-jwt-hmac-key}`.
Con el nombre viejo, el backend **no habría fallado al arrancar**: habría firmado con la clave de
desarrollo embebida en el código. Una clave conocida, publicada en el repositorio, con la que
cualquiera que lo lea puede forjar tokens. `APP_JWT_REFRESH_PEPPER` faltaba en el ConfigMap y
tampoco lo leía Spring.

Esto generaliza: **el backend tiene un solo `application.properties`, sin perfil de prod**, así
que los defaults de desarrollo siguen disponibles en cualquier ambiente. En Spring el perfil
`prod` al menos exigía `DATABASE_URL` sin default. Queda anotado en `.env.example` y como
follow-up; no se arregla acá porque es endurecimiento de configuración, no corte de runtime.

**3. Tres comandos de los runbooks no existen en Quarkus (commit `6b16a6e`).** Un rename de rutas
no alcanzaba: `jacoco` (el pom de Quarkus no instrumenta cobertura), `dependency-check:check` (no
está configurado) y `mvn liquibase:changelogSync` (el goal no existe; en Quarkus las
migraciones corren al arrancar). Los runbooks seguian mandando a correrlos.

### L5 — 2026-10-21 — Lo que NO se tocó, y por qué

- **`documents/task-control/`** (8 archivos): son el registro de trabajos completados, con las
  rutas que tenían cuando se hicieron. Reescribirlos sería falsear el historial. Some ejemplos
  obsoletos que se van a quedar: `documents/PLAN.md`, `documents/diagram/README.md`,
  `documents/DOCKER.md:16` (menciona "JRE Alpine + JAR ejecutable" en un párrafo que sí se
  actualizó), `backend/contract-tests/README.md:108` ("que se portará al proyecto Quarkus" — ya
  se portó, en #140).
- **`validate-on-migrate` sigue en `false`**: el `DATABASECHANGELOG` de los ambientes existentes lo
  escribió el cliente Liquibase del backend Spring (5.0.3). La imagen de Quarkus trae
  `org.liquibase.liquibase-core-4.33.0` — no es el mismo cliente ni de la misma major. Encender la
  validación en el mismo PR que cambia el runtime le pediría a producción verificar checksums
  contra un cliente que nunca los escribió. Se enciende después, cuando el schema esté re-baselined
  por Quarkus.
- **El `HEALTHCHECK` del Dockerfile sigue apuntando a `:8080`** mientras el ConfigMap de k8s pone
  `SERVER_PORT: "8081"`. Preexistente: k8s no usa el `HEALTHCHECK` de la imagen (no hay
  `readinessProbe` ni `livenessProbe`, solo el Service). Se deja como está.
- **`CORS_ALLOWED_ORIGINS` en el ConfigMap**: ni Spring ni Quarkus lo leen. Preexistente.
- **`backend-quarkus` y `backend-contract` construyen la misma imagen** en dos jobs distintos. Es
  trabajo duplicado de CI (~2 min), no un error. Consolidarlos es otro PR.
- **`backend/electoral-api/build/`** sigue en disco: son 494 archivos gitignorados propiedad de
  otro usuario (de un build en contenedor), asi que `rm -rf` da "permiso denegado". Git no los
  trackea y el PR es correcto; se borran con `sudo rm -rf` cuando quiera el operador.