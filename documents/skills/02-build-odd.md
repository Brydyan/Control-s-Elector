# Skill: /02-build-odd

**Función:** Implementar una ODD (ajuste a feature existente).

**Entrada:** Ruta a ODD (`odd/tasks/<feature>.md`).

**Salida:** apply-progress.md con tasks completadas, números medidos, compuertas verdes.

---

## Invocación

```
/02-build-odd odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md
```

---

## Flujo Interno

### 1. Authorize
- odd/tasks/<feature>.md: tareas A-E claras
- SDD padre (si referencia): design.md + tasks.md
- Criterios de aceptación por task

### 2. Explore
- Código afectado: qué archivo toca cada task
- Tests existentes: baseline
- Patrones similares

### 3. Implement (Task por task)
- Marca `- [x]` en odd/tasks/<feature>.md mientras cierras
- Código + tests
- ~200-300 líneas por commit
- **IMPORTANTE:** Si task toca UI/UX, invoca skills

### 4. Close (apply-progress.md)
- Tasks completadas
- Números: tests, errors, failures
- Compuertas ejecutadas (lint, test, build)

---

## ⚠️ Skills REQUERIDAS (INVOCA DENTRO DE ESTA SKILL)

### Si task toca Frontend / UX / Componentes:

**INVOCA DENTRO de Implement fase, para cada task de UI:**

```
/ux-designer

Contexto:
  - Feature: <ODD #504 — T-AUTH-INV-02 UI Adjustments>
  - Task: <A — Email template sin contraseña>
  - Cambio: <Email muestra solo enlace de activación, no contraseña temporal>
  
Valida:
  - ¿Email es legible en Outlook, Gmail, Apple Mail?
  - ¿CTA "Activar Cuenta" es clara y clicable?
  - ¿Contraste suficiente? (WCAG AA)
  - ¿Accesible para screen readers (alt text, roles)?
```

**INVOCA DESPUÉS de Implement UI, para pulido visual:**

```
/taste-skill

Contexto:
  - Feature: <ODD #504>
  - Cambios UI: <Email template, activation screen, account summary>
  - Paleta: <Control's Elector blue #2563eb, slate surfaces>
  
Valida:
  - ¿Email template branding coherente?
  - ¿Logo escala bien en móvil?
  - ¿Tipografía (IBM Plex) renderiza correctamente?
  - ¿Dark mode (si aplica) se ve bien?
  - ¿Spacing y márgenes consistentes?
```

### Ejemplo en apply-progress.md:

```markdown
## A — Email Template (No Contraseña, Solo Enlace)

- [x] Email HTML: solo muestra usuario + enlace, no contraseña
  Test: EmailTemplateTest.activationEmail_noPassword ✅ PASS
  
  **QA UX/Visual (via /ux-designer + /taste-skill):**
  - ✅ /ux-designer: Email legible en 5+ clientes. Link clickable.
  - ✅ /taste-skill: Logo escala, colores Control's Elector brand
  - Hallazgo: Outlook 2016 no muestra fondo de color (expected, fallback ok)

## B — Pantalla Activación Contraseña

- [x] Form: establecer contraseña con validación real-time
  Test: ActivationTest.passwordValidation_RealTime ✅ PASS
  
  **QA UX/Visual (via /ux-designer):**
  - ✅ Input password: 44px height (mobile touch target)
  - ✅ Focus ring: 2px blue outline, visible
  - ✅ Keyboard: Tab recorre todos inputs + botón
  - ✅ Requisitos checkmark: clear visual feedback (✓/✗)

## C — Pantalla Resumen Activación

- [x] Summary: usuario, correo control, rol, botón "Ir a Login"
  Test: AccountSummaryTest.renderUserData ✅ PASS
  
  **QA UX/Visual (via /taste-skill):**
  - ✅ Card branding: verde (éxito), typo consistent
  - ✅ Botón "Ir a Login": brand primary color
  - ✅ Spacing: --space-4 entre elementos
```

---

## Estándares de Calidad

### Tests Mínimos
- Nuevo tests si task introduce comportamiento
- Regresión: tests de SDD padre siguen pasando

### Compuertas
- `pnpm lint` — 0 warnings
- `pnpm test` — 0 failing
- `mvn test` — 0 failures
- `pnpm build` — success

### Secuencia si Cambio Toca UI
1. **Implement el código**
2. **Invoca `/ux-designer`** → accesibilidad + usabilidad
3. **Invoca `/taste-skill`** → dirección visual + tokens
4. **Incorpora hallazgos** en apply-progress.md bajo "QA Visual"
5. **Fix cualquier CRITICAL** antes de cerrar task

---

## Salida: apply-progress.md

```markdown
# apply-progress.md — ODD #504

## Baseline (Authorize)
Backend: 213 suites / 4,521 tests / 0 error
Frontend: eslint clean, pnpm build succeeds

## Tasks Completadas

### A — Email Template (Sin Contraseña)
- [x] A.1 — Crear email HTML
  Test: EmailTemplateTest.noPassword ✅ PASS
  
  **QA Visual:**
  - ✅ /ux-designer: Legible, CTA clara
  - ✅ /taste-skill: Branding Control's Elector OK

### B — Expiry 24 Horas
- [x] B.1 — Migración
  Verificación: SELECT DATEDIFF(hour, ...) = 24 ✅

### C — Botón Re-enviar
- [x] C.1 — Modal + endpoint
  Test: UserTableTest.resendInvitation_ShowsModal ✅ PASS

## Números Medidos (Final)
Backend: 213 suites / 4,531 tests / 0 error (Δ: +10)
Frontend: 0 failing, lint ✅

## Compuertas
✅ Backend tests: mvn test
✅ Frontend tests: pnpm test (0 failing)
✅ Lint: pnpm lint (0 warnings)
✅ Build: pnpm build (success)

## Hallazgos QA UX
- Email: Outlook 2016 no renderiza fondo (expected)
- Activation form: Requisitos checkmark clara
- Summary screen: Éxito visual muy clara (verde)
```

---

## Regresión Obligatoria

Si ODD ajusta SDD padre:

```markdown
## Validación Regresión — SDD #492

Corrí suite original de User Invitations:
mvn -B test -Dtest=UserInvitationTest,UserInvitationSmtpTest

Resultado: ✅ 18 tests PASS (SDD #492 intacta)
```

---

## NO hagas

❌ Crear sección `## ✅ Verificación` (lo hace Verify)
❌ git mv a odd/archive (lo decide Arquitecto)
❌ Commitear ni pushear
❌ Saltarte /ux-designer o /taste-skill si cambio toca UI
❌ Refactor fuera de scope

