# ODD — RLS real: rol de aplicación sin superusuario (ARCHIVED)

**Verification Date:** 2026-11-18  
**Verifier:** Claude Code (03-verify-odd skill)  
**Status:** ✅ VERIFIED — PASS

> **Archivado:** ODD completado y verificado. Listo para merge.
> Este archivo fue `odd/tasks/rls-app-role.md`. Detalles del apply-progress en `VERIFIED-2026-11-18-rls-app-role.apply-progress.md`.

---

## Verificación Completa

### 1. Tareas: 7/7 ✅

- [x] **T1)** Dev: Script idempotente, compose.yaml, .env.example, backend con rol app
- [x] **T2)** Grants: Changesets 036 con IF EXISTS, funciones SECURITY DEFINER
- [x] **T3)** Separar migración: MigrateOnlyRunner, `migrate-at-start=false`, compose db-migrate
- [x] **T4)** CI: 3 workflows crean rol, validan atributos, corren con rol app
- [x] **T5)** k8s: Secret, ConfigMap, Deployment con initContainers create-app-role + migrate
- [x] **T6)** Tests: RlsIsolationIT verifica rolsuper/BYPASSRLS, contraste 0/N
- [x] **T7)** Verificación integral: Backend 168/168 ✅, Contract 152/152 ✅

### 2. Tests: 168/168 Backend + 152/152 Contract ✅

**Backend (`mvn verify`):**
- Antes: 166 tests
- Después: 168 tests (+2: contraste 0/N + atributos del rol)
- Resultado: **168/168 en verde**

**Contract Tests:** 152/152 ✅ (3 aserciones cambiadas: 403→400, net: 0)

**Frontend:** 1010 tests + 5 a11y ✅ (sin cambios)

**Regressions:** 0 ✅

### 3. Commits: 8/8 ✅

| Commit | Mensaje | Formato |
|--------|---------|---------|
| `258675f` | `feat(dev): rol de aplicacion sin superusuario para que RLS sea real` | ✅ |
| `e66cfcd` | `feat(db): permisos del rol de aplicacion y funciones de RLS SECURITY DEFINER` | ✅ |
| `2763836` | `feat(runtime): separar la migracion del runtime y el modo migrate-only` | ✅ |
| `bbb37c2` | `fix(rls): los huecos de escritura que la capa RLS estaba tapando` | ✅ |
| `ed41cf1` | `test(contrato): adaptar la suite al aislamiento real de RLS` | ✅ |
| `0cd08cb` | `ci: correr los tres jobs con el rol de aplicacion` | ✅ |
| `e52771e` | `feat(k8s): la API entra con el rol de aplicacion y migra en un initContainer` | ✅ |
| `605914e` | `test: fijar que el rol de la aplicacion no puede saltarse RLS` | ✅ |

**Co-autor tags:** ✅ 0 (ninguno)

### 4. Mutaciones: 5/5 ✅

- ✅ notificacion sin política INSERT (changeset 037)
- ✅ evidencia sin UPDATE/DELETE (changeset 038)
- ✅ audit_log y dpo_contact_requests con IDENTITY → SEQUENCE
- ✅ Contexto no activado en 2 casos
- ✅ Rol real filtra efectivamente (0 sin contexto, N con contexto)

### 5. Defectos Destapados (4)

Con RLS real (rol sin superusuario):

1. **notificacion sin INSERT policy** — Denegaba inserción, solo SELECT/UPDATE existían
2. **evidencia sin UPDATE/DELETE policies** — Denegaba cambios
3. **audit_log y dpo_contact_requests con IDENTITY** — RETURNING evaluaba lectura
4. **Dos casos sin contexto activado** — Disputa abierta, acta anulada

### 6. Cambios de Contrato

| Antes (RLS inerte) | Ahora (RLS real) | Razón |
|---|---|---|
| Observer pide acta ajena: **403** | **400** | Fila no visible con RLS |
| Cambiar evidencia ajena: **403** | **400** | Mismo motivo |
| MASTER/COORDINATOR sobre acta ajena | **igual** | Policies los dejan verla |

### 7. Desviaciones (documentadas)

| Desviación | Razón | Impacto |
|-----------|-------|--------|
| Migración en initContainer | Binario nativo sin psql | ✅ Objetivo cumplido |
| Modo `migrate-only` añadido | Necesario para términación | ✅ Pragmático |
| Contraseña CI en texto plano | Base efímera | ✅ Mismo que repo |

### 8. Archivos Modificados: 14

- Scripts: create-app-role.sql (nuevo)
- Compose: compose.yaml
- Config: .env.example, 3 changesets (036-038)
- Runtime: MigrateOnlyRunner.java
- CI/CD: ci.yml, ci-native.yml, load-test-baseline.yml
- k8s: secret.yaml, configmap.yaml, backend.yaml, kustomization.yaml
- Tests: RlsIsolationIT.java, ObserverActasContractIT.java

### 9. Evidencia de RLS Real

**Visibilidad de actas (dev):**

| Rol | sin contexto | con contexto |
|---|---|---|
| electoral_user (superusuario) | 206 | 206 (inerte) |
| **electoral_app** | **0** | 206 (real) |

---

## Veredicto Final

### ✅ PASS — Ready for Merge

**Criterios cumplidos:**
- ✅ 7/7 tareas
- ✅ 5/5 mutaciones
- ✅ 168/168 + 152/152 tests en verde
- ✅ 8/8 commits bien formados
- ✅ 4 defectos destapados y arreglados
- ✅ 0 blockers

**Próximos pasos:**
- Merge a develop
- Rotar contraseña del rol en Secret k8s (producción)
- Validar checksum del changeset 025 en próxima migración

---

**Verificado:** 2026-11-18 · Claude Code 03-verify-odd
