# Apply progress — ODD #505 (Design System Completion: Toast, A11y, Spacing, Focus, Transitions)

> **Builder.** Cierre de *implementación*: qué se cerró, qué no y por qué, los números **medidos** y
> las mutaciones ejecutadas. **NO es un veredicto de calidad**: eso lo hace Verify.
>
> Fecha: 2026-11-08 · Rama: `brydyan/sc-492/t-auth-inv-02-user-invitations-smtp-email`
> ODD: `odd/tasks/odd-505-design-system-completion.md`

---

## 1. Qué se cerró

| Bloque | Tarea | Estado | Dónde vive |
|---|---|---|---|
| A | Toast: componente + servicio + integración en el root + uso en un modal | ✅ (mecanismo con desviación 3) | `shared/ui/toast/toast.component.{ts,scss}`, `core/services/toast.service.ts`, `app.component.{ts,html}`, `cargo-modal.component.ts` |
| B | Escala de espaciado completa + documentación | ✅ (desviaciones 1 y 2) | `theme/tokens.scss`, `frontend/app/DESIGN.md` |
| C | Anillo de foco con token de marca | ✅ (desviación 6) | `theme/tokens.scss`, `theme/focus-ring.scss`, `theme/variables.scss` |
| D | Transiciones base con tokens de duración y keyframes | ✅ (desviación 7) | `theme/tokens.scss`, `theme/base.scss`, `theme/animations.scss`, `theme/variables.scss` |
| E | Setup de a11y en CI: analizador + helper + specs de los componentes base | ✅ (desviación 4) | `shared/testing/a11y-test.helper.ts`, `src/test-setup.ts`, `*.a11y.spec.ts`, `package.json` |
| M1–M4 | Mutaciones obligatorias | ✅ **4/4 matan** | ver §4 |

**No se cerró (no es del Builder):** `Verify round 1` y `Archive to odd/archive/…` quedan sin marcar.
No archivé, no commiteé, no pusheé, y no escribí `verify-report.md`, `fixes-required.md` ni
`archive-report.md`.

---

## 2. Números medidos

### 2.1 Frontend — `pnpm test:ci` (vitest + a11y + lint)

```
vitest run  → 82 archivos / 802 tests / 0 fallos / 0 errores
vitest a11y →  3 archivos /   5 tests / 0 fallos        (Button, Card, Table)
ng lint     → All files pass linting.
```

Specs nuevos de este ODD:

| Spec | tests | qué fija |
|---|---:|---|
| `theme/design-system.spec.ts` | 9 | escala de espaciado, tokens del anillo, keyframes, `prefers-reduced-motion` |
| `core/services/toast.service.spec.ts` | 6 | severidad y vida de cada mensaje |
| `shared/ui/toast/toast.component.spec.ts` | 1 | el toast montado |
| `shared/ui/button/button.component.a11y.spec.ts` | 3 | 0 violaciones + el contrapeso (un botón sin nombre **cae**) |
| `shared/ui/card/card.component.a11y.spec.ts` | 1 | 0 violaciones |
| `shared/ui/table/table.component.a11y.spec.ts` | 1 | 0 violaciones |
| `features/master/.../cargo-modal.component.spec.ts` | 2 | el toast del modal |
| `app.component.spec.ts` (+1) | 2 | el toast en el root |

### 2.2 Guard de tokens — `pnpm check:tokens`

```
Token guard: no violations.
```

### 2.3 E2E — `npx playwright test`

```
60 tests → 58 pasan / 2 skip / 0 fallan
```

`j8-design-system.spec.ts` (nuevo, 4 tests = 2 escenarios × 2 proyectos): el anillo de foco se
mide **en el navegador** (2px, sólido, color de marca) y el toast está montado en el root.

### 2.4 Build — `pnpm build`

```
Output location: frontend/app/www     (sin errores)
```

---

## 3. Qué cambió, por bloque

### A · Toast

- `shared/ui/toast/toast.component.ts`: envuelve el `<p-toast>` de PrimeNG con el lenguaje del
  sistema (radio, sombra, ancho) y **sin desenfoque de fondo**: un panel de vidrio se come el
  contraste del texto.
- `core/services/toast.service.ts`: `success` / `info` / `warn` / `error` / `add` / `clear`.
  `success` e `info` se cierran solos a los **5 s**; `warn` y `error` **se quedan** hasta que se
  cierran (un error que desaparece solo es un error que nadie vio).
- `main.ts`: se provee el `MessageService` de PrimeNG (en la v21 **no** es `providedIn: 'root'`:
  sin esta instancia compartida el toast se monta pero no muestra nada).
- `app.component.html`: `<app-toast />` en el root → cualquier pantalla avisa sin registrar nada.
- `cargo-modal.component.ts`: el **éxito** avisa con un toast. El **error no**: el modal ya lo
  muestra con su `role="alert"` y su motivo (409 / 400 / otro); duplicarlo sería avisar dos veces.

### B · Espaciado

- `tokens.scss`: la escala **ya existía** (1, 2, 3, 4, 5, 6, 8, 10, 12 en múltiplos de 4px). Se
  completan los huecos (**7 = 28px**, **9 = 36px**) sin mover ningún valor existente.
- `frontend/app/DESIGN.md`: la escala, el anillo de foco, el movimiento y el toast, documentados.
- Las **utilidades** de espaciado (`p-4`, `gap-3`, `m-6`…) las emite **Tailwind v4** con la misma
  escala de 4px: no se duplican en `utilities.scss` (ver desviación 2).

### C · Anillo de foco

- `tokens.scss`: `--focus-ring-color` (el color de la marca), `--focus-ring-width: 2px`,
  `--focus-ring-offset: 2px`, `--focus-ring-style: solid`.
- `theme/focus-ring.scss`: regla global `:focus-visible` + los controles de PrimeNG, a los que
  les quita la sombra de foco de la librería (dos anillos superpuestos no son un anillo).
- `theme/variables.scss`: `@use "./focus-ring";`

### D · Movimiento

- `tokens.scss`: `--duration-fast/normal/slow` (100/200/300 ms), `--ease-standard/out/bounce` y
  transiciones **por propiedad** (`--transition-color`, `--transition-transform`, …).
- `theme/base.scss`: botones, cards, modales e inputs.
- `theme/animations.scss`: `fadeIn`, `slideUp`, `slideDown`, `spinIn`, `shimmer` **y el apagado por
  `prefers-reduced-motion`** (WCAG 2.3.3): el resultado es el mismo, sin el viaje.
- `theme/variables.scss`: `@use "./animations";`

### E · Accesibilidad en CI

- `vitest-axe` instalado; `src/test-setup.ts` registra los matchers.
- `shared/testing/a11y-test.helper.ts`: `expectA11yClean()` (dice **qué regla** falló y cuántos
  nodos la violan) y `a11yViolations()`, el **contrapeso**: comprueba que un control sin nombre
  accesible **cae**.
- Specs `*.a11y.spec.ts` de Button, Card y Table.
- `package.json`: `test:a11y` y `test:ci` = vitest (sin los a11y) + a11y + lint.

---

## 4. Mutaciones (4/4 matan)

Restauración con copia byte a byte desde `/tmp/mut505/*.bak` (**no** `git checkout --`, que se
lleva cambios sin commitear). Al final, `grep -rn "MUTACION" src/ e2e/` → **0 coincidencias**.

| # | Mutación | Cómo se midió | Test exacto que cayó |
|---|---|---|---|
| **M1** | Quitar `<app-toast />` del `AppComponent` | `vitest run src/app/app.component.spec.ts` → 2 tests, **1 failure** | `AppComponent > monta el toast en el root (ODD 505, A.3)` (`expected null to be truthy`) |
| **M2** | `--space-4: 16px` → `--space-4: 0.5rem` | `vitest run src/theme/design-system.spec.ts` → 9 tests, **1 failure** | `la escala de espaciado > declara la escala completa de 4px` |
| **M3** | Quitar el `outline` de `:focus-visible` | unit → **1 failure**; E2E → **2 failures** | `el anillo de foco > la regla :focus-visible usa los tokens del anillo` **y** `j8 > el foco por teclado se ve: anillo de 2px con el color de la marca` (esperaba 2px, recibió 1px: el del navegador) |
| **M4** | Quitar el binding `[ariaLabel]` del botón base | `vitest run button.component.a11y.spec.ts` → 3 tests, **1 failure** | `un boton de solo icono con aria-label no tiene violaciones` → `button-name: Buttons must have discernible text (1 nodo/s)`. El contrapeso (`un boton de solo icono sin nombre accesible SI se reporta`) siguió verde: el gate tiene dientes. |

**M4 se reinterpreta.** El ODD la describe como "quitar `expectA11yClean()`": quitar una aserción
hace que el test **pase**, no que caiga, así que esa mutación no demuestra nada. La versión
ejecutada rompe el **componente** (el botón se queda sin nombre accesible) y comprueba que el
test de a11y **cae**: eso sí demuestra que el gate ve las violaciones.

**M3 necesitó el E2E.** Con el `outline` de `:focus-visible` fuera, el botón del login seguía
mostrando el anillo de PrimeNG (3px): el test unitario de contrato sí cayó, pero la medición en
navegador fue la que probó que el anillo **del sistema** había desaparecido.

---

## 5. Hallazgos (y correcciones colaterales)

1. **`MessageService` no es `providedIn: 'root'` en PrimeNG 21.** Sin provee'rlo en `main.ts`, el
   `<p-toast>` se monta y no muestra nada (`NG0201`). La instancia tiene que ser compartida entre
   el `ToastService` y el `<p-toast>` del root.
2. **El anillo de foco de PrimeNG gana por cascada.** El styled engine emite
   `.p-inputtext:enabled:focus` (3px) en runtime, después de la hoja global: ninguna regla global
   sin `!important` lo pisa. El `!important` **localizado** repite el precedente de `base.scss`
   (que ya lo usa en la card y en el piso de toque) por la misma razón concreta.
3. **El dev server sirve el bundle viejo.** Los E2E de un cambio de hoja de estilos hay que
   medirlos contra el servidor reiniciado: con el bundle anterior, el E2E del anillo daba verde
   sin que la regla existiera.
4. **El E2E del foco era intermitente** (pasaba solo, fallaba en la suite completa): la app monta
   la pantalla de forma asíncrona y el `Tab` caía en el `<body>`. Se espera al formulario y se
   descarta el `body` y los controles de PrimeNG (que tienen su propia regla).
5. **El guard de tokens marca `#505` como color crudo** en los títulos de los tests, igual que
   `#504`: los títulos dicen "ODD 505" (sin almohadilla).

---

## 6. Desviaciones del ODD (con motivo)

| # | El ODD pide | Se hizo | Por qué |
|---|---|---|---|
| 1 | **B.1**: escala `--space-1` … `--space-10` con `--space-10: 3rem` | La escala que ya tenía el repo (1, 2, 3, 4, 5, 6, 8, 10, 12 en múltiplos de 4px), **completada** con 7 y 9 | Cambiar `--space-10` de 40px a 48px habría movido un token en uso en toda la app — y el propio ODD prohíbe cambiar tokens existentes ("No cambiar tokens existentes (solo expandir spacing scale)"). |
| 2 | **B.2**: `.p-*`, `.gap-*`, `.m-*` en `utilities.scss` | **No se duplican**: las emite Tailwind v4 con la misma escala de 4px | Dos fuentes de verdad para el mismo valor terminan discrepando, y Tailwind se carga después y gana. El contrato se verifica con un test (`las utilidades de espaciado las da Tailwind, con la misma escala`). |
| 3 | **A.1/A.2**: `ToastComponent` con signal de mensajes + `ToastService.setToastComponent()` | El `ToastService` habla con el `MessageService` de PrimeNG; el `<p-toast>` del root los escucha | El mecanismo del ODD tiene una ventana donde el feedback se pierde en silencio: si el componente no está registrado, `success()` es un no-op con un `console.warn`. Con el `MessageService` (root) no existe esa ventana. **La API pública es la misma** (`success`/`info`/`warn`/`error`/`add`/`clear`). |
| 4 | **E.1**: `jest-axe` | **`vitest-axe`** | El runner del repo es Vitest, no Jest: `jest-axe` no tiene versión para Vitest y su matcher espera el contexto de Jest. `vitest-axe` es el port para Vitest y expone el mismo `axe` + `toHaveNoViolations`. |
| 5 | **A.4**: toast de éxito **y** de error en el modal | Toast de **éxito**; el error sigue inline en el modal | El modal ya muestra el error con `role="alert"` y con su motivo (409 / 400 / otro). Avisarlo dos veces es ruido, y el toast es para el caso en que la pantalla **no** puede mostrarlo. |
| 6 | **C.2**: `:focus-visible` sin `!important` | `!important` **localizado** en la regla global y en los controles de PrimeNG | El styled engine de PrimeNG emite en runtime y su anillo (3px) gana por cascada; sin esto el anillo del sistema no llega a verse. Precedente: `base.scss` ya lo hace en dos reglas, con el motivo escrito. |
| 7 | **D.1**: `--transition-fast: all 100ms …` | Duración y easing por separado, y transiciones **por propiedad** (`--transition-color`, `--transition-transform`, …) | `transition: all` se lleva por delante cambios que nadie pidió (un color de estado, un ancho) y hace que la interfaz parezca más lenta de lo que es. |
| 8 | **A.1**: `life` por defecto 3000 ms | **5000 ms** | 3 s no alcanza a leerse un mensaje de dos líneas; la banda de la casa para feedback efímero es 4-8 s. Los errores y avisos no tienen vida: se quedan hasta que se cierran. |
| 9 | **A.1**: `backdrop-filter: blur(4px)` en el toast | Fondo opaco | Un panel de vidrio encima del contenido se come el contraste del texto y el sistema ya tiene superficies opacas para eso. |

---

## 7. Comandos para reproducir

```bash
cd frontend/app
pnpm test:ci          # vitest + a11y + lint
pnpm check:tokens     # guard de tokens
pnpm format:check && pnpm build
npx playwright test j8-design-system.spec.ts --reporter=list   # anillo de foco + toast
```
