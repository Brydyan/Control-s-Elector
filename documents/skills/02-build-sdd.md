# Skill: /02-build-sdd

**Función:** Implementar una SDD completa (Authorize → Explore → Architect → Build).

**Entrada:** Ruta a SDD (`openspec/changes/<área>/<fecha>-<slug>/`).

**Salida:** apply-progress.md con tasks completadas, números medidos, mutaciones ejecutadas.

---

## Invocación

```
/02-build-sdd openspec/changes/auth/2026-11-08-t-auth-inv-02/
```

---

## Flujo Interno

### 1. Authorize (Lee antes de tocar código)
- proposal.md: qué se construye, por qué, fuera de scope
- design.md: decisiones D1-D5 con alternativas descartadas
- specs/*.md: requisitos GIVEN/WHEN/THEN
- tasks.md: contrato ejecutable (A-E tareas + mutaciones)

### 2. Explore (Analiza estructura existente)
- Código afectado: ubicación exacta de archivos
- Patrones similares: buscar precedentes
- Tests existentes: baseline antes de cambios

### 3. Resolve (Aclara incertidumbres ANTES de escribir)
- ¿TDD estricto o standard?
- ¿Refactor primero o build sobre lo viejo?
- ¿Qué test corre primero?

### 4. Implement (Task por task A→B→C→D→E)
- Marca `- [x]` en tasks.md mientras cierras
- Código + tests + mutaciones
- ~400 líneas por commit
- **IMPORTANTE:** Si task toca UI/UX, invoca skills

### 5. Close (apply-progress.md con números MEDIDOS)
- Tasks completadas vs. incompletas
- Números medidos (tests, errors, failures)
- Mutación: nombre exacto del test que cayó
- Compuertas ejecutadas

---

## ⚠️ Skills REQUERIDAS (INVOCA DENTRO DE ESTA SKILL)

### Si task toca Frontend / UX / Componentes:

**INVOCA DENTRO de Implement fase, para cada task de UI:**

```
/ux-designer

Contexto:
  - Feature: <T-AUTH-INV-02>
  - Task: <A.1 — Create form toggle>
  - Cambio: <Agregar checkbox "Enviar Invitación por Email">
  
Valida:
  - ¿Checkbox es usable en móvil (min 44px)?
  - ¿Está clara su función? (label descriptivo)
  - ¿Navegación teclado funciona?
  - ¿Color contraste WCAG AA?
  - ¿Estados hover/focus/disabled visibles?
```

**INVOCA DESPUÉS de Implement UI, para pulido visual:**

```
/taste-skill

Contexto:
  - Feature: <T-AUTH-INV-02>
  - Cambios UI: <Activation screen, account summary screen>
  - Paleta: <brand blue #2563eb, slate surfaces>
  
Valida:
  - ¿Dirección visual coherente con design system?
  - ¿Tokens de color/spacing aplicados?
  - ¿Sin hardcoded colors?
  - ¿Tipografía consistente (IBM Plex)?
  - ¿Dark mode si aplica?
```

### Ejemplo en apply-progress.md:

```markdown
## A.5 — Email Template + Activation Screen

- [x] HTML email: no muestra contraseña, solo enlace
  Test: EmailTemplateTest.activationLink_noPassword ✅ PASS
  
  **QA UX/Visual (via /ux-designer + /taste-skill):**
  - ✅ /ux-designer: Activation screen es accessible (44px buttons, ARIA labels)
  - ✅ /taste-skill: Email branding coherente (logo, colores, tipografía)
  - Hallazgo: Button "Activar Cuenta" en email 2px border, match brand specs

- [x] Activation screen form
  Test: ActivationComponentTest.passwordValidation ✅ PASS
  
  **QA UX/Visual (via /ux-designer):**
  - ✅ Form inputs: min 44px height, focus ring 2px blue
  - ✅ Password strength meter: visual feedback clara
  - ✅ Keyboard nav: Tab recorre todos inputs + botón
```

---

## Estándares de Calidad Obligatorios

### Tests Mínimos
- Backend: 80% cobertura en clases nuevas
- Frontend: 70% en componentes nuevos
- Mutaciones: TODAS las que declares en tasks.md

### Lint + Format
- `mvn spotless:check` (Java)
- `pnpm lint && pnpm format:check` (TypeScript)

### Secuencia Obligatoria si Cambio Toca UI
1. **Implement el código**
2. **Invoca `/ux-designer`** → valida accesibilidad + usabilidad
3. **Invoca `/taste-skill`** → valida dirección visual + tokens
4. **Incorpora hallazgos** en apply-progress.md bajo "QA Visual"
5. **Fix cualquier CRITICAL** antes de cerrar task

---

## Guardar Hallazgos en Engram

Si descubres algo fuera de scope o interesante:

```
mem_save
type: discovery
project: Control-s-Elector
payload:
  sdd: SDD #492
  hallazgo: "Email template no soporta dark mode correctamente"
  impacto: "Design system needs dark email template variant"
```

---

## Salida: apply-progress.md

```markdown
# apply-progress.md

## Baseline (Authorize)
Backend: 213 suites / 4,521 tests / 0 error

## Tasks Completadas

### A — Toggle Envío Email
- [x] A.1 — Form checkbox
  Test: CreateUserModalTest.sendEmail_false... ✅ PASS
  
  **QA UX/Visual:**
  - ✅ /ux-designer: accessible, 44px+ touch target
  - ✅ /taste-skill: token colors applied
  
### B — Expiry 24 Horas
- [x] B.1, B.2, B.3 ...

### C — Email + Activation Screens
- [x] C.1, C.2 ...

  **QA UX/Visual:**
  - ✅ /ux-designer: form inputs accessible
  - ✅ /taste-skill: email branding coherent

## Números Medidos (Final)
Backend: 213 suites / 4,531 tests / 0 error (+10 tests new)
Frontend: 0 failing

## Hallazgos QA UX
- Button contrast in dark mode: borderline (4.51:1), acceptable
- Email template font rendering in Outlook: fallback to serif, acceptable

## Compuertas
✅ mvn spotless:check
✅ pnpm lint && pnpm format:check
✅ mvn test
✅ pnpm test
✅ pnpm build
```

---

## NO hagas

❌ Escribir verify-report.md (lo hace Verify)
❌ Auto-archivar (git mv a archive/)
❌ Commitear ni pushear
❌ Decidir si está "bien" (eso lo hace Verify)
❌ Refactor fuera de scope (declará si bloquea)
❌ Saltarte /ux-designer o /taste-skill si cambio toca UI

