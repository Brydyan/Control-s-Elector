# 02 — Builder ODD — Implementar Implementation Routing

**Skill:** `/02-build-odd <odd-path>`

**Función:** Implementar una ODD (ajuste a feature existente) siguiendo fases Authorize → Explore → Implement → Close.

**Nota:** No necesita Architect phase (es ajuste, no nueva feature).

---

## Flujo ODD (Implement → Verify)

```
┌──────────────────────────────────────────────────┐
│ ARQUITECTO crea odd/tasks/<feature>.md           │
│  - Define tasks A-E (ajuste concreto)           │
│  - Especifica qué SDD referencia (si aplica)    │
└──────────────────────────────────────────────────┘
                     ↓
┌──────────────────────────────────────────────────┐
│ BUILDER (vía /02-build-odd)                      │
│  - Authorize: leer tasks.md                      │
│  - Explore: código afectado                      │
│  - Implement: tasks A→B→C→D→E                   │
│  - Close: apply-progress.md                      │
└──────────────────────────────────────────────────┘
                     ↓
┌──────────────────────────────────────────────────┐
│ VERIFY (vía /03-verify-odd)                      │
│  - Validar tasks completadas                    │
│  - Ejecutar compuertas (lint, tests)            │
│  - Reportar checklist                           │
└──────────────────────────────────────────────────┘
                     ↓ (si PASS)
┌──────────────────────────────────────────────────┐
│ ARCHIVE (git mv a odd/archive)                   │
│  Nombre: VERIFIED-<YYYY-MM-DD>-<feature>.md     │
└──────────────────────────────────────────────────┘
```

---

## Cuándo Lanzar `/02-build-odd`

1. **ODD está en `odd/tasks/<feature>.md`** — con tasks A-E claras
2. **Cada task tiene criterios de aceptación** — qué se testa, qué se muta (si aplica)
3. **Referencia clara a SDD padre** (si ajusta SDD existente)

**NO lanzar** si:
- Tasks son vagas ("make better", "improve UX")
- ODD no explica qué SDD ajusta
- Sin apply-progress.md template

---

## Plantilla de Lanzamiento

```
Implementá la ODD odd/tasks/<feature>.md.

ANTES de tocar código, leé:
  1. odd/tasks/<feature>.md (tu contrato)
  2. Si referencia SDD: leé openspec/changes/…/design.md y tasks.md
  3. Si está archivada: odd/archive/VERIFIED-*-<referenced>.md

## FASES

### Authorize — Entiende el ajuste
  ✓ ¿Qué SDD referencia esta ODD?
  ✓ ¿Cuál es el alcance (A-E tasks)?
  ✓ ¿Qué se testea en cada task?

### Explore — Código afectado
  ✓ Ubicar archivos que se tocarán (A.1 → qué archivo)
  ✓ Tests existentes: baseline
  ✓ Patrones similares en código

### Implement — Task por task
  Marca `- [x]` en tasks.md mientras cierras.
  - Tests: nombre exacto + resultado
  - Commits: mensaje claro, ~200-300 líneas

### Close — apply-progress.md
  - Tasks completadas (A ✅ B ✅ C ✅)
  - Números: suites, tests, errors, failures
  - Compuertas: lint, format, tests
  - Bloqueadores: qué no cerraste y por qué

## ESTÁNDARES DEL PROYECTO
[pegar de README.md § Estándares — SOLO los que apliquen]

## TRAMPAS VERIFICADAS
[pegar de README.md § Trampas]

## SALIDA

1. **odd/tasks/<feature>.md actualizado**: `- [ ]` → `- [x]` mientras implementas

2. **apply-progress.md** con:
   - Tasks completadas (✅) e incompletas (⏳)
   - Números medidos (suites, tests)
   - Compuertas ejecutadas

3. **Git history limpio**: commits con mensajes claros

NO hagas:
  ❌ Crear sección `## ✅ Verificación` (lo hace Verify)
  ❌ git mv a odd/archive (lo hace Arquitecto después de PASS)
  ❌ Decidir si está "bien" (eso lo hace Verify)
  ❌ Commitear ni pushear
```

---

## Métodos Obligatorios — ODD

| Fase | Acción |
|------|--------|
| **Authorize** | Leer odd/tasks/<feature>.md y SDD referenciada (si existe) |
| **Explore** | Analizar código afectado, patrones, tests existentes |
| **Implement** | Cada task A→B→C: código, tests, mutaciones (si aplica) |
| **Close** | apply-progress.md con números medidos + compuertas |

---

## Ejemplo: Tasks ODD

**Contexto:** ODD #504 ajusta SDD #492 (User Invitations)

```markdown
### A · Toggle "Enviar Invitación por Email"

- [ ] **A.1** — Form crear usuario: añadir checkbox "Enviar Invitación"
  Test: Si unchecked → user.status = CREATED (sin email)
  
- [ ] **A.2** — Frontend envía `sendEmail: boolean` en request
  Test: Request a POST /api/v1/users/invite con sendEmail=false
        Respuesta: 200, usuario creado sin email

- [ ] **A.3** — Backend lógica: si sendEmail=false, NO enviar SMTP
  Test: Mock MailService, verificar sendInvitationEmail NO se llamó

### B · Cambiar Expiry de 30 días a 24 horas

- [ ] **B.1** — Migración: ALTER TABLE user_invitations SET expiry_at = created_at + INTERVAL '1 day'
  Test: SELECT DATEDIFF(hour, created_at, expiry_at) FROM user_invitations
        Resultado: 24 (minutos)

- [ ] **B.2** — Email template: cambiar "30 días" → "24 horas"
  Test: Verificar email HTML contiene "24 horas"

### C · Tabla Usuarios + Estado

- [ ] **C.1** — Agregar columna "Estado" con CASE SQL
  Test: SELECT invitation_status FROM usuarios
        Estados posibles: INVITED, EXPIRED, ACTIVE, DISABLED
```

---

## Ejecutar Compuertas en ODD

```bash
# Tests
cd backend/electoral-api-quarkus
mvn -B test -Dtest=UserInvitationTest
# Esperado: tests nuevos de A, B, C pasan

cd frontend/app
pnpm test
# Esperado: tests nuevos pasan

# Lint
cd frontend/app
pnpm lint
# Esperado: ✅ no warnings

# Build
cd frontend/app
pnpm build
# Esperado: ✅ build succeeds
```

---

## Entrega: apply-progress.md

```markdown
# apply-progress.md — ODD #504

## Baseline (Authorize)
Backend: 213 suites / 4,521 tests / 0 error
Frontend: eslint clean, pnpm build succeeds

## Tasks Completadas

### A — Toggle Envío Email

- [x] **A.1** — Form checkbox
  - Test: `CreateUserModalTest.sendEmail_false_createsUserWithoutEmail`
  - Resultado: ✅ PASS

- [x] **A.2** — Request payload
  - Test: `UserControllerTest.createUser_SendEmailFalse_ReturnsCreatedStatus`
  - Resultado: ✅ PASS

- [x] **A.3** — Backend logic
  - Test: `UserServiceTest.createUser_NoEmailSent_WhenSendEmailFalse`
  - Resultado: ✅ PASS

### B — Expiry 24 horas

- [x] **B.1** — Migración
  - Comando: `select extract(hour from (expiry_at - created_at)) from user_invitations limit 1;`
  - Resultado: 24

- [x] **B.2** — Email template
  - Verificación manual: HTML contiene "24 horas"

### C — Tabla Estado

- [x] **C.1** — Columna Estado
  - Test: `UserTableComponentTest.renderInvitationStatus_INVITED`
  - Resultado: ✅ PASS

## Números Medidos (Final)

Backend: 213 suites / 4,531 tests / 0 error (Δ: +10 tests nuevos)
Frontend: eslint ✅, pnpm build ✅

## Compuertas Ejecutadas

✅ Backend tests: `mvn test` (0 failures)
✅ Frontend tests: `pnpm test` (0 failing)
✅ Lint: `pnpm lint` (0 warnings)
✅ Build: `pnpm build` (success)

## Hallazgos
(Ninguno — todo en scope)
```

---

## Diferencia Clave: ODD vs SDD Builder

| Aspecto | ODD | SDD |
|---------|-----|-----|
| **Propose/Design** | NO — ya existe | SÍ — Arquitecto define |
| **Mutaciones** | Opcionales (si garantía nueva) | Obligatorias |
| **apply-progress.md** | Simplificado (tasks + números) | Detallado (mutaciones + compuertas) |
| **Tiempo** | 2-4 horas | 2-3 semanas |
| **Verify formal** | NO — validación simple | SÍ — auditoría rigurosa |

---

## Qué NO Hacer en ODD Builder

| ❌ | Razón |
|----|-------|
| Crear sección Verificación | Lo hace Verify al final |
| Refactor más allá del scope | Si bloquea, declará. Si no, anótalo |
| Auto-decidir "está listo" | Lo decide Verify |
| Commitear/pushear | Decisión de Arquitecto |

---

## Cuando Pasar a Verify

Antes de llamar a `/03-verify-odd`:

✅ odd/tasks/<feature>.md: todas las tareas marcadas `- [x]`
✅ apply-progress.md: números medidos, compuertas ejecutadas
✅ Git history: commits limpios, sin archivos extra
✅ Tests: suite completa en verde (mvn test, pnpm test)
✅ Lint: eslint + prettier sin warnings (frontend)

