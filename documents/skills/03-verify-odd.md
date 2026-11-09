# Skill: /03-verify-odd

**Función:** Verificar ODD con validación checklist (sin mutaciones formales).

**Entrada:** Ruta a ODD (`odd/tasks/<feature>.md`), ronda opcional (R1, R2).

**Salida:** verify-report.md + fixes-required.md (si CRITICAL).

---

## Invocación

```
/03-verify-odd odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md
/03-verify-odd odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md R2
```

---

## Flujo Interno

### 1. Authorize
- odd/tasks/<feature>.md: tareas claras
- apply-progress.md: números medidos
- Si referencia SDD: verifica que SDD padre funciona

### 2. Audit Tasks
- ¿Todas marcadas [x]?
- ¿Cada test nombrado existe y pasa?
- ¿Números crecen (si hay tests nuevos)?

### 3. Validar Compuertas (Básicas)
- Lint ✅
- Tests ✅
- Build ✅
- Regresión (SDD padre sigue funcionando) ✅

### 4. Audit Visual/UX (Si aplica)
- Invocar /ux-designer y /taste-skill
- Validar coherencia con SDD padre

### 5. Reportar
- verify-report.md: qué validaste, números
- fixes-required.md: SOLO CRITICAL

---

## ⚠️ Skills REQUERIDAS (INVOCA DENTRO DE ESTA SKILL)

### Si ODD toca Frontend / UX:

**INVOCA DESPUÉS de compuertas básicas, para validar accesibilidad/usabilidad:**

```
/ux-designer

Contexto:
  - Feature: <ODD #504 — T-AUTH-INV-02 UI Adjustments>
  - Tasks: <A (email sin contraseña), B (expiry 24h), C (resend button)>
  - Cambios UI: <Email template, tabla con Re-enviar, modales>
  
Valida:
  - ¿Email accesible en lectores de pantalla?
  - ¿Botón "Re-enviar Invitación": 44px+ touch target?
  - ¿Modal confirmatorio: keyboard nav completa?
  - ¿Contraste email/estado badges: WCAG AA?
```

**INVOCA DESPUÉS, para validar coherencia visual con SDD padre:**

```
/taste-skill

Contexto:
  - Feature: <ODD #504>
  - Referencia SDD: <SDD #492 — User Invitations>
  - Cambios: <Email branding, tabla estado badges, modales>
  - Design system: <Tokens, dark mode>
  
Valida:
  - ¿Email mantiene branding de SDD #492?
  - ¿Colores estado (INVITED/EXPIRED/ACTIVE): coherentes con design system?
  - ¿Dark mode (si aplica): se ve bien?
  - ¿No hay colores hardcoded, todo tokens?
  - ¿Tipografía y spacing consistentes?
```

### Ejemplo en verify-report.md:

```markdown
## QA Visual — ODD #504 (Referencia SDD #492)

### ux-designer Findings
- ✅ Email template: accesible (img alt text, link descriptivo)
- ✅ Re-enviar button: 48px height (> 44px min)
- ✅ Modal: Tab/Escape navegación funciona
- ✅ Estado badges: colores WCAG AA en ambos temas

### taste-skill Findings
- ✅ Email branding: Control's Elector logo, colors match SDD #492
- ✅ Estado badges: usan tokens (--color-success, --color-warning)
- ✅ Modal styling: coherente con SDD #492 design
- ✅ No hardcoded colors: grep "#[0-9a-f]{6}" = 0 matches
- ✅ Dark mode: estado badges visibles en ambos temas

Conclusión: ODD mantiene coherencia visual con SDD #492 ✅
```

---

## Checklist de Validación

```markdown
## Checklist Verify ODD #504

- [x] odd/tasks/<feature>.md: todas tasks [x]
- [x] apply-progress.md: números medidos (tests, errors)
- [x] Compuertas: lint ✅, tests ✅, build ✅
- [x] SDD padre (SDD #492): suite original pasa (regresión OK)
- [x] QA Visual (/ux-designer): accesible ✅
- [x] QA Visual (/taste-skill): coherente ✅
- [x] Hallazgos documentados (si los hay)

Status: 🟢 PASS
```

---

## Validación Regresión (IMPORTANTE)

Si ODD referencia SDD:

```bash
# Correr suite original de SDD referenciada
mvn -B test -Dtest=UserInvitationTest,UserInvitationSmtpTest
# Esperado: ✅ PASS (SDD #492 intacta)

## En verify-report.md:
# Regresión Validation — SDD #492 (padre)
# Tests corridos: UserInvitationTest, UserInvitationSmtpTest
# Resultado: ✅ 18 tests PASS (intactos)
```

**Si regresión falla:**
→ CRITICAL en fixes-required.md

---

## Salida: verify-report.md

```markdown
# verify-report.md — ODD #504

## Fecha
2026-11-08

## Verificador
verify-odd-agent

## Autorización
- ✅ odd/tasks: 5 tasks [x], criterios claros
- ✅ SDD padre (SDD #492): referencia correcta
- ✅ apply-progress.md: números medidos

## Checklist de Tareas

### A — Email Template (Sin Contraseña)
- [x] A.1 HTML email ✅ PASS
  Test: EmailTemplateTest.noPassword
  Verificación: email no contiene contraseña temporal ✅

### B — Expiry 24 Horas
- [x] B.1 Migración ✅ PASS
  SELECT DATEDIFF(hour, created_at, expiry_at) = 24 ✅

### C — Botón Re-enviar
- [x] C.1 Endpoint + Modal ✅ PASS
  Test: UserControllerTest.resendInvitation_GeneratesNewPassword ✅

## Compuertas Ejecutadas

### Backend Tests
mvn -B test
Resultado: 213 suites / 4,531 tests / 0 error ✅

### Frontend Tests
pnpm test: 0 failing ✅
pnpm lint: 0 warnings ✅

### Build
mvn package -DskipTests ✅
pnpm build ✅

## Regresión — SDD #492 Original

mvn -B test -Dtest=UserInvitationTest,UserInvitationSmtpTest
Resultado: ✅ 18 tests PASS (SDD #492 intacta, sin regresión)

## QA Visual (via /ux-designer + /taste-skill)

### ux-designer
- ✅ Email template accesible
- ✅ Re-enviar button: 48px, focus visible
- ✅ Modal: keyboard nav completa

### taste-skill
- ✅ Email branding coherente con SDD #492
- ✅ Colores en tokens, no hardcoded
- ✅ Dark mode OK (si aplica)

## Status

🟢 **PASS** — Todas las tasks completadas, compuertas verdes, sin regresión en SDD #492.
```

---

## Agregar Sección Final al ODD (Si PASS)

Después de Verify, agregar al final de `odd/tasks/<feature>.md`:

```markdown
---

## ✅ Verificación Verify Round 1

**Fecha**: 2026-11-08
**Verificador**: verify-odd-agent
**Status**: 🟢 PASS

### Checklist Verify
- [x] ODD Phase Authorize: tasks claras
- [x] ODD Phase Explore: Builder exploró código
- [x] ODD Phase Implement: todas las 5 tasks ejecutadas
- [x] ODD Phase Close: apply-progress.md completo
- [x] Compuertas pasaron: lint ✅, tests ✅, build ✅
- [x] Regresión validada: SDD #492 intacta
- [x] QA Visual validado: /ux-designer + /taste-skill OK

### Hallazgos
- Ninguno

### Próximo Step
Mover a: odd/archive/VERIFIED-2026-11-08-t-auth-inv-02-ui-adjustments.md
```

---

## Ronda 2 (Si hay fixes)

```
/03-verify-odd odd/tasks/... R2

Cambios desde R1:
  - Builder cerró CRITICAL: <descripción>
  
Tu salida:
  - Ejecuta compuertas de nuevo
  - Valida regresión
  - Genera verify-report.md R2 final
```

---

## NO hagas

❌ Arreglar lo que encuentres (reportá)
❌ Crear sección Verificación si aún hay CRITICAL
❌ git mv a archive (lo hace Arquitecto)
❌ Commitear ni pushear
❌ Saltarte /ux-designer o /taste-skill si ODD toca UI

