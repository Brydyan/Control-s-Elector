# Skills de Flujos SDD y ODD

Automatización de los tres papeles: **Arquitecto QA**, **Builder** (SDD vs ODD), **Verify** (SDD vs ODD).

---

## Flujo Completo

```
                    TÚ (Arquitecto QA)
                          │
                          ▼
                   /01-qa-review
                    (es SDD o ODD?)
                          │
                ┌─────────┴─────────┐
                ▼                   ▼
            SDD FLOW             ODD FLOW
                │                   │
                ▼                   ▼
         /02-build-sdd       /02-build-odd
    (Authorize→Explore→    (Authorize→Explore→
     Architect→Build)        Implement)
           │                   │
           ▼                   ▼
         apply-progress.md   apply-progress.md
           │                   │
           ▼                   ▼
      /03-verify-sdd      /03-verify-odd
    (Mutaciones          (Checklist
     Formales)           Validación)
           │                   │
      ┌────┴────┐         ┌────┴────┐
      ▼         ▼         ▼         ▼
    PASS    REWORK     PASS    REWORK
      │         │         │         │
      ▼         ▼         ▼         ▼
  Archive   /02-build   Archive   /02-build
             (fixes)             (fixes)
```

---

## Skills

| Skill | Función | Entrada | Salida | Tiempo |
|-------|---------|---------|--------|--------|
| **`/01-qa-review`** | Decide SDD o ODD | Concepto de cambio | Recomendación + próximos pasos | 15 min |
| **`/02-build-sdd`** | Implementar SDD completa | Ruta a `openspec/changes/` | apply-progress.md | 1-2 semanas |
| **`/02-build-odd`** | Implementar ODD (ajuste) | Ruta a `odd/tasks/` | apply-progress.md | 2-4 horas |
| **`/03-verify-sdd`** | Auditar SDD con mutaciones | Ruta a `openspec/changes/` | verify-report.md + fixes-required.md | 4-6 horas |
| **`/03-verify-odd`** | Validar ODD | Ruta a `odd/tasks/` | verify-report.md + fixes-required.md | 1-2 horas |

---

## Referencia Rápida

### SDD (Software Design Document)

**Cuándo:** Nueva feature, cambio arquitectónico, API nueva.

**Pasos:**
1. `01-qa-review` → decide SDD
2. Crear `openspec/changes/<área>/<fecha>-<slug>/` con proposal.md, design.md, specs/, tasks.md
3. `/02-build-sdd <ruta>` → implementar (invocar `/ux-designer` y `/taste-skill` si necesario)
4. `/03-verify-sdd <ruta>` → auditar con mutaciones (invocar skills si UI)
5. Si PASS: Arquitecto hace `git mv` a `openspec/archive/`
6. Si REWORK: vuelve a `/02-build-sdd` con fixes-required.md

**Skills Requeridas (Builder/Verify invocan):**
- `/ux-designer` — si toca UI/UX
- `/taste-skill` — si toca diseño visual/branding

---

### ODD (Implementation Routing)

**Cuándo:** Ajuste a feature existente, fix, completación.

**Pasos:**
1. `01-qa-review` → decide ODD
2. Crear `odd/tasks/<feature>.md` con tasks A-E y criterios de aceptación
3. `/02-build-odd <ruta>` → implementar (invocar `/ux-designer` y `/taste-skill` si necesario)
4. `/03-verify-odd <ruta>` → validar checklist (invocar skills si UI)
5. Si PASS: Arquitecto hace `git mv` a `odd/archive/VERIFIED-*`
6. Si REWORK: vuelve a `/02-build-odd` con fixes-required.md

**Skills Requeridas (Builder/Verify invocan):**
- `/ux-designer` — si toca UI/UX
- `/taste-skill` — si toca diseño visual/branding

---

## Invocación

```bash
# Decidir tipo
/01-qa-review

# SDD
/02-build-sdd openspec/changes/auth/2026-11-08-t-inv-02/
/03-verify-sdd openspec/changes/auth/2026-11-08-t-inv-02/
/03-verify-sdd openspec/changes/auth/2026-11-08-t-inv-02/ R2

# ODD
/02-build-odd odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md
/03-verify-odd odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md
/03-verify-odd odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md R2
```

---

## Dónde Leer

- **`01-qa-review.md`** — Lógica de decisión, ejemplos SDD vs ODD
- **`02-builder-sdd.md`** — Phases, mutaciones, apply-progress.md template, cuándo invocar skills
- **`02-builder-odd.md`** — Phases, compuertas, apply-progress.md template, cuándo invocar skills
- **`03-verify-sdd.md`** — Mutaciones formales, compuertas, verify-report.md template, cuándo invocar skills
- **`03-verify-odd.md`** — Checklist, validación, verify-report.md template, cuándo invocar skills

---

## Skills Compañeros (Invocar desde Builder/Verify)

**`/ux-designer`** — Auditar accesibilidad, usabilidad, navegación teclado, ARIA roles, contraste WCAG.

**`/taste-skill`** — Auditar branding, dirección visual, tokens de diseño, dark mode, tipografía, pulido.

**Cuándo invocar desde Builder:**
- Task toca componentes UI (forma, tabla, modal)
- Task es nueva pantalla/flujo
- Task modifica email template o visual branding

**Cuándo invocar desde Verify:**
- apply-progress.md reporta cambios frontend
- SDD/ODD toca UI/UX
- Necesitas validar accesibilidad + coherencia visual

**Cómo integrar resultados:**
- Agregar sección `## QA Visual` a apply-progress.md (Builder)
- Agregar sección `## QA Visual` a verify-report.md (Verify)
- Reportar CRITICAL si hay violaciones WCAG o inconsistencias de design system

---

## Reglas de Oro

1. **Nadie verifica su propio trabajo**
   - Builder no decide si está bien
   - Verify no arregla lo que encuentra

2. **Skills complementarios son OBLIGATORIOS si toca UI**
   - `/ux-designer` SIEMPRE si hay cambios frontend
   - `/taste-skill` SIEMPRE si hay cambios visuales/branding

3. **SDD y ODD son flujos DISTINTOS**
   - SDD: proposal → design → specs → tasks → build → verify formal
   - ODD: tasks → build → verify checklist

4. **Cada sección (QA Visual) en apply-progress/verify-report debe citar qué skill la hizo**
   - "via /ux-designer: accessible ✅"
   - "via /taste-skill: tokens OK ✅"

