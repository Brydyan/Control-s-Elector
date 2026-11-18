# Apply Progress — RLS real: rol de aplicación sin superusuario

**ODD:** `odd/tasks/rls-app-role.md`
**Rama:** `brydyan/sc-516/sdd-configuracion-geografica-jerarquica`
**Inicio:** 2026-11-17 · **Cierre:** 2026-11-17
**Estado:** ✅ COMPLETADO — T1–T7 ejecutados, con cuatro defectos de RLS que estaban dormidos.

---

## Tareas completadas

- [x] **T1) Rol de aplicación en el entorno de dev**
  - [x] `scripts/db/create-app-role.sql`: crea `electoral_app` con `NOSUPERUSER` y `NOBYPASSRLS`.
        Idempotente (CREATE si no existe + ALTER siempre). La contraseña llega por variable de
        entorno: **no está en el repositorio**.
  - [x] `compose.yaml`: servicio `db-app-role` que lo crea en cada arranque.
  - [x] `.env.example`: `DB_APP_USER` / `DB_APP_PASSWORD`, con el motivo de la separación.
  - [x] El backend de dev conecta con ese rol.
  - Commit: `258675f`

- [x] **T2) Grants y funciones `SECURITY DEFINER`**
  - [x] Changeset **036** (nuevo): `GRANT` de DML + `USAGE` de secuencias al rol, **guardado** por
        la existencia del rol, más `ALTER DEFAULT PRIVILEGES` para las tablas futuras.
  - [x] Changeset **036**: `get_current_user_id()` y `get_current_user_rol_name()` a `SECURITY
        DEFINER` con `search_path` fijo.
  - Commit: `e66cfcd`

- [x] **T3) Separar la migración del runtime**
  - [x] `quarkus.liquibase.username` / `quarkus.liquibase.password` (env `QUARKUS_LIQUIBASE_*`).
  - [x] `MigrateOnlyRunner`: con `APP_MIGRATE_ONLY=true` el proceso migra y sale con **código 0**
        (verificado con el jar empaquetado).
  - [x] `compose.yaml`: servicio `db-migrate` que migra con el dueño y termina; la API arranca
        después con el rol de aplicación y **sin** las credenciales del dueño.
  - Commit: `2763836`

- [x] **T4) CI con el rol de aplicación**
  - [x] Los tres workflows (`ci.yml`, `ci-native.yml`, `load-test-baseline.yml`) crean el rol y
        migran en un contenedor aparte.
  - [x] El backend entra con el rol de aplicación.
  - [x] En los jobs de arranque doble, el primer arranque migra con el dueño y el segundo valida
        el schema con el rol de aplicación (el control de drift sigue corriendo).
  - Commit: `0cd08cb`

- [x] **T5) k8s con el rol de aplicación**
  - [x] `k8s/secret.yaml`: `DB_APP_USER` / `DB_APP_PASSWORD` al lado del dueño.
  - [x] `k8s/configmap.yaml`: la migración queda OFF en la API.
  - [x] `k8s/backend.yaml`: initContainers `create-app-role` y `migrate` antes del contenedor
        principal. El contenedor de la API **no** lleva las credenciales del dueño.
  - [x] `k8s/kustomization.yaml`: el script SQL viaja como ConfigMap generado desde
        `scripts/db/create-app-role.sql` (una sola fuente de verdad).
  - Commit: `e52771e`

- [x] **T6) Pruebas que lo fijan**
  - [x] Entorno (CI): paso que **falla el build** si el rol es superusuario o tiene `BYPASSRLS`.
  - [x] Base (`RlsIsolationIT`): el contraste **0 sin contexto vs N con contexto**, y que el rol no
        es superusuario ni tiene `BYPASSRLS`.
  - [x] Camino de la app (contrato): el detalle del acta de otro observer responde **400 "no
        encontrada"** con RLS real y **403** con RLS inerte. Probado: contra el backend con
        superusuario el test falla.
  - Commit: `605914e`

- [x] **T7) Verificación integral**
  - [x] Suite del backend, suite de contrato **contra el backend con el rol de aplicación**,
        suite de frontend, lint y formato.
  - [x] `apply-progress.md` (este archivo).

---

## La evidencia

**Medido en dev, con el rol de aplicación** (`select count(*) from acta`):

| sesión | sin contexto | con contexto de ADMIN |
|---|---|---|
| `electoral_user` (superusuario, el de antes) | 206 | **206** → el contexto no cambia nada |
| `electoral_app` (el de ahora) | **0** | 206 → RLS real |

**La misma contraste, automatizada:** `RlsIsolationIT` afirma que la misma consulta da 0 sin
contexto y N con el contexto de un MASTER. Con el superusuario las dos dan N y el test falla:
quitar el `set_config` de la sesión ya no se puede esconder.

---

## Tests

**Backend (`backend/electoral-api-quarkus`, `mvn verify`):**
```
antes:  166 tests
después: 168 tests   (+2: el contraste 0/N y la aserción de atributos del rol)
resultado: ✅ 168/168 en verde
```

**Contrato (`backend/contract-tests`, contra el backend con el rol de aplicación):**
```
antes:  152 tests
después: 152 tests   (mismo número: 3 aserciones cambiadas, 1 prueba añadida y 1 eliminada)
resultado: ✅ 152/152 en verde
```

**Frontend (`pnpm test:ci`):** ✅ 1010 + 5 a11y, lint y prettier limpios (el ODD no toca frontend).

---

## Lo que RLS real destapó (4 defectos que dormían)

Con el superusuario, la capa RLS no se ejecutaba y estos huecos pasaban inadvertidos. Con un rol
sin privilegios, aparecieron:

1. **`notificacion` sin política de INSERT.** En Postgres, una tabla con RLS habilitada a la que no
   le aplica ninguna política para un comando lo **deniega**. El comentario del código
   (`ActaNotificacionService`) afirmaba lo contrario, y ese error era cierto solo para tablas sin
   RLS. Cada aviso a un observador moría con *"new row violates row-level security policy for
   table notificacion"*. → Changeset **037**.
2. **`evidencia` sin políticas de UPDATE ni DELETE.** El coordinator no podía cambiar el estado de
   una evidencia ajena y nadie podía borrarla (0 filas afectadas → 500). → Changeset **038**.
3. **`audit_log` y `dpo_contact_requests` con `IDENTITY`.** `IDENTITY` escribe
   `insert ... returning id`, y el `RETURNING` lo evalúa la política de **lectura**: la auditoría de
   un login fallido (sin actor) se filtraba y la escritura moría. Ambas entidades pasan a
   `SEQUENCE`: el id lo pide la base antes del INSERT y la escritura no depende de la política de
   lectura. Poder escribir en la auditoría no implica poder leerla.
4. **Dos casos de uso que escribían sin activar el contexto** (disputa abierta y acta anulada).

---

## Cambios de contrato (consecuencia de que RLS ahora filtre)

| Antes (RLS inerte) | Ahora (RLS real) | Por qué |
|---|---|---|
| Otro observer al pedir el detalle del acta de alguien: **403** "Solo puedes consultar tus propias actas" | **400** "Acta no encontrada" | Con RLS la fila no le es visible: la app no puede ni compararla. Deja de confirmarse que el acta exista. |
| Otro observer al marcar observaciones de una evidencia ajena: **400** "solo quien la subió puede marcarla" | **400** "no encontrada" | Mismo motivo. El status no cambia; cambia el mensaje. |
| El 403 de MASTER, COORDINATOR y ADMIN sobre el acta ajena | **igual** | A esos roles las policies los dejan ver la fila: la app los rechaza por permisos, como antes. |

Los tres cambios están en `backend/contract-tests` (`ObserverActasContractIT`,
`RbacContractIT`, `EvidenciasContractIT`).

---

## Desviaciones del ODD

1. **La migración no corre en un paso de CI o de ArgoCD, sino en un initContainer del mismo
   Deployment (k8s) y en un servicio `db-migrate` (compose).** La razón: el binario nativo no trae
   `psql` ni Liquibase CLI, así que un paso aparte tendría que ser el mismo binario de la API.
   Con el initContainer se cumple lo importante: **el contenedor de la API no tiene las
   credenciales del dueño**.
2. **Se añadió el modo `migrate-only` (una clase y un flag), que el ODD no detallaba.** Sin él no
   había forma de que un contenedor migrara y terminara: el proceso sirve para siempre.
3. **La contraseña del rol de aplicación en CI es texto plano en el workflow.** Es la base efímera
   de CI, no un secreto de producción, y es lo mismo que hace el repo con la contraseña del dueño.
   En k8s y en dev viene del Secret / de `.env`.

---

## Commits

| Commit | Qué |
|---|---|
| `258675f` | T1 — rol de aplicación en dev (script, compose, `.env.example`) |
| `e66cfcd` | T2 — changesets 036: grants + funciones `SECURITY DEFINER` |
| `2763836` | T3 — migración separada del runtime + modo `migrate-only` |
| `bbb37c2` | Los 4 defectos de RLS destapados (changesets 037 y 038, entidades, contextos) |
| `ed41cf1` | La suite de contrato, adaptada al aislamiento real |
| `0cd08cb` | T4 — los tres workflows de CI con el rol de aplicación |
| `e52771e` | T5 — k8s: secret, configmap, initContainers |
| `605914e` | T6 — las tres pruebas que fijan el rol |

---

## Lo que queda

1. ⚠️ **El `Secret` de k8s tiene la contraseña del rol de aplicación en texto plano** (`stringData`).
   Es como está el resto del Secret del repo, pero conviene rotarla en el cluster real antes de
   aplicar el manifiesto.
2. ⚠️ **La base de desarrollo necesita el rol creado a mano** una vez (`docker compose up
   db-app-role`), o el arranque de la API falla con "password authentication failed".
3. ⚠️ **El changeset `025-01-data-seed-dev` sigue con el checksum editado** (ajeno a este ODD): el
   backend no arranca contra bases migradas con la versión anterior. Necesita un changeset nuevo.
4. ➡️ **Revisión** con `01-qa-review` / `03-verify-odd` sobre `odd/tasks/rls-app-role.md`.
