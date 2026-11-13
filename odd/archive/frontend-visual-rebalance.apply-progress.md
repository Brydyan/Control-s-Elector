# ODD Frontend Visual Rebalance — Apply Progress

**Fecha:** 2026-10-21  
**Rama:** develop (merged)  
**ODD:** `odd/tasks/frontend-visual-rebalance.md`  
**Commit Principal:** `14e0dc6` feat(frontend): rebalance the light scheme to institutional navy (#149)

---

## Resumen

ODD de rebalance visual del light scheme completada. Marca navy institucional (#1c4a8e), superficie página #eef2f7, banner dashboard quieto (fondo sidebar), borde en cards. Código merged a develop desde 2026-10-21.

---

## Tareas

### T1 — Rebalance tokens brand palette (S1) — [x]

- [x] `--color-brand-primary`: `#2563eb` → **`#1c4a8e`** (navy institucional)
- [x] `--color-brand-primary-strong`: `#1d4ed8` → **`#15366b`**
- [x] `--color-brand-primary-active`: → **`#0f2a4d`**
- [x] `--color-surface-page`: `#f8fafc` → **`#eef2f7`** (slate-100 + 4% cian tint)
- [x] Contraste WCAG AA verificado: `#1c4a8e` sobre white > 4.5:1

**Archivos:**
- `frontend/app/src/theme/tokens.scss` — nuevos hex para brand + surface-page

### T2 — Rebalance primeng preset (S1) — [x]

- [x] Rampa `BRAND_BLUE[50-950]`: 50→`#e8eef7`, 100→`#d6e0ee`, ..., 500→`#1c4a8e`, ..., 950→`#020617`
- [x] `LIGHT_BRIDGE.primary`: `#1c4a8e` (sincronizado con tokens.scss)
- [x] Semantic mappings (primary/strong/active/info) sincronizados

**Archivos:**
- `frontend/app/src/theme/primeng-preset.ts` — nuevos valores de rampa + bridge

### T3 — Card border hairline (S2) — [x]

- [x] `.p-card` gana `border: 1px solid var(--color-border-on-surface)` + localised `!important`
- [x] Borde separa card de página (visual despegue + shadow)
- [x] Dark mode compatible

**Archivos:**
- `frontend/app/src/theme/base.scss` — nueva regla .p-card con border

### T4 — Dashboard banner quieto (S3) — [x]

- [x] `.progress-banner` cambia de gradiente → fondo sólido `--color-surface-sidebar` (slate-900)
- [x] Texto/porcentaje → `--color-text-on-brand-panel` (white)
- [x] Stat "Enviadas" sigue siendo único con cifra en blue

**Archivos:**
- `frontend/app/src/app/features/dashboard/dashboard.page.scss` — cambio banner

### T5 — Tests assertion rebalance (S4) — [x]

- [x] `theme-contrast.spec.ts` — actualizado para `#1c4a8e` sobre white, banner pair reescrito
- [x] `primeng-preset.spec.ts` — rampa navy validada
- [x] `bridge.spec.ts` — primary mapping `#1c4a8e` validado
- [x] `token-guard.spec.ts` — fixture fixture fixture actualizado

**Archivos:**
- Tests theme actualizados, assertions para nuevos hex

### T6 — Suite verde (S4) — [x]

- [x] `pnpm check:tokens` → OK (vocabulario sin cambios, solo valores)
- [x] `pnpm test:ci` → 419+ tests, todos verdes
- [x] `pnpm lint` → 0 warnings
- [x] `pnpm build` → success (sin warnings nuevos)

---

## Specs Cubiertas

| Spec | Acceptance | Status |
|------|-----------|--------|
| S1 | `#1c4a8e` sobre white = WCAG AA 4.5:1+ | ✅ |
| S2 | Cards flotando (Δlum 12 + borde) | ✅ |
| S3 | Banner quieto slate-900, cifra 36px blanca | ✅ |
| S4 | Tests verde, guard verde, build verde | ✅ |

---

## Commits

| Commit | Mensaje | Parte | Status |
|--------|---------|-------|--------|
| 14e0dc6 | feat(frontend): rebalance the light scheme to institutional navy (#149) | T1–T6 todos | ✅ |

El PR #149 es un cherry-pick de 8 commits independientes que ya habían sido vertidos en una rama anterior:
- T1+T2: `feat(frontend): rebalance brand palette to institutional navy`
- T3: `feat(frontend): give cards a hairline to separate them from the page`
- T4: `refactor(frontend): quiet the dashboard KPI banner`
- T5: `test(frontend): assert the rebalanced palette and quiet banner`
- T6: Cierre de PR + docs

---

## Impacto Visual

**Antes:** Azul fuerte, página blanca sobre blanca (slate-50 vs white), banner gradiente media pantalla.

**Después:** Navy institucional, página gris-azulada clara (#eef2f7), cards con borde blanco, banner slate-900 con texto blanco, cifra "Enviadas" azul únicamente.

---

## Checklist Final

- [x] T1: Tokens brand palette rebalanceados
- [x] T2: PrimeNG preset sincronizado con tokens
- [x] T3: App-card border hairline añadido
- [x] T4: Dashboard banner quieto (sidebar surface)
- [x] T5: Tests actualizados + assertions verdes
- [x] T6: Suite completa verde (check:tokens, test:ci, lint, build)
- [x] Dark mode compatible
- [x] Commits convencionales
- [x] Listo para `/03-verify-odd` y archivado

---

**Verificado por:** Verificación de código en develop (2026-11-10)  
**Status:** ✅ COMPLETE & MERGED
