# ODD — RLS real: rol de aplicación sin superusuario

**Estado:** especificado (2026-11-16). Pendiente de implementación.
**Spec padre:** guardrail de `AGENTS.md` — *"RLS de PostgreSQL es capa obligatoria: un endpoint que
la salta se considera defecto y no excepción"* — y hallazgo 0 del verify de
`odd/tasks/dashboard-desglose-parroquias.md`.

---

## Por qué este trabajo

Hoy la capa RLS **no se ejecuta en ningún entorno del repo**: dev (compose), CI y k8s. No es un
bug de lógica: es que **el rol con el que conecta el backend es superusuario de Postgres**, y
Postgres no le aplica RLS a un superusuario.

### La cadena

| Eslabón | Qué pasa |
|---|---|
| Imagen de Postgres | `POSTGRES_USER=electoral_user` → ese rol es **superusuario** (`rolsuper=t`, `rolbypassrls=t`) |
| RLS de las tablas | `011-rls.sql` hace `ENABLE` + **`FORCE ROW LEVEL SECURITY`** |
| `FORCE` | ata al **owner** de la tabla, **no** al superusuario: el superusuario sigue viendo todo |
| El backend | `RlsContexto.activar(usuarioId)` ejecuta `set_config('app.current_user_id', …)` |
| El efecto | el contexto se activa, pero **no cambia ni una fila**: la consulta devuelve lo mismo con y sin él |

### La evidencia (medida en el entorno de dev, 2026-11-16)

| Rol | `select count(*) from acta` **sin** contexto | **con** contexto de ADMIN |
|---|---|---|
| `electoral_user` (superusuario, el que usa la app) | 206 | **206** → el contexto no cambia nada |
| `probe_app` (`NOSUPERUSER NOBYPASSRLS`) | **0** | 206 → RLS real |

### Por qué esto importa

1. **Una mutación pasa inadvertida.** Quitar `activarContexto()` del `DashboardService` **no cambia
   ningún número** en dev ni en CI: el contrato sigue verde con el contexto eliminado.
2. **El camino real de la app no prueba RLS.** La única prueba de aislamiento es
   `RlsIsolationIT`, que crea **su propio rol** sin privilegios. El camino de producción, con su
   propio datasource, no está probado.
3. **La política de insert de `acta` tampoco está probada.** Con el superusuario, un OBSERVER
   podría escribir una acta de una JRV que no es suya sin que la política lo dijera.
4. **No es solo dev/CI.** `k8s/postgresql.yaml` despliega `postgres:16-alpine` con
   `POSTGRES_USER` desde el Secret: **también es superusuario**. El hallazgo 0 del verify de
   `dashboard-desglose-parroquias` decía *"en prod el rol de Supabase no es superusuario"*, y en
   este repo el despliegue real de k8s **sí lo es**.

---

## Scope

**A. El rol de aplicación**
- Rol `electoral_app`: `NOSUPERUSER`, `NOBYPASSRLS`, `LOGIN`, con su contraseña **desde el Secret**
  (dev: `.env`; CI: variable del servicio; k8s: `k8s/secret.yaml`).
- `electoral_user` queda como **owner/superusuario** para migrar y sembrar.

**B. Los permisos (grants)**
- Changeset Liquibase nuevo (el siguiente número libre) que da DML al rol de app sobre las tablas
  de la aplicación y `USAGE` sobre las secuencias.
- **Guardado por la existencia del rol**: si el rol no existe (entorno que todavía no lo tiene),
  el changeset no falla y no deja el esquema a medias.

**C. Las funciones helper**
- `get_current_user_id()` y `get_current_user_rol_name()` pasan a **`SECURITY DEFINER`** con
  `search_path` fijo: la evaluación de la política no puede depender de que el rol de app tenga
  `SELECT` sobre `usuario` y `rol`.

**D. Datasource y Liquibase**
- El **pool de la app** conecta con `electoral_app`; **Liquibase** sigue con `electoral_user`.
  Quarkus usa un único datasource para ambos, así que la migración se separa (paso propio en CI y
  en el pipeline de ArgoCD) y la app arranca con `quarkus.liquibase.migrate-at-start=false`.

**E. Los entornos**
- `compose.yaml` (dev): crea el rol de app al inicializar la base.
- CI (`ci.yml`, `ci-native.yml`, `load-test-baseline.yml`): el servicio de Postgres crea el rol y
  los tests corren contra él.
- `k8s/`: el Secret gana `DB_APP_USER`/`DB_APP_PASSWORD`, el ConfigMap y el Deployment point to
  ese rol, y la migración corre en un paso previo.

**Fuera de scope:** cambiar las políticas RLS (ya están escritas y son correctas), el modelo
electoral, el seed, y el comportamiento del frontend.

---

## Specs

**S1 — El rol de aplicación no es superusuario.** `rolsuper = false` y `rolbypassrls = false` para
el rol con el que conecta la app, verificado con `pg_roles` en cada entorno.

**S2 — El contexto RLS cambia el resultado.** En dev y en CI, `select count(*) from acta` da **0**
sin contexto y **> 0** con el contexto del usuario. La prueba: el mismo SQL, dos conexiones, con y
sin `set_config`.

**S3 — La app sigue funcionando con RLS real.** Los caminos que escriben (`ActaService`,
`EvidenciaService`) activan el contexto **antes** de escribir; el dashboard lo activa antes de
leer. Con el rol de app, el suite completo sigue verde.

**S4 — Liquibase conserva el dueño.** El seed inserta filas en `acta`, una tabla con RLS forzado:
si corriera con el rol de app (que no es owner) la política de insert exigiría contexto de MASTER y
el seed fallaría. La migración corre con `electoral_user`.

**S5 — Ningún secreto versionado.** La contraseña del rol de app viene de `.env`/Secret/variable de
CI; **nunca** de un changelog, un manifest de k8s con `stringData` real ni del `.env` del repo.

**S6 — El camino real de la app prueba el aislamiento.** Además de `RlsIsolationIT` (rol propio),
un test comprueba que **la app** (con su datasource de aplicación) no deja ver actas de otro
observer.

**S7 — k8s usa el rol de aplicación.** El Deployment de la API conecta con `electoral_app`; la
migración va en un paso previo del pipeline de ArgoCD.

---

## Tasks

### T1 — Rol de aplicación en el entorno de dev
- `compose.yaml`: script de inicialización que crea `electoral_app` (`NOSUPERUSER NOBYPASSRLS`)
  con la contraseña de `${DB_APP_PASSWORD}`.
- `.env.example`: `DB_APP_USER` y `DB_APP_PASSWORD` (plantilla, sin valor real).
- El backend de dev conecta con el rol de app.

### T2 — Grants y funciones `SECURITY DEFINER`
- Changeset nuevo: `GRANT` de DML al rol de app + `USAGE` en secuencias, **guardado** por
  `IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'electoral_app')`.
- Changeset nuevo: `get_current_user_id()` y `get_current_user_rol_name()` a `SECURITY DEFINER`
  con `search_path` fijo.
- **Nunca editar un changeset ya aplicado** (cambia el checksum y el backend no arranca).

### T3 — Separar la migración del runtime
- `quarkus.liquibase.migrate-at-start=false` en el perfil de aplicación; la migración se ejecuta
  como paso propio (CI: paso antes de los tests; k8s: paso previo en ArgoCD).
- El usuario de migración es `electoral_user`; el de la app, `electoral_app`.

### T4 — CI con el rol de aplicación
- El servicio de Postgres de los tres workflows crea el rol de app antes de levantar el backend.
- Los tests de contrato y de integración corren **contra el rol de app**: es lo que hace que el
  camino de la app pruebe RLS.

### T5 — k8s con el rol de aplicación
- `k8s/secret.yaml`: `DB_APP_USER` / `DB_APP_PASSWORD` (placeholders).
- ConfigMap/Deployment: el `DB_USER` de la app pasa a ser el de aplicación; el de migración se
  queda aparte.
- Job/step de migración antes del rollout de la API.

### T6 — Pruebas que lo fijan
- Test de entorno que verifica `rolsuper`/`rolbypassrls` del rol de la app → falla si alguien
  degrada el rol a superusuario.
- Test que verifica el contraste **0 vs N** de S2 en el entorno de pruebas.
- Test de aislamiento **por el camino de la app** (S6), no solo por `RlsIsolationIT`.

### T7 — Verificación integral
- Suite completa del backend (unit + integración) con el rol de app.
- Suite de contrato completa contra la API con el rol de app.
- Evidencia de que la mutación "quitar `activarContexto()`" **ahora sí** cambia los números.

---

## Gate

```bash
# 1. El rol de la app no es superusuario
psql -c "select rolname, rolsuper, rolbypassrls from pg_roles where rolname = 'electoral_app';"
# esperado: rolsuper = f, rolbypassrls = f

# 2. El contexto RLS cambia el resultado (S2)
psql -c "select count(*) from acta;"                      # 0 (sin contexto)
psql -c "select set_config('app.current_user_id', '<admin_id>', false); select count(*) from acta;"  # > 0

# 3. La app sigue verde con RLS real
cd backend/electoral-api-quarkus && mvn -o -B -ntp verify
cd backend/contract-tests && mvn -o -B -ntp verify -Dcontract.baseUrl=<api>

# 4. La mutación ahora se nota
# quitar rls.activar(...) del DashboardService → los conteos cambian → el contrato falla
```

---

## Log

### L1 — 2026-11-16 — Especificación
Hallazgo 0 del verify de `dashboard-desglose-parroquias` convertido en ODD propio: el arreglo
cambia datasource, CI, compose y k8s, y no corresponde a un SDD de feature.
Medición de referencia (dev): superusuario 206 → 206 con y sin contexto; rol sin privilegios
0 → 206.
Decisión del humano: ODD de infra aparte, no mezclado con el SDD de geografía.
