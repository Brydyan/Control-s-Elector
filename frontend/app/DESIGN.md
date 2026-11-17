# Design System — Control's Elector

La capa de tema vive en `src/theme/` y es la **única** que puede declarar color, radio,
sombra o duración. El guard (`pnpm check:tokens`) lo hace cumplir: una referencia a un
token que nadie declara, o un color crudo fuera de `src/theme/`, rompen la compuerta.

```
src/theme/
  tokens.scss       ← los tokens (color, espaciado, tipografía, radio, sombra, foco, movimiento)
  fonts.scss        ← las familias
  base.scss         ← reglas globales (lienzo, card, piso de toque, transiciones)
  typography.scss   ← la escala tipográfica
  utilities.scss    ← clases de superficie, texto, borde, radio y sombra
  tables.scss       ← la tabla (cabecera, fila, celda, paginador)
  focus-ring.scss   ← el anillo de foco
  animations.scss   ← los keyframes y el apagado por prefers-reduced-motion
  tailwind.css      ← Tailwind v4 (utilidades de layout) + el plugin de PrimeUI
```

---

## Espaciado (spacing scale)

Baseline: **4px**. Los tokens van en `rem`/`px` dentro de `tokens.scss` y la escala
completa es `--space-1` … `--space-12`.

| Token | Valor | Uso típico |
|---|---:|---|
| `--space-1` | 4px | micro-separación, icono y texto |
| `--space-2` | 8px | chips, separación corta |
| `--space-3` | 12px | separación estándar en línea |
| `--space-4` | 16px | padding de botón y de tarjeta |
| `--space-5` | 20px | separación de bloque |
| `--space-6` | 24px | margen de sección, padding de contenedor |
| `--space-7` | 28px | separación grande |
| `--space-8` | 32px | separación entre secciones |
| `--space-9` | 36px | separación muy grande |
| `--space-10` | 40px | margen de bloque grande |
| `--space-12` | 48px | margen de contenedor de página |

**Patrón:** se usa siempre el token, nunca un `px` suelto en un componente.

**Utilidades:** las emite **Tailwind v4** (`p-4`, `gap-3`, `m-6`, `my-2`…) con la misma
escala de 4px. No se duplican en `utilities.scss`: dos fuentes para el mismo valor
terminan discrepando, y Tailwind se carga después y gana.

---

## Anillo de foco

El foco tiene que verse (WCAG 2.4.7): es la única forma de que quien navega con
teclado sepa dónde está.

| Token | Valor |
|---|---|
| `--focus-ring-color` | `var(--color-brand-primary)` |
| `--focus-ring-width` | `2px` |
| `--focus-ring-offset` | `2px` |
| `--focus-ring-style` | `solid` |

La regla vive en `src/theme/focus-ring.scss`: alcanza a todo lo enfocable y le quita
la sombra de foco de PrimeNG a los controles de la librería, para que no queden dos
anillos superpuestos.

---

## Movimiento

| Token | Valor | Uso |
|---|---|---|
| `--duration-fast` | 100ms | cambio de color, foco |
| `--duration-normal` | 200ms | hover, sombras, modal |
| `--duration-slow` | 300ms | entrada de contenido |
| `--ease-standard` | `cubic-bezier(0.4, 0, 0.2, 1)` | movimiento general |
| `--ease-out` | `cubic-bezier(0.22, 1, 0.36, 1)` | entrada |
| `--ease-bounce` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | rebote corto |

**Reglas:**

- Se anima **por propiedad**, nunca `transition: all`: un `all` se lleva por delante
  cambios que nadie pidió.
- `prefers-reduced-motion: reduce` apaga el movimiento sin cambiar el resultado
  (`src/theme/animations.scss`).
- Los keyframes del sistema son `fadeIn`, `slideUp`, `slideDown`, `spinIn` y `shimmer`.

---

## Toast (feedback)

- Vive en el root (`<app-toast>` en `app.component.html`), así que cualquier pantalla
  avisa sin registrar nada.
- `ToastService` habla con el `MessageService` de PrimeNG: `success` e `info` se
  cierran solos a los **5 s**; `warn` y `error` **se quedan** hasta que se cierran
  (un error que desaparece solo es un error que nadie vio).
- El error **no** se duplica en un toast cuando la pantalla ya lo muestra con su
  `role="alert"`: el aviso va una vez, con su motivo.

---

## Accesibilidad

- `pnpm test:a11y` corre los specs `*.a11y.spec.ts` (Button, Card, Table) con
  `vitest-axe`: **0 violaciones**.
- El helper `src/app/shared/testing/a11y-test.helper.ts` dice **qué regla** falló y
  cuántos nodos la violan; y tiene su contrapeso (`a11yViolations`), que comprueba
  que un control sin nombre accesible **cae**.
- jsdom no calcula layout: el contraste se mide en el navegador (E2E), no en el unit.
