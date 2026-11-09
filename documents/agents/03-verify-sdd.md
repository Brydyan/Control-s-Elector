# 03 — Verify SDD — Auditoría Formal de Software Design Document

**Skill:** `/03-verify-sdd <sdd-path> [round]`

**Función:** Verificar que una SDD fue implementada con exactitud, mutaciones incluidas.

**Subagente:** Nuevo cada vez (contexto limpio). Modelo: `sonnet`.

---

## Cuándo Lanzar `/03-verify-sdd`

1. **Builder entregó apply-progress.md** en openspec/changes/<área>/<fecha>-<slug>/
2. **Todas las tasks marcadas `- [x]`** en tasks.md
3. **Números medidos** — tests, errors, failures (de archivos XML, no de log)
4. **Mutaciones reportadas** — test exacto que cayó en cada una

**NO lanzar** si:
- apply-progress.md falta
- Tasks sin marcar `- [x]`
- Mutaciones dicen "test falló" sin nombre exacto

---

## Plantilla de Lanzamiento

**Ronda 1:**
```
Verificá la SDD openspec/changes/<área>/<fecha>-<slug>/.
Rama: <branch>, HEAD: <commit>, árbol limpio.

ANTES de nada, leé en orden:
  1. proposal.md
  2. design.md
  3. specs/*/spec.md
  4. tasks.md
  5. apply-progress.md (output de Builder)

## MATERIAL NUEVO DESDE LA RONDA ANTERIOR

(Ronda 1: "ninguno — es la primera verificación")

## TRAMPAS VERIFICADAS DE ESTE REPO
[pegar de README.md § Trampas]

## REGLAS DE MUTACIÓN — TÚ EJECUTAS
[pegar de README.md § Reglas de mutación]

## LAS PREGUNTAS QUE DECIDEN

Formular 3-6 preguntas, cada una con su mutación.
NO preguntas que se contesten leyendo código.

Ejemplos BIEN:
✅ "¿El SELECT FOR UPDATE NOWAIT realmente bloquea, o solo existe el decorador?"
   Mutación: quitar el SELECT FOR UPDATE NOWAIT → test de concurrencia cae?

✅ "¿La validación de RLS ocurre en BD, o el guardrail existe pero no se ejecuta?"
   Mutación: remover el set_config('app.current_user_id', ...) → INSERT sin validación?

## COMPUERTAS

Comandos EXACTOS que ejecutaste en la ronda anterior (para comparar):

Ronda 1:
cd backend/electoral-api-quarkus && mvn -B clean test > /tmp/r1.log
# Esperado: X suites / Y tests / Z errors / W failures
# (Sacados de target/surefire-reports/TEST-*.xml)

cd frontend/app && pnpm lint && pnpm test:coverage
# Esperado: lint ✅, coverage > 70%

## SALIDA

### verify-report.md
Qué ejecutaste, mutaciones aplicadas, números que contaste.

### fixes-required.md
SÓLO lo bloqueante (CRITICAL), con las cinco partes:
  1. Síntoma
  2. Causa raíz
  3. Reproducción (comando exacto)
  4. Fix propuesto
  5. Verificación de cierre
```

**Ronda 2 (si hay fixes):**
```
Verificá de nuevo openspec/changes/<área>/<fecha>-<slug>/.

## MATERIAL NUEVO DESDE LA RONDA ANTERIOR

Builder cerró estos fixes-required (ronda 1):
  - CRITICAL 1: faltaba índice en tabla usuario
  - CRITICAL 2: SQL injection en filtro de búsqueda

La rama ahora tiene commits <X>, <Y> que cierran ambos.

[Resto de plantilla igual]
```

---

## Fase 1: Authorize — Entiende el Cambio

**NO lances mutaciones aún.** Solo lee:

- ¿Qué promete proposal.md que va a cambiar?
- ¿Cuáles son las decisiones D1, D2, D3 en design.md?
- ¿Cuál es el criterio de aceptación en specs/?
- ¿Qué garantías declara tasks.md via mutaciones?

**Red flag:** Si proposal dice "mejorar login" pero design.md solo habla de CSS, algo no cierra.

---

## Fase 2: Audit Tasks Completadas

Comparar **lo que tasks.md pide** vs **lo que apply-progress.md reporta**:

```markdown
## Task A.1 — Crear tabla usuario

Pide (tasks.md):
  - [ ] Migración Liquibase: CREATE TABLE usuario (id, email, rol_id)
  - Mutación: quitar la fila en 001-schema.sql, test debe fallar

Reporta (apply-progress.md):
  - [x] **A.1** — Migración creada
  - Test: UserRepositoryTest.save() ✅ PASS
  - Mutación: "quitado CREATE TABLE. Test: UserRepositoryTest.save() cayó" ✅

Tu verificación:
  ✅ Task marcada [x]
  ✅ Test nombrado exactamente
  ✅ Mutación: test name reportado, debe caer cuando quitas línea
```

---

## Fase 3: Ejecutar Mutaciones (LA PARTE CRÍTICA)

**Nunca confiar en "el código se ve bien".** Ejecuta.

```bash
# Template: copiar línea EXACTA de la mutación, ejecutar

cd backend/electoral-api-quarkus

# M1: Quitar CREATE TABLE usuario
git stash
# Editar: 001-schema.sql, comentar CREATE TABLE usuario
mvn -B test -Dtest=UserRepositoryTest > /tmp/m1.log 2>&1; echo "exit=$?"
# Esperado: error "relation \"usuario\" does not exist" en logs
# Captura: grep "relation" /tmp/m1.log
git checkout 001-schema.sql
mvn -B test -Dtest=UserRepositoryTest > /tmp/m1-restore.log 2>&1; echo "exit=$?"
# Esperado: exit=0

## En apply-progress.md de Verify:
# M1: Quitar CREATE TABLE usuario
#   Test: UserRepositoryTest.save
#   Resultado: ✅ Cayó (relation "usuario" does not exist)
#   Restaurado: git checkout
```

**Si test NO cae cuando quitas la línea:**
→ El test es decorativo. Reportar en fixes-required.md como CRITICAL.

---

## Fase 4: Validar Compuertas

```bash
# Lint
cd frontend/app && pnpm lint
# Captura: output completo. Esperado: "0 warnings"

# Build
cd backend/electoral-api-quarkus && mvn -B clean package -DskipTests
# Esperado: BUILD SUCCESS

# Tests
cd backend/electoral-api-quarkus
# IMPORTANTE: levanta BD desde cero
docker compose down -v && docker compose up -d
# Espera ~30s
mvn -B test
# Captura: de target/surefire-reports/TEST-*.xml:
# grep -h 'tests=\|errors=\|failures=' target/surefire-reports/TEST-*.xml
```

---

## Patrones de Defecto — Qué Buscar

**1. Nadie recorre el camino**
- Frontend envía `CreateUserRequest` → Backend recibe `CreateUserDto`
- ¿Hay test que valida que ambos coinciden?
- Buscar: test de integración E2E o unit que llame ambos lados

**2. Regla en un sitio y no en el vecino**
- Endpoint A exige `@PreAuthorize("hasRole('ADMIN')")`
- Endpoint B similarmente accede datos ADMIN sin exigir
- Mutación: quitar autorizador en B → debe fallar test de RBAC

**3. El comentario que miente**
- Código: "/*RLS via set_config*/", pero no hay llamada a set_config
- Mutación: hacer INSERT sin set_config → debe fallar test de RLS

**4. Control que compara contra un nombre**
- `if (rol.getNombre().equals("MASTER"))` — pero tabla rol.nombre es editable
- Mutación: renombrar rol MASTER → control falla silenciosamente

**5. Depende de dónde corre**
- Migración: `INSERT INTO rol DEFAULT VALUES` — ok en PG, error en H2
- Test: corre en BD en memoria (H2), nunca en PG real
- Mutación: ejecutar en PG pura (`docker compose up db`)

**6. Compuerta sin garantía**
- CI: `mvn test -DskipTests` (¡ni ejecuta los tests!)
- Mutación: introducir syntax error en Java → CI sigue pasando

**7. Visual/UX defects** (si el change toca frontend)
- Validar con `/ux-designer` y `/taste-skill`
- Reportar hallazgos en sección "QA Visual" de verify-report.md

---

## verify-report.md — Estructura

```markdown
# verify-report.md

## Fecha
2026-11-08

## Verificador
[agent name]

## Autorización
Leída:
- ✅ proposal.md: cambio de X a Y
- ✅ design.md: decisiones D1, D2, D3
- ✅ specs/usuario/spec.md: 12 escenarios
- ✅ tasks.md: 8 tasks con mutaciones
- ✅ apply-progress.md: 8 tasks [x] todas

## Mutaciones Aplicadas

### M1 — Quitar CREATE TABLE usuario (task A.1)
- Cambio: comenté línea en 001-schema.sql
- Test afectado: `UserRepositoryTest.save`
- Resultado: ✅ **CAYÓ** (relation "usuario" does not exist)
- Restaurado: git checkout
- Conclusión: Garantía existe, test válido

### M2 — Remover set_config RLS (task B.2)
- Cambio: comenté línea en UserService.findById()
- Test afectado: `UserSecurityTest.getCurrentUserCannotSeeOtherUser`
- Resultado: ✅ **CAYÓ** (returned other user data)
- Conclusión: RLS ejecuta, control válido

### M3 — Cambiar default expiry 24h → 30d (task C.1)
- Cambio: edité user_invitations migration
- Test afectado: `UserInvitationTest.invitationExpiresAfter24Hours`
- Resultado: ✅ **CAYÓ** (expiry should be 24, got 720)
- Conclusión: Guardia existe

## Compuertas Ejecutadas

### Backend Tests
```bash
cd backend/electoral-api-quarkus
mvn -B test
```

Resultado (target/surefire-reports/):
- Suites: 23
- Tests: 456
- Errors: 0
- Failures: 0
- ✅ PASS

### Frontend Tests
```bash
cd frontend/app
pnpm test
```

Resultado:
- Tests: 89
- Failing: 0
- ✅ PASS

### Lint + Format
```bash
cd frontend/app && pnpm lint
```

Resultado: ✅ 0 warnings

### Build
```bash
cd backend && mvn clean package -DskipTests
cd frontend/app && pnpm build
```

Resultado: ✅ Both succeed

## QA Visual (si aplica)

[Hallazgos de ux-designer y taste-skill, si el change toca UI]

## Status

🟢 **PASS** — Todas las mutaciones causaron fallos esperados. Compuertas verdes. Listo para Archive.
```

---

## fixes-required.md — Solo si Hay Blockers

```markdown
# fixes-required.md

## CRITICAL 1 — Test de RLS no ejecuta

1. **Síntoma:** UserSecurityTest.getCurrentUserCannotSeeOtherUser no cae cuando quito set_config
2. **Causa raíz:** Test no está en suite de ejecución (falta @SpringBootTest en clase)
3. **Reproducción:**
   ```bash
   cd backend && mvn test -Dtest=UserSecurityTest
   # Resultado: BUILD SUCCESS (debería fallar)
   ```
4. **Fix propuesto:** Agregar @SpringBootTest a UserSecurityTest
5. **Verificación de cierre:** mvn test -Dtest=UserSecurityTest cae sin set_config

## CRITICAL 2 — SELECT FOR UPDATE NOWAIT no existe

1. **Síntoma:** Lock concurrency test pasa aunque quito la línea
2. **Causa raíz:** @Lock(LockModeType.PESSIMISTIC_WRITE) está escrito pero no se ejecuta (método no entra en transacción)
3. **Reproducción:**
   ```sql
   -- En ActaService.registrar():
   -- Quitar: @Lock(LockModeType.PESSIMISTIC_WRITE)
   -- Resultado: concurrency test sigue pasando (debe fallar)
   ```
4. **Fix propuesto:** Mover @Lock fuera del método auxiliar, al método que tiene @Transactional
5. **Verificación de cierre:** Con lock → test PASS. Sin lock → test FAIL
```

---

## Cuando Reportar REWORK

**REWORK** si:
- ❌ Mutación NO causó test fail (test decorativo)
- ❌ apply-progress.md tiene gaps (tasks sin números medidos)
- ❌ Test nuevo que pide tasks.md no existe en suite
- ❌ Compuerta critical no pasa (lint, build, tests)

**PASS** si:
- ✅ Todas las mutaciones causaron fallos esperados
- ✅ apply-progress.md completo con números medidos
- ✅ Compuertas: lint ✅, tests ✅, build ✅
- ✅ QA Visual sin blockers (si aplica)

---

## Diferencia Clave: Verify SDD vs ODD

| Aspecto | SDD Verify | ODD Verify |
|---------|-----------|-----------|
| **Mutaciones** | Obligatorias, todas | Opcionales, si aplica |
| **Compuertas** | Formales (todos los comandos) | Básicas (lint, test) |
| **Rigor** | Auditoría completa | Validación checklist |
| **Tiempo** | 4-6 horas | 1-2 horas |
| **Rondas** | Posibles (R1, R2, R3) | Generalmente 1 ronda |

