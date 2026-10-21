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
| T1 | Mover `database/` al módulo Quarkus; sacar el `copy-resources` del pom, el `COPY` del Dockerfile y volver el contexto de build al módulo | S6,S7 | inline | — |
| T2 | `compose.yaml`: build del módulo, variables `QUARKUS_*`, `migrate-at-start` y arranque en dos fases | S5,S10,S11 | inline | — |
| T3 | `.env` y `.env.example`: las variables de Spring que ya no aplican, las de Quarkus que sí | S5 | inline | — |
| T4 | `ci.yml`: borrar los jobs de Spring (líneas 43, 60, 133-178) y ajustar el contexto del build Quarkus | S5,S6 | inline | — |
| T5 | `deploy.yml` y `railway.json`: que se construya y publique la imagen de Quarkus | S5,S6 | inline | — |
| T6 | `k8s/`: `SERVER_PORT` del probe y `migrate-at-start` en el ConfigMap | S5,S10 | inline | — |
| T7 | Docs: `README.md` (líneas 36, 117, 119, 143), README del módulo (sección "Lo que falta" desactualizada), `.claude/skills/control-electoral-review/SKILL.md` | S5 | inline | — |
| T8 | Borrar `backend/electoral-api/` completo | S5 | inline | — |
| T9 | Verificación end-to-end con Spring ya borrado: suite de contrato contra la imagen Quarkus, base limpia en dos fases, `mvn verify` del módulo | S9,S11 | inline | — |
| T10 | PR encadenado sobre `test/backend-quarkus-coverage` + entrada L16 en este documento | S1,S2 | inline | — |

**Estado:** sin empezar.

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