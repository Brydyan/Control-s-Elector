# Frontend: shell responsive — el shell deja de romperse a 390px y los touch targets llegan a 44px

## Objetivo

El shell y las páginas del producto están escritos para desktop de 1100px+. A 390px (iPhone 12/13/14, Galaxy S, Pixel 6) tres defectos visibles rompen el uso real del veedor y del coordinador:

1. **El pill de conexión se pinta encima del wordmark del sidebar**.
2. **El registro de acta desborda horizontalmente** y obliga a scroll lateral página por página.
3. **85 elementos interactivos tienen menos de 44×44 px**, debajo del mínimo WCAG 2.5.5 (Target Size — Level AA). En touch esto significa errores de toque en el peor momento: en el conteo.

Cada uno ataca una capa distinta. La fase cierra los tres en un solo PR porque el shell y la accesibilidad de touch target son dimensiones del mismo problema: el shell a 390px.

**Rama base de la cadena:** `feat/visual-rebalance` (PR #149, head actual `3a9547e`).
**Estrategia de entrega:** un PR apilado sobre el stack abierto, encadenado bajo el cierre de `#149`.

**Test runner:** `pnpm test:ci` (vitest) · `pnpm lint` · `pnpm check:tokens` · `pnpm build` — los cuatro deben quedar verdes al cierre, más un test nuevo que recorra la página de registro de acta a 390px y reporte targets <44px.

## Specs

### S1 — El pill de conexión se pinta encima del wordmark a 390px

> "el pill de conexión pintándose encima del wordmark a 390px"

Identificado provisionalmente en `frontend/app/src/app/layout/layout.scss:161-189` (`.layout-status-pill` y sus modificadores `status-online|reconnecting|offline`) y `frontend/app/src/app/layout/topbar/topbar.component.html:36` (donde la topbar lo renderiza). A 390px la topbar está comprimida contra el sidebar que aún pinta el wordmark del CNE, y los dos elementos colisionan: el pill queda flotando por encima del texto del logo en lugar de relegarse a su fila propia.

**Regla:** a partir de `<768px` (el breakpoint tablet que `layout.scss:429` ya declara como "es mobile"), la topbar pasa a stack vertical: el wordmark + acciones primarias en la fila 1, el pill de conexión en la fila 2 (o se reubica en el sidebar collapsed). El sidebar deja de estar visible debajo de 390px en mobile-first (queda detrás del toggle de hamburger, que ya existe en `layout.scss`). El pill deja de pintarse en la misma fila que el wordmark.

**Criterio de aceptación:** a `390×844` (iPhone 12/13/14 viewport estándar), abrir la app, loguearse como cualquier rol, observar:
- el wordmark se lee sin colisión,
- el pill "En línea" / "Reconectando" / "Sin conexión" vive en su propia línea debajo o al lado del avatar, no superpuesto al wordmark,
- ninguna coordenada del DOM tiene dos elementos con `z-index > 0` y `position: absolute|fixed` cuya bounding box se superponga con la del wordmark.

### S2 — El registro de acta desborda a 390px

> "el registro de acta desbordando"

Identificado en `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.scss`. La página de captura del acta tiene una grilla de cards (Totales de Mesa, Votos por Candidato, Blancos/Nulos, Captura Física, Resumen) con anchos `max-width` fijos heredados de desktop y patrones de `display: grid` que asumen 4 columnas. A 390px las cards se salen por la derecha y la página tiene scroll horizontal; los inputs numéricos reciben `:hover`/`:focus` mientras el usuario está scrolleando lateralmente, lo cual es exactamente el escenario equivocado.

**Regla:** la página de captura pasa a una grilla de una columna a `<768px`. Los inputs numéricos que ya usan `font-variant-numeric: tabular-nums` (meritorio de la fase #146) conservan su ancho mínimo pero no fuerzan ancho de fila. Las cards dejan de tener `max-width` superiores a `100vw - 32px` (16px de margen por lado). El sticky footer "Resumen de Escrutinio" se vuelve inline y respeta el safe-area del browser.

**Criterio de aceptación:** a `390×844`, abrir como observador, ir a *Captura de Acta*, recorrer las 4 cards: ningún elemento sale del viewport. Las pills de candidato se apilan en columna (no en grilla 2×N). Los botones de paso (1/2/3/4) caben en una fila. El input de totales cabe sin scroll horizontal.

### S3 — 85 targets interactivos están por debajo de 44×44 px

> "los 85 targets de menos de 44px"

WCAG 2.5.5 (Target Size, Level AA) exige ≥ 24×24 CSS px; WCAG 2.2 SC 2.5.8 (Target Size — Minimum, Level AA, nuevo en 2.2) sube el piso a 44×44 para todo target que no esté en "espacio equívoco" (densidad alta, copy breve, etc.). El shell del producto es un caso claro de "espacio equívoco" para formularios densos (no aplica el piso de 44), pero para los controles primaries (CTA, navegación, iconos de acción) sí.

**Cómo se mide:** una nueva suite en `frontend/app/src/theme/touch-targets.spec.ts` que abre el navegador a 390×844 y mide la `getBoundingClientRect()` de cada `button`, `a`, `[role="button"]`, `p-inputnumber` (control de PrimeNG con botones internos), `[pButton]` (botón de PrimeNG), `input[type="checkbox|radio"]` en todas las rutas autenticadas. Reporta los targets cuya `width` o `height` son < `44px`. La suite falla si el conteo sube de **0** targets nuevos bajo el piso en la lista cerrada de páginas (login, dashboard, observador/jrvs, observer/registrar-acta, coordinator/actas-lista, coordinator/acta-detalle, admin/usuarios, admin/sesiones).

**Cómo se arregla:** tres palancas escalonadas, en este orden de preferencia:
1. `min-height: 44px` (y `min-width: 44px` cuando es simétrico) en los selectores ya conocidos: `p-button`, `p-inputnumber button`, el icono-only de la topbar, los chevrones de las cards colapsables, los badges tipo `app-badge`, los chips de los filtros.
2. Cuando el contexto es denso (filas de tabla, items de timeline), `padding` adicional + `position: relative` con un `::before` que extienda el área de toque sin alterar el layout visual. WCAG 2.5.8 lo permite: el "espacio equívoco" admite padding invisible.
3. Cuando un control es decorativo (icon-only dentro de un bloque mayor), declararlo con `aria-hidden="true"` y un control accesible alternativo con `min-width: 44px`.

**Criterio de aceptación:** la suite `touch-targets.spec.ts` corre en CI, lee cada página cerrada a 390px y reporta 0 targets bajo 44×44 entre los controles primarios. Los targets secundarios (filas de tabla, chips de candidato) se documentan en un anexo del feature doc y se les aplica `min-height: 36px` + `padding-top`/`padding-bottom` extendidos como "espacio equívoco justificado" con el comentario explicando la regla.

## Tasks

| ID | Specs | Ruta | Commit |
|----|-------|------|--------|
| T1 | S1 | `frontend/app/src/app/layout/layout.scss` + `frontend/app/src/app/layout/topbar/topbar.component.html` (topbar stack vertical a `<768px`; pill relegado a su fila o al sidebar collapsed) | `feat(frontend): keep the topbar's connection pill out of the wordmark at mobile width` |
| T2 | S2 | `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.scss` (grilla de 1 col a `<768px`; safe-area en el sticky footer) | `refactor(observer): make the acta capture fit at 390px` |
| T3 | S3 | `frontend/app/src/app/layout/layout.scss` + componentes del shell (regla global `min-height: 44px` para `p-button`, icon-only, chevrones) | `feat(frontend): raise interactive controls to 44px touch target` |
| T4 | S3 | páginas específicas — observer/registrar-acta, coordinator/actas-lista, coordinator/acta-detalle, admin/usuarios, admin/sesiones — extender el área de toque de filas densas con padding + ::before invisible | `refactor(frontend): extend touch hit-areas on dense lists without changing the visual rhythm` |
| T5 | S3 | `frontend/app/src/theme/touch-targets.spec.ts` (nueva suite: abre cada página cerrada a 390×844, mide bounding rects, reporta targets <44px) | `test(frontend): assert no interactive control is below 44px at 390` |
| T6 | S1-S3 | suite + guard: `pnpm check:tokens` · `pnpm test:ci` (con la nueva suite verde) · `pnpm lint` · `pnpm build` | cierre del PR |

Riesgos:

- La suite `touch-targets.spec.ts` requiere jsdom + un renderer Angular que respete media queries. La política del repo ya usa jsdom y `@angular/core/testing` para rendering; la suite nueva hereda esa infraestructura sin sumar dependencias.
- T1 cambia la jerarquía visual de la topbar en mobile. El test de la suite y el spec de topbar (`topbar.component.spec.ts`) tienen que verificar la nueva jerarquía sin romperse: el `topbar.component.spec.ts` actual cubre detección de rol y toggles de tema, no jerarquía visual, así que no se toca.

## Log

### L1 — Pedido original (verbatim)

> "Quedan T7–T11 (shell responsive → accesibilidad → copy → performance → DESIGN.md). T7 es la que cierra lo que más se ve: el pill de conexión pintándose encima del wordmark a 390px, el registro de acta desbordando y los 85 targets de menos de 44px."

### L2 — Operador de la fase anterior

El operador aprobó el bloque T7–T11 después de que el rebalance visual (#149) cerrara el síntoma "azul fuerte + todo blanco". T7 empieza donde el rebalance termina: a 390px la experiencia del operador del conteo (veedor, coordinador, MASTER en phone) deja de estar soportada por el shell. Los tres problemas fueron enumerados textualmente por el operador; este feature doc los reescribe en lenguaje contractual con su `S#` correspondiente.

### L3 — Alcance explícito

El esquema oscuro del shell y el modo tablet (768–1024px) están fuera de scope de esta fase. La accesibilidad visual (contraste, focus rings, scroll suave) entra en T8. La copia de la UI (mensajes de error, headings, placeholders) entra en T9. T10 (performance) revisa el bundle y la hidratación si T7 introduce código nuevo por encima del budget. T11 (DESIGN.md) consolida la documentación del sistema de diseño después de T7–T10.

T7 ataca el shell del mobile-first. Si el shell a 390px está usable y los touch targets son AA, las páginas T8+ empiezan desde una base sana.
