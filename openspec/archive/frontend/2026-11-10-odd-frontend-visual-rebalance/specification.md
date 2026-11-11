# Frontend: rebalance visual del light scheme

## Objetivo

El esquema claro del producto tiene tres síntomas visibles que el operador del dashboard percibe como una sola cosa: el azul institucional `#2563eb` se siente "fuerte" en selección y en el KPI de progreso; la página y las cards viven en `#f8fafc` y `#ffffff`, dos tonos separados por cinco unidades de luminosidad; el gradiente del banner `Progreso de Escrutinio` cubre media pantalla con azul saturado y rompe la jerarquía visual del dashboard.

El rebalance ataca los tres frentes en un solo PR: bajar el chroma de la marca, separar la página del card con un tinte + un borde, y cambiar el banner del dashboard a un fondo sólido del sidebar. El esquema oscuro (`#145`) no se toca — ya pasó su fase y quedó aceptable.

**Rama base de la cadena:** `refactor/design-system-cleanup` (PR #147, head actual `bfd86c4`).
**Estrategia de entrega:** un PR apilado sobre el stack abierto, encadenado bajo el cierre de `#147`.

**Test runner:** `pnpm test:ci` (vitest) · `pnpm lint` · `pnpm check:tokens` · `pnpm build` — los cuatro deben quedar verdes al cierre.

## Specs

### S1 — El azul que se usa para selección o para el kpi de progreso está muy fuerte

> "en el color blanco no me gusta tanto el azul que se usa para seleccion o para el kpi de progreso esta muy fuerte"

Identificado en:
- `frontend/app/src/theme/tokens.scss:74-77` — `--color-brand-primary: var(--primitive-blue-500)` resuelve a `#2563eb` (Tailwind blue-600, chroma ≈ 90%).
- `frontend/app/src/theme/tokens.scss:74-77` — `--color-brand-primary-strong: var(--primitive-blue-600)` resuelve a `#1d4ed8`.
- `frontend/app/src/theme/primeng-preset.ts:24-26` — `BRAND_BLUE[500] = '#2563eb'`, `BRAND_BLUE[600] = '#1d4ed8'` alimentan la rampa semántica del preset Aura y, por el puente `--p-primary-color → --color-brand-primary`, cualquier `p-button`, `p-inputtext:focus`, `p-checkbox`, `p-tag` con `severity="info"`, etc.
- `frontend/app/src/app/features/dashboard/dashboard.page.scss:8-12` — el gradiente del `progress-banner` va de `--color-brand-primary` a `--color-brand-primary-strong` y ocupa media pantalla.

El token semántico `--color-brand-primary` debe bajar su chroma de 90% a ~55% para que la marca se sienta "institucional" y no "app de consumo". Su nuevo valor debe ser **`#1c4a8e`** (azul navy con luminancia ~30% y chroma ~55%). El `--color-brand-primary-strong` baja a **`#15366b`** y `--color-brand-primary-active` a **`#0f2a4d`**, manteniendo la misma familia y la jerarquía "primary < strong < active".

La rampa `BRAND_BLUE` del preset se reescribe entero para que `--p-primary-500/600/700` (que Aura usa como variantes hover/active de focus rings y de los tags `severity="info"`) acompañe la decisión: 50→`#eef2f7`-afín, 100→`#d6e0ee`, 200→`#b1c4dd`, 300→`#7c9cc4`, 400→`#4d75a8`, 500→`#1c4a8e`, 600→`#15366b`, 700→`#0f2a4d`, 800→`#0a213f`, 900→`#061730`, 950→`#020617`.

**Criterio de aceptación:** `#1c4a8e` sobre `#ffffff` arroja contraste WCAG AA > 4.5:1 para texto (la cifra 32px de los stat cards queda sobre white); `#15366b` sobre `#ffffff` arroja contraste > 7:1 (texto bold); el focus ring del preset Aura sigue siendo visible (no queda por debajo del 3:1 sobre el fondo del input).

### S2 — El fondo del light mode parece todo blanco y las cards no se despegan

> "la plantailla de sakai tenia como el fondo de un color y cada cosa era como un div algo asi y se veia como el contraste"
> "porque en el mio actual es como todo blanco y se ve raro"

Identificado en:
- `frontend/app/src/theme/tokens.scss:80-84` — `--color-surface-page: var(--primitive-slate-50)` resuelve a `#f8fafc` (slate-50); `--color-surface-card: var(--primitive-white)` resuelve a `#ffffff`. Δluminosidad 5: invisible en brillo medio.
- `frontend/app/src/app/layout/main-layout/main-layout.component.html` y el resto del shell — el body hereda `--color-surface-page`, las cards heredan `--color-surface-card`, y no hay un borde que separe superficie de tarjeta.

Tres cambios coordinados:

1. `--color-surface-page` cambia de `#f8fafc` a **`#eef2f7`** (slate-100 con un tinte cian del 4%, que sigue hablando del azul de marca). Δluminosidad contra `#ffffff` sube a ~12 unidades, perceptible sin gritar.
2. `--color-surface-card` se queda en `#ffffff`.
3. El componente **`app-card`** gana un borde estándar `1px solid var(--color-border-on-surface)` para que la card se despegue del fondo tanto por la sombra como por el filo. El borde se aplica al host del `p-card` que envuelve cada `app-card`, sin tocar el resto del chrome.

**Criterio de aceptación:** al cambiar de tema a "claro" y abrir `/dashboard`, las cuatro tarjetas de la fila de stats (Enviadas / En Disputa / Corregidas / Aprobadas) y las dos tarjetas de la grilla inferior (Progreso por Cantón / Actas en Disputa) **se ven como cards individuales** flotando sobre un fondo gris-azulado claro, no como bloques blancos sobre blanco. La sombra `--shadow-card` se conserva.

### S3 — El banner del dashboard deja de ser un gradiente azul que cubre media pantalla

> "el kpi de progreso esta muy fuerte"

Identificado en:
- `frontend/app/src/app/features/dashboard/dashboard.page.scss:8-12` — `.progress-banner` aplica `linear-gradient(135deg, var(--color-brand-primary) 0%, var(--color-brand-primary-strong) 100%)`. Si S1 baja los hex, este gradiente baja el chroma, pero el KPI igual ocupa media pantalla con un color de marca. La solución no es solo cambiar hex: el banner cambia de superficie.

El `progress-banner` deja de ser azul y pasa a ser fondo sólido **`var(--color-surface-sidebar)`** (slate-900 en claro, mismo token que ya usa el sidebar del shell). El texto y la cifra del porcentaje quedan en `var(--color-text-on-brand)` (blanco) — el borde superior y la cifra 36px del stat "Enviadas" conservan `--color-brand-primary` y son los únicos lugares del dashboard donde el azul de marca toca la vista: el ojo va a la cifra, no al cromo.

**Criterio de aceptación:** el `progress-banner` se ve como una franja oscura con porcentaje grande en blanco, no como un banner publicitario; el stat "Enviadas" sigue siendo el único card del dashboard con la cifra en azul; el resto del dashboard hereda la nueva superficie `#eef2f7`.

### S4 — El guard de tokens, los tests de contraste y los snapshots reflejan el rebalance

- `frontend/app/src/theme/theme-contrast.spec.ts` debe reescribir las aserciones `--color-brand-primary` para exigir `#1c4a8e` sobre `#ffffff` con WCAG AA (≥ 4.5:1), y los pares nuevos (page→card `#eef2f7` vs `#ffffff`) deben pasar el piso de 1.05:1 que el suite ya exige para "los dos esquemas son perceptibles".
- `frontend/app/src/theme/primeng-theme.spec.ts` y `primeng-preset.spec.ts` deben aceptar `BRAND_BLUE[500] = '#1c4a8e'` y la nueva rampa del 50 al 950.
- `frontend/app/src/theme/token-guard.spec.ts` y la regla del guard (`pnpm check:tokens`) deben seguir verdes: **el vocabulario no cambia** (los mismos nombres semánticos), solo los valores cambian, así que el guard no necesita reglas nuevas.
- El fixture que rompe la regla de "no literales hex en `app/`" (excepto en `theme/`) sigue siendo válido — `--color-brand-primary` se sigue nombrando igual, lo que cambia es la dirección de la flecha.

**Criterio de aceptación:** `pnpm test:ci` queda verde con el nuevo hex y los nuevos pares de contraste documentados. La suite no agrega tests "decorativos": solo actualiza las aserciones para reflejar los nuevos hex.

## Tasks

| ID | Specs | Ruta | Commit |
|----|-------|------|--------|
| T1 | S1 | `frontend/app/src/theme/tokens.scss:74-77,80-84` (solo `--color-brand-primary`, `--color-brand-primary-strong`, `--color-brand-primary-active`, `--color-surface-page`) | `feat(frontend): rebalance brand palette and page surface` |
| T2 | S1, S4 | `frontend/app/src/theme/primeng-preset.ts:18-26,156-167` (rampa `BRAND_BLUE` y `semantic.primary`) | `feat(frontend): rebalance brand palette and page surface` (mismo commit que T1 — el preset es la mitad semántica del cambio de tokens) |
| T3 | S2 | `frontend/app/src/app/shared/components/app-card/*` (host `border: 1px solid var(--color-border-on-surface)`) | `feat(frontend): give cards a hairline to separate them from the page` |
| T4 | S3 | `frontend/app/src/app/features/dashboard/dashboard.page.scss:8-19` (quitar gradiente, fondo `--color-surface-sidebar`) | `refactor(frontend): quiet the dashboard KPI banner` |
| T5 | S4 | `frontend/app/src/theme/theme-contrast.spec.ts`, `primeng-theme.spec.ts`, `primeng-preset.spec.ts`, `dashboard.rendering.spec.ts` (el último si el snapshot del banner cambia) | `test(frontend): assert the rebalanced palette and quiet banner` |
| T6 | S1-S4 | suite completa: `pnpm check:tokens` · `pnpm test:ci` · `pnpm lint` · `pnpm build` — los cuatro verdes | cierre del PR |

Riesgo: T1+T2 comparten commit (cambio de tokens + cambio de preset son indivisibles: si uno entra y el otro no, el bridge queda colgado). T3+T4+T5 son commits separados.

## Log

### L1 — Pedido original (verbatim)

> "haz otro pr en todo el stack pr, para en el color blacndo no me gusta tanto el azul qeu se usa para seleccion o para el kpi de progreso esta muy fuerte aparte que recomiendas?, la plantailla de sakai tenia como el fondo de un color y cada cosa era como un div algo asi y se veia como el contraste porque en el mio actual es como todo blanco y se ve raro  o alemnos esa es mi impresion que podrias recomendar?"

### L2 — Diagnóstico

El azul `#2563eb` es un blue-600 puro de Tailwind, chroma ≈ 90%, luminosidad media. Cuando un color cubre >20% del viewport (gradiente del banner) sin bajar el chroma, el ojo lo siente "fuerte" aunque el contraste sea técnicamente correcto. La page vs card (slate-50 vs white) tiene Δlum = 5, invisible a brillo medio. El gradiente del banner se siente doblemente fuerte porque coincide en hue con el azul de los stat cards, monocromía sobre monocromía.

### L3 — Opciones presentadas y decisión

Se presentaron tres direcciones:

- A — Navy institucional: brand primary `#1c4a8e` + banner slate-900 + page slate-100 + borde de card. **Elegida.**
- B — Cálido neutro: brand primary `#3b6fb8` + page slate-50 + cards con borde. Descartada.
- C — Solo el banner: cambio quirúrgico sin tocar brand ni surfaces. Descartada.

Decisión final del operador en la sesión: **opción A** (Navy institucional).
