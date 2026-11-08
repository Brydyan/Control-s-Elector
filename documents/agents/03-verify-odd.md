# 03 — Verify ODD — Validación de Implementation Routing

**Skill:** `/03-verify-odd <odd-path> [round]`

**Función:** Validar que una ODD fue completada correctamente, tareas funcionales, compuertas verdes.

**Subagente:** Nuevo cada vez (contexto limpio). Modelo: `sonnet`.

---

## Cuándo Lanzar `/03-verify-odd`

1. **Builder entregó apply-progress.md** en odd/tasks/<feature>/
2. **Todas las tasks marcadas `- [x]`** en odd/tasks/<feature>.md
3. **Compuertas reportadas:** lint, tests, build
4. **Números medidos:** tests totales, failures, errors

**NO lanzar** si:
- apply-progress.md falta
- Tasks sin marcar `- [x]`
- Sin números medidos de tests/compuertas

---

## Plantilla de Lanzamiento

**Ronda 1:**
```
Verificá la ODD odd/tasks/<feature>.md.
Rama: <branch>, HEAD: <commit>, árbol limpio.

ANTES de nada, leé en orden:
  1. odd/tasks/<feature>.md (tareas y criterios)
  2. Si referencia SDD: openspec/changes/.../design.md + tasks.md
  3. apply-progress.md (output de Builder)

## MATERIAL NUEVO DESDE LA RONDA ANTERIOR

(Ronda 1: "ninguno — es la primera verificación")

## TRAMPAS VERIFICADAS DE ESTE REPO
[pegar de README.md § Trampas]

## LAS PREGUNTAS QUE DECIDEN

Formular 3-4 preguntas sobre lo funcional:

Ejemplos:
✅ "¿El toggle sendEmail realmente afecta si se envía email o no?"
   Test: CreateUserRequest(sendEmail=false) → MailService.send() NO se llama?

✅ "¿La invitación realmente expira después de 24 horas?"
   Test: invitation.expiresAt = NOW + 24h, clock.advance(25h), status = EXPIRED?

✅ "¿El botón Re-enviar Invitación genera nueva contraseña?"
   Test: oldPassword != newPassword después de resend?

## COMPUERTAS

Comandos EXACTOS ejecutados en ronda anterior:

Ronda 1:
cd backend/electoral-api-quarkus && mvn -B test
# Esperado: X tests, 0 errors, 0 failures

cd frontend/app && pnpm lint && pnpm test
# Esperado: lint clean, X tests pass, 0 failing

## SALIDA

### verify-report.md
Qué validaste, números que mediste, checklist completada.

### fixes-required.md (SOLO si hay blockers)
CRITICAL: tareas incompletas, tests fallidos, compilación quebrada.
```

**Ronda 2 (si hay fixes):**
```
Verificá de nuevo odd/tasks/<feature>.md.

## MATERIAL NUEVO DESDE LA RONDA ANTERIOR

Builder cerró estos fixes-required (ronda 1):
  - CRITICAL 1: Test de invitación no completaba
  - CRITICAL 2: Build fallaba por import faltante

La rama ahora tiene commits <X>, <Y> que cierran ambos.

[Resto igual]
```

---

## Fase 1: Autorización — Lee las Tasks

**Solo lee.** No ejecutes tests aún:

- Cada task A-E: ¿criterio de aceptación claro?
- ¿Cómo se testea cada una?
- ¿Referencia alguna SDD? (si sí, léela también)

**Red flag:** Si task dice "hacer bonito" o sin criterio de aceptación, es bloqueante.

---

## Fase 2: Auditar Tasks Completadas

Comparar **odd/tasks/<feature>.md** vs **apply-progress.md**:

```markdown
## Task A.1 — Crear toggle "Enviar Invitación"

Pide (tasks.md):
  - [ ] Form: checkbox "Enviar Invitación por Email" (default ON)
  - Test: unchecked → user.status = CREATED (sin email)

Reporta (apply-progress.md):
  - [x] **A.1** — Toggle añadido a form
  - Test: `CreateUserModalTest.sendEmail_false_createsUserWithoutEmail` ✅ PASS

Tu verificación:
  ✅ Task marcada [x]
  ✅ Test nombrado exactamente
  ✅ Test existe y pasa (ejecutar)
```

---

## Fase 3: Ejecutar Compuertas (Básicas)

**ODD no usa mutaciones.** Compuertas son suficientes:

```bash
# Backend Tests
cd backend/electoral-api-quarkus
mvn -B test
# Captura: target/surefire-reports/
# grep 'tests=\|errors=\|failures=' TEST-*.xml

# Frontend Tests + Lint
cd frontend/app
pnpm lint
pnpm test
# Captura: output, esperado 0 warnings, 0 failing

# Build
cd backend && mvn -B clean package -DskipTests
cd frontend/app && pnpm build
# Esperado: SUCCESS
```

---

## Fase 4: Validar Comportamiento Funcional

**Ejecuta los tests que Builder reportó:**

```bash
# Backend: test que Builder menciona en A.1
mvn -B test -Dtest=CreateUserModalTest#sendEmail_false_createsUserWithoutEmail
# Esperado: PASS

# Backend: test que valida cambio de expiry
mvn -B test -Dtest=UserInvitationTest#invitationExpiresAfter24Hours
# Esperado: PASS (y toma ~24 horas de reloj, o usa fake Clock)

# Frontend: test de tabla con Estado
cd frontend/app
pnpm test -- UserTableComponent.spec.ts
# Esperado: 0 failing
```

---

## Patrones de Defecto — Qué Buscar en ODD

**1. Task sin test**
- Task A.1 pide: "Agregar toggle"
- apply-progress.md: sin test reportado
- → CRITICAL: no hay prueba de que existe

**2. Test que no valida el cambio**
- Test: `UserModalTest.renderModal()` pasa
- Pero no valida si toggle realmente afecta comportamiento
- → CRITICAL: test es smoke, no funcional

**3. Números que no crecen**
- baseline: 150 tests
- apply-progress.md: 150 tests (sin cambios)
- Task A pide test nuevo → **debería haber 151+**
- → CRITICAL: tests nuevos no se ejecutan

**4. Compuerta que no corre realmente**
- apply-progress.md: "✅ lint clean"
- Pero no muestra comando ejecutado
- → Verificar vos: `pnpm lint` ahora en rama
- Si falla → CRITICAL

**5. Cambio sin impacto en referencia SDD**
- ODD #504 ajusta SDD #492
- Pero apply-progress.md no valida que SDD #492 sigue funcionando
- → Regresión: correr suite original de SDD #492

---

## verify-report.md — Estructura ODD

```markdown
# verify-report.md — ODD #504

## Fecha
2026-11-08

## Verificador
[agent name]

## Autorización Inicial
Leída:
- ✅ odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md: 5 tasks (A-E)
- ✅ Referencia: SDD #492 (User Invitations + SMTP)
- ✅ apply-progress.md: 5 tasks [x], números medidos

## Checklist de Tareas

### A — Toggle "Enviar Invitación"
- [x] A.1 Checkbox en form → Test: `CreateUserModalTest.sendEmail_false_createsUserWithoutEmail` ✅ PASS
- [x] A.2 Frontend payload `sendEmail: boolean` → Test: `UserServiceTest.createUser_SendEmailFalse` ✅ PASS
- [x] A.3 Backend lógica → Test: `UserServiceTest.createUserWithoutEmail_DoesNotSendMail` ✅ PASS

### B — Expiry 24 Horas
- [x] B.1 Migración → Test: `UserInvitationTest.invitationExpiresAfter24Hours` ✅ PASS
- [x] B.2 Email template → Validación manual: HTML contiene "24 horas" ✅
- [x] B.3 Tabla estado → Test: `UserTableComponentTest.renderInvitationStatus` ✅ PASS

### C — Botón Re-enviar
- [x] C.1 Modal confirmatorio → Validación manual: Modal aparece ✅
- [x] C.2 Endpoint resend-invitation → Test: `UserControllerTest.resendInvitation_GeneratesNewPassword` ✅ PASS

## Compuertas Ejecutadas

### Backend Tests
```bash
cd backend/electoral-api-quarkus
mvn -B test
```
Resultado:
- Baseline (apply-progress.md): 213 suites / 4,521 tests / 0 error / 2 failure
- Actual (verificador): 213 suites / 4,531 tests / 0 error / 0 failure
- Δ: +10 tests (A.1-A.3, B.1, C.1, C.2 implementados)
- ✅ PASS

### Frontend Tests + Lint
```bash
cd frontend/app
pnpm lint
# Resultado: 0 warnings ✅

pnpm test
# Resultado: 0 failing ✅
```

### Build
```bash
mvn clean package -DskipTests
# Resultado: BUILD SUCCESS ✅

pnpm build
# Resultado: Success ✅
```

## Validación Funcional

✅ A.1 — Toggle unchecked → no se envía email
✅ B.1 — Invitación expira exactamente a las 24h
✅ C.2 — Resend genera nueva password distinta de la anterior

## Regresión — SDD #492 Original

Corrí suite de SDD #492 para validar que no se rompió:
```bash
mvn -B test -Dtest=UserInvitationTest,UserInvitationSmtpTest
```
Resultado: ✅ 18 tests PASS (original suite intacta)

## Status

🟢 **PASS** — Todas las tasks completadas, compuertas verdes, sin regresión en SDD #492 original.
```

---

## fixes-required.md — Solo CRITICAL

```markdown
# fixes-required.md

## CRITICAL 1 — Test de re-envío no existe

1. **Síntoma:** apply-progress.md reporta C.2 completado pero test no existe
2. **Reproducción:** mvn test -Dtest=UserControllerTest#resendInvitation_GeneratesNewPassword
   Resultado: BUILD FAILURE (test not found)
3. **Fix:** Crear test en UserControllerTest
```

---

## Marcar ODD como VERIFICADA

Si status es PASS, agregar al final de `odd/tasks/<feature>.md`:

```markdown
---

## ✅ Verificación Verify Round 1

**Fecha**: 2026-11-08
**Verificador**: verify-odd-agent
**Status**: 🟢 PASS

### Checklist Verify
- [x] ODD Phase Authorize: tasks.md coherente con SDD #492
- [x] ODD Phase Explore: Builder exploró código afectado
- [x] ODD Phase Implement: todas las 5 tasks ejecutadas correctamente
- [x] ODD Phase Close: apply-progress.md completo (números medidos)
- [x] Compuertas pasaron: lint ✅, tests ✅, build ✅
- [x] Funcional validado: toggle, expiry 24h, re-envío todos OK
- [x] Regresión validada: SDD #492 suite original intacta

### Hallazgos
- Ninguno

### Próximo Step
Mover archivo a odd/archive/VERIFIED-2026-11-08-t-auth-inv-02-ui-adjustments.md
```

**Después:** (Arquitecto realiza)
```bash
git mv odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md \
        odd/archive/VERIFIED-2026-11-08-t-auth-inv-02-ui-adjustments.md
git mv odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.apply-progress.md \
        odd/archive/VERIFIED-2026-11-08-t-auth-inv-02-ui-adjustments.apply-progress.md
```

---

## Diferencia Clave: Verify SDD vs ODD

| Aspecto | SDD Verify | ODD Verify |
|---------|-----------|-----------|
| **Mutaciones** | Obligatorias | NO |
| **Compuertas** | Formales todas | Básicas (lint, test, build) |
| **Regresión** | Validar design no se rompe | Validar SDD padre funciona |
| **Rigor** | Auditoría exhaustiva | Validación checklist |
| **Tiempo** | 4-6 horas | 1-2 horas |

---

## Cuando Reportar REWORK

❌ REWORK si:
- Tareas sin marcar `- [x]`
- Tests reportados no existen
- Compuertas fallan (lint, test, build)
- Regresión: SDD padre se rompió
- Números no crecen (si se piden tests nuevos)

✅ PASS si:
- Todos criterios de A-E cumplidos
- Tests corren y pasan
- Compuertas verdes
- SDD padre funciona igual

