# Skill: /03-verify-sdd

**Función:** Verificar SDD con auditoría formal (mutaciones + compuertas).

**Entrada:** Ruta a SDD (`openspec/changes/<área>/<fecha>-<slug>/`), ronda opcional (R1, R2, R3).

**Salida:** verify-report.md + fixes-required.md (si CRITICAL).

---

## Invocación

```
/03-verify-sdd openspec/changes/auth/2026-11-08-t-auth-inv-02/
/03-verify-sdd openspec/changes/auth/2026-11-08-t-auth-inv-02/ R2
```

---

## Flujo Interno

### 1. Authorize (Lee antes de ejecutar)
- proposal.md, design.md, specs/, tasks.md, apply-progress.md
- ¿Qué promete vs. qué entregó?

### 2. Audit Tasks
- Cada task en tasks.md: ¿está marcada [x]?
- Cada test nombrado: ¿existe y pasa?
- Cada número medido: ¿coincide con apply-progress.md?

### 3. Ejecutar Mutaciones (LA PARTE CRÍTICA)
- Cada mutación en tasks.md: ejecutar
- Esperar que test caiga
- Si test NO cae → CRITICAL

### 4. Validar Compuertas
- Lint, tests, build: todos verdes
- Si aplica: Verify UX/Visual

### 5. Reportar
- verify-report.md: qué ejecutaste, números medidos
- fixes-required.md: SOLO CRITICAL (si hay)

---

## ⚠️ Skills REQUERIDAS (INVOCA DENTRO DE ESTA SKILL)

### Si SDD toca Frontend / UX:

**INVOCA DESPUÉS de Implement validation, para auditar UI/UX:**

```
/ux-designer

Contexto:
  - Feature: <SDD #492 — User Invitations>
  - Tasks implementadas: <A (form toggle), C (tabla estado)>
  - Cambios UI: <Form, tabla usuarios, modales>
  
Valida:
  - ¿Todos los inputs (checkbox, button) son accesibles?
  - ¿Min 44px touch targets en móvil?
  - ¿Navegación teclado completa (Tab, Enter, Escape)?
  - ¿States (hover/focus/active/disabled) visibles?
  - ¿ARIA labels + roles en lugar correcto?
```

**INVOCA DESPUÉS, para auditar visual/branding:**

```
/taste-skill

Contexto:
  - Feature: <SDD #492>
  - Cambios visuales: <Form controls, tabla, badges de estado>
  - Design system: <tokens.scss, dark mode>
  
Valida:
  - ¿Colores hardcoded o tokens?
  - ¿Dark mode funciona igual?
  - ¿Tipografía (IBM Plex) consistente?
  - ¿Estados (INVITED, EXPIRED, ACTIVE) visibles claramente?
  - ¿Badge colors WCAG AA?
```

### Ejemplo en verify-report.md:

```markdown
## QA Visual (Frontend Changes)

### ux-designer Findings
- ✅ Form toggle: 48px height (> 44px minimum)
- ✅ Table buttons: focus ring 2px blue outline
- ✅ Modal: keyboard nav Tab/Escape works
- ⚠️ Estado badge: color too light in light mode (3.8:1 contrast)
  → Reportado en fixes-required.md

### taste-skill Findings
- ✅ Colores: todos en tokens, no hardcoded
- ✅ Dark mode: estado badges visibles en ambos temas
- ✅ Tipografía: IBM Plex aplicado
- ✅ Spacing: tokens usados (--space-3, --space-4)
- ⚠️ Button en email template: pixel-perfect vs. brand specs
  → Nota en hallazgos, no bloqueante
```

---

## Mutaciones Críticas

**Ejecutar TODAS las mutaciones en tasks.md:**

```bash
# M1: Quitar CREATE TABLE usuario
git stash
# Editar 001-schema.sql: comentar línea
mvn -B test -Dtest=UserRepositoryTest
# Esperado: FAIL (relation "usuario" does not exist)
git checkout 001-schema.sql
mvn -B test -Dtest=UserRepositoryTest
# Esperado: PASS

## En verify-report.md:
# M1 — Quitar CREATE TABLE usuario
#   Test: UserRepositoryTest.save
#   Esperado: cayó
#   Actual: ✅ CAYÓ (relation not found)
```

**Si mutación NO causa test fail:**
→ CRITICAL en fixes-required.md

---

## Compuertas Formales

```bash
# Backend
cd backend && mvn -B test
cd backend && mvn spotless:check

# Frontend
cd frontend/app && pnpm lint
cd frontend/app && pnpm test
cd frontend/app && pnpm build

# Build todo
mvn -B clean package
```

---

## Salida: verify-report.md

```markdown
# verify-report.md

## Autorización
- ✅ proposal.md: promete X, design de Y
- ✅ design.md: decisiones D1-D5 documentadas
- ✅ tasks.md: 8 tasks [x], cada una con mutación
- ✅ apply-progress.md: números medidos

## Mutaciones Ejecutadas

### M1 — Quitar CREATE TABLE usuario
- Cambio: edité 001-schema.sql (comenté línea)
- Test afectado: UserRepositoryTest.save
- Esperado: FAIL
- Actual: ✅ **CAYÓ** (relation "usuario" does not exist)
- Restaurado: git checkout

### M2 — Remover @Lock pessimistic
- Cambio: comenté anotación en ActaService
- Test afectado: ActaConcurrencyTest.doubleRegisterDetected
- Esperado: FAIL
- Actual: ✅ **CAYÓ** (duplicate acta id)

## Compuertas Ejecutadas

### Backend Tests
mvn -B test
Resultado: 213 suites / 4,531 tests / 0 error ✅

### Frontend Tests + Lint
pnpm test: 0 failing ✅
pnpm lint: 0 warnings ✅
pnpm build: success ✅

## QA Visual (via /ux-designer + /taste-skill)

### ux-designer
- ✅ Form toggle: 48px, focus visible
- ✅ Modal: keyboard nav complete
- ⚠️ Badge estado: contrast 3.8:1 (AA requires 4.5:1)
  → CRITICAL (reported)

### taste-skill
- ✅ Colores en tokens, no hardcoded
- ✅ Dark mode funciona
- ✅ Tipografía consistente

## Status

🟡 **REWORK** — Una violación WCAG AA en badge color (fixes-required.md)
```

---

## fixes-required.md

```markdown
# fixes-required.md

## CRITICAL 1 — Badge "EXPIRED" tiene bajo contraste (3.8:1)

1. **Síntoma:** Badge con color amber-600, texto amber-900, contrast ratio 3.8:1 (< 4.5:1 WCAG AA)
2. **Causa raíz:** Token `--color-warning` usa amber-600 (too light)
3. **Reproducción:**
   ```bash
   # Visually: open tabla de usuarios, mira badge EXPIRED
   # Medida: https://webaim.org/resources/contrastchecker/
   # amber-600 #d97706 sobre white: 3.8:1
   ```
4. **Fix propuesto:** Cambiar `--color-warning-text` a amber-800 (más oscuro)
5. **Verificación de cierre:** Badge EXPIRED sigue visible, contraste > 4.5:1

## CRITICAL 2 — Test de invitación no completa mutación

1. **Síntoma:** M3 (quitar expiry time) no causa test fail
2. **Causa raíz:** Test solo verifica tabla, no la lógica de expiry
3. **Fix propuesto:** Agregar test: `InvitationTest.expiry_24hours_exacto()`
4. **Verificación de cierre:** Con expiry 24h → PASS. Con 30 days → FAIL
```

---

## Ronda 2+ (Si hay fixes)

```
/03-verify-sdd openspec/changes/... R2

Cambios desde R1:
  - Builder cerró CRITICAL 1: cambió amber-600 → amber-800
  - Builder cerró CRITICAL 2: agregó test exacto de expiry

Tu salida:
  - Ejecuta mutación M3 de nuevo → test cae ✅
  - Valida que badge contrast > 4.5:1 ✅
  - Genera verify-report.md R2 final
```

---

## NO hagas

❌ Arreglar lo que encuentres (reportá y cierra)
❌ Decidir scope o prioridades
❌ Commitear ni pushear
❌ Saltarte /ux-designer o /taste-skill si SDD toca UI

