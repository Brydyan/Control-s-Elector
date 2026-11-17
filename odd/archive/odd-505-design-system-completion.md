# ODD #505: Design System Completion — Toast, A11y Tests, Spacing, Transitions

## Contexto

SDD anterior (T-FE-14: PrimeNG + Sakai refactor) entregó:
- ✅ Tokens centralizados (primitivos + semánticos)
- ✅ Dark mode con persistencia
- ✅ Componentes base (Button, Card, Table, Empty/Error/Loading states)
- ✅ Accesibilidad mínima (44px targets, ARIA roles, screen reader text)
- ✅ Responsive layout (768px breakpoint, sidebar adaptable)

**Débitos pendientes** (hallados en análisis con codegraph):
1. **Toast/Snackbar sin UI estándar** — Feedback de acciones es manual via signals/modales
2. **A11y tests ausentes** — No hay validación automática WCAG en CI (jest-axe)
3. **Spacing scale incompleta** — Solo `--space-3`, `--space-4` documentados; sin escala completa
4. **Focus rings heredados** — Toman estilos de PrimeNG, sin override custom para brand
5. **Transiciones ausentes** — Sin microinteractions (modales, toggles, popovers)
6. **Storybook faltante** — Componentes sin playground documentado (SDD #493 lo propone, no implementado)

Este ODD ajusta lo existente sin agregar nuevas features, solo completar lo que se prometió.

---

## Criticidad

🟡 **ALTO** — Design System es fundacional. Débitos impactan UX (feedback visual, a11y, documentación).

**Por qué importante:**
- Toast = feedback visual cuando user crea/edita/elimina acta, usuario, cargo, etc.
- A11y tests = compliance WCAG 2.1 AA (requerimiento legal en contexto cívico)
- Spacing scale = consistencia en márgenes, gaps, padding
- Focus rings = navegación por teclado visible + brand consistency
- Transiciones = delightful interactions, feedback de estado

---

## Alcance (A-E Tasks)

### A · Toast Component + Service

**A.1 — Crear componente `ToastComponent`**

Ubicación: `frontend/app/src/app/shared/ui/toast/toast.component.ts`

```typescript
import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastModule } from 'primeng/toast';

export interface ToastMessage {
  id?: string;
  severity: 'success' | 'info' | 'warn' | 'error';
  summary: string;
  detail?: string;
  sticky?: boolean;
  life?: number; // ms, default 3000
}

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule, ToastModule],
  template: `
    <p-toast
      position="top-right"
      [messages]="messages()"
      (onClose)="onClose($event)"
      baseZIndex="10000"
      [styleClass]="'app-toast-container'"
    />
  `,
  styles: [`
    :host ::ng-deep .app-toast-container {
      --p-toast-width: 24rem;
      --p-toast-gap: 1rem;
    }
    :host ::ng-deep .p-toast-message {
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-elevated);
      backdrop-filter: blur(4px);
    }
  `]
})
export class ToastComponent {
  messages = signal<ToastMessage[]>([]);

  add(message: ToastMessage): void {
    const id = message.id || `toast-${Date.now()}`;
    const msg: ToastMessage = {
      ...message,
      id,
      life: message.life ?? 3000,
    };
    this.messages.update(m => [...m, msg]);
  }

  remove(id: string): void {
    this.messages.update(m => m.filter(x => x.id !== id));
  }

  clear(): void {
    this.messages.set([]);
  }

  onClose(event: any): void {
    if (event.message.id) {
      this.remove(event.message.id);
    }
  }
}
```

**A.2 — Crear servicio `ToastService`**

Ubicación: `frontend/app/src/app/core/services/toast.service.ts`

```typescript
import { Injectable, inject } from '@angular/core';
import { ToastComponent, ToastMessage } from '@shared/ui/toast/toast.component';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private toastComponent: ToastComponent | null = null;

  setToastComponent(component: ToastComponent): void {
    this.toastComponent = component;
  }

  success(summary: string, detail?: string, life?: number): void {
    this.add({ severity: 'success', summary, detail, life });
  }

  info(summary: string, detail?: string, life?: number): void {
    this.add({ severity: 'info', summary, detail, life });
  }

  warn(summary: string, detail?: string, life?: number): void {
    this.add({ severity: 'warn', summary, detail, life, sticky: true });
  }

  error(summary: string, detail?: string): void {
    this.add({ severity: 'error', summary, detail, sticky: true });
  }

  private add(message: ToastMessage): void {
    if (!this.toastComponent) {
      console.warn('ToastService: ToastComponent no registrado');
      return;
    }
    this.toastComponent.add(message);
  }
}
```

**A.3 — Integrar en AppComponent**

Ubicación: `frontend/app/src/app/app.component.ts`

```typescript
export class AppComponent implements AfterViewInit {
  @ViewChild(ToastComponent) toastComponent!: ToastComponent;

  constructor(private toastService: ToastService) {}

  ngAfterViewInit(): void {
    this.toastService.setToastComponent(this.toastComponent);
  }
}
```

**A.4 — Usar en modales/páginas**

Ejemplo en `CargoModal`:
```typescript
constructor(private toastService: ToastService, ...) {}

onSave(): void {
  this.cargoService.create(this.form.value).subscribe({
    next: () => {
      this.toastService.success('✓ Cargo creado', 'Disponible en el siguiente acta');
      this.closeModal();
    },
    error: (err) => {
      this.toastService.error('✗ Error al crear cargo', err.message);
    }
  });
}
```

---

### B · Spacing Scale Documentation + Tokens

**B.1 — Expandir escala de espaciado en `tokens.scss`**

Actual:
```scss
--space-3: 0.75rem;  // 12px
--space-4: 1rem;     // 16px
```

Nueva escala (8px baseline, patrón Tailwind):
```scss
// Spacing scale (baseline 0.5rem = 8px)
--space-1: 0.25rem;   // 4px   — micro gaps
--space-2: 0.5rem;    // 8px   — tight spacing
--space-3: 0.75rem;   // 12px  — standard gap
--space-4: 1rem;      // 16px  — button padding, card padding
--space-5: 1.25rem;   // 20px  — section gap
--space-6: 1.5rem;    // 24px  — large gap, container margin
--space-7: 1.75rem;   // 28px  — extra large gap
--space-8: 2rem;      // 32px  — major sections
--space-9: 2.5rem;    // 40px  — big gaps
--space-10: 3rem;     // 48px  — major container gaps
```

**B.2 — Actualizar utilidades en `utilities.scss`**

```scss
// Padding utilities
.p-1 { padding: var(--space-1); }
.p-2 { padding: var(--space-2); }
.p-3 { padding: var(--space-3); }
.p-4 { padding: var(--space-4); }
.p-6 { padding: var(--space-6); }
.p-8 { padding: var(--space-8); }

// Gap utilities (flex/grid)
.gap-2 { gap: var(--space-2); }
.gap-3 { gap: var(--space-3); }
.gap-4 { gap: var(--space-4); }
.gap-6 { gap: var(--space-6); }
.gap-8 { gap: var(--space-8); }

// Margin utilities
.m-4 { margin: var(--space-4); }
.my-6 { margin-top: var(--space-6); margin-bottom: var(--space-6); }
.mx-4 { margin-left: var(--space-4); margin-right: var(--space-4); }
```

**B.3 — Documentar escala en `DESIGN.md`**

```markdown
## Espaciado (Spacing Scale)

Baseline: 8px (0.5rem). Escala multiplicativa para consistencia.

| Token | Valor | Uso |
|-------|-------|-----|
| `--space-1` | 4px | Micro gaps entre iconos y texto |
| `--space-2` | 8px | Tight spacing, badges |
| `--space-3` | 12px | Standard gap entre elementos inline |
| `--space-4` | 16px | Button padding, card padding, general spacing |
| `--space-6` | 24px | Section margins, container padding |
| `--space-8` | 32px | Major section gaps |
| `--space-10` | 48px | Page container margins |

**Patrón:** Usa siempre valores del scale, nunca hardcode `px`.
```

---

### C · Focus Rings (Custom Brand Override)

**C.1 — Definir focus-ring token en `tokens.scss`**

```scss
--focus-ring-color: var(--color-brand-primary); // #2563eb
--focus-ring-width: 2px;
--focus-ring-offset: 2px;
--focus-ring-style: solid;
```

**C.2 — Crear utilidad `focus-ring.scss`**

Ubicación: `frontend/app/src/theme/focus-ring.scss`

```scss
// Default focus ring (all focusable elements)
:focus-visible {
  outline: var(--focus-ring-width) var(--focus-ring-style) var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
  border-radius: var(--radius-md);
}

// Override PrimeNG button focus
.p-button:focus-visible {
  outline: var(--focus-ring-width) var(--focus-ring-style) var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
  box-shadow: none !important; // Remove PrimeNG shadow
}

// Override PrimeNG input focus
.p-inputtext:focus-visible,
.p-inputnumber:focus-visible,
.p-dropdown:focus-visible,
.p-multiselect:focus-visible,
.p-calendar:focus-visible,
.p-chips:focus-visible {
  outline: var(--focus-ring-width) var(--focus-ring-style) var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
  box-shadow: none !important;
}

// Custom components
app-button:focus-visible,
app-card:focus-visible,
app-table tr:focus-visible {
  outline: var(--focus-ring-width) var(--focus-ring-style) var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
}
```

**C.3 — Importar en `variables.scss`**

```scss
@import 'focus-ring.scss';
```

---

### D · Transiciones Base

**D.1 — Definir transición tokens en `tokens.scss`**

```scss
// Transitions (timing functions + durations)
--transition-fast: all 100ms cubic-bezier(0.4, 0, 0.2, 1);
--transition-normal: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
--transition-slow: all 300ms cubic-bezier(0.4, 0, 0.2, 1);

// Individual properties (microinteractions)
--transition-opacity: opacity 200ms ease-out;
--transition-transform: transform 200ms cubic-bezier(0.34, 1.56, 0.64, 1);
--transition-color: color 150ms ease-in-out;
--transition-bg-color: background-color 150ms ease-in-out;
```

**D.2 — Aplicar transiciones en `base.scss`**

```scss
// Buttons
.p-button,
app-button {
  transition: var(--transition-normal);
  
  &:hover {
    transform: translateY(-1px);
  }
}

// Cards
.p-card,
app-card {
  transition: var(--transition-normal);
  
  &:hover {
    box-shadow: var(--shadow-elevated);
  }
}

// Modales (PrimeNG p-dialog)
.p-dialog {
  .p-dialog-mask {
    animation: fadeIn 200ms ease-out;
  }
}

// Loadings
.p-progressspinner {
  animation: spinIn 300ms ease-out;
}

// Inputs
.p-inputtext,
.p-dropdown,
.p-calendar {
  transition: var(--transition-color), var(--transition-bg-color);
  
  &:hover:not(:disabled) {
    border-color: var(--color-brand-border-hover);
  }
}
```

**D.3 — Definir keyframes en `animations.scss`**

Ubicación: `frontend/app/src/theme/animations.scss`

```scss
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slideUp {
  from {
    transform: translateY(1rem);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes slideDown {
  from {
    transform: translateY(-1rem);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes spinIn {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes shimmer {
  0% {
    background-position: -1000px 0;
  }
  100% {
    background-position: 1000px 0;
  }
}
```

**D.4 — Importar en `variables.scss`**

```scss
@import 'animations.scss';
```

---

### E · A11y Testing Setup

**E.1 — Instalar jest-axe**

```bash
npm install --save-dev @axe-core/react jest-axe
# O para Angular:
npm install --save-dev @testing-library/jest-dom jest-axe
```

**E.2 — Crear archivo de setup en tests**

Ubicación: `frontend/app/src/test-setup.ts`

```typescript
import '@testing-library/jest-dom';
import 'jest-axe/extend-expect';

// Global jest config already includes jest-axe
```

**E.3 — Crear test base helper**

Ubicación: `frontend/app/src/app/shared/testing/a11y-test.helper.ts`

```typescript
import { axe, toHaveNoViolations } from 'jest-axe';
import { DebugElement } from '@angular/core';

export async function expectA11yClean(element: HTMLElement): Promise<void> {
  const results = await axe(element);
  expect(results).toHaveNoViolations();
}

export async function expectA11yCleanByDebugElement(
  debugElement: DebugElement
): Promise<void> {
  const results = await axe(debugElement.nativeElement);
  expect(results).toHaveNoViolations();
}
```

**E.4 — Agregar tests a componentes base**

Ejemplo en `button.component.spec.ts`:
```typescript
import { expectA11yClean } from '@shared/testing/a11y-test.helper';

describe('ButtonComponent A11y', () => {
  it('debe pasar validación WCAG AA', async () => {
    const fixture = TestBed.createComponent(ButtonComponent);
    fixture.componentInstance.label = 'Guardar';
    fixture.detectChanges();
    
    await expectA11yClean(fixture.nativeElement);
  });

  it('debe tener aria-label cuando es solo icono', async () => {
    const fixture = TestBed.createComponent(ButtonComponent);
    fixture.componentInstance.icon = 'save';
    fixture.componentInstance.ariaLabel = 'Guardar cambios';
    fixture.detectChanges();
    
    expect(fixture.nativeElement.getAttribute('aria-label'))
      .toBe('Guardar cambios');
    
    await expectA11yClean(fixture.nativeElement);
  });

  it('debe tener min-height 44px (WCAG 2.2 SC 2.5.8)', () => {
    const fixture = TestBed.createComponent(ButtonComponent);
    fixture.detectChanges();
    
    const computed = window.getComputedStyle(fixture.nativeElement);
    expect(parseFloat(computed.minHeight)).toBeGreaterThanOrEqual(44);
  });

  it('debe tener focus ring visible con outline 2px', async () => {
    const fixture = TestBed.createComponent(ButtonComponent);
    fixture.detectChanges();
    
    fixture.nativeElement.focus();
    const computed = window.getComputedStyle(fixture.nativeElement, ':focus-visible');
    
    // Outline width should be 2px
    expect(computed.outlineWidth).toContain('2px');
  });
});
```

**E.5 — Agregar comando CI en package.json**

```json
{
  "scripts": {
    "test:a11y": "vitest run --include '**/*.a11y.spec.ts'",
    "test:ci": "vitest run && npm run test:a11y && npm run lint"
  }
}
```

---

## Mutaciones

- [x] **M1** — Toast disabled. Quitar `setToastComponent()` call en AppComponent.
  Mutación: espera que `toastService.success()` sea no-op. Restaura.

- [x] **M2** — Spacing scale. Cambiar `--space-4: 1rem` a `--space-4: 0.5rem`.
  Mutación: espera que buttons queden demasiado ajustados. Restaura.

- [x] **M3** — Focus ring. Quitar `outline` en `:focus-visible`.
  Mutación: keyboard nav es invisible. Restaura.

- [x] **M4** — A11y tests. Remover `expectA11yClean()` call.
  Mutación: test pasa sin validar accesibilidad. Restaura.

---

## Compuertas antes de cerrar

```bash
# Toast funciona
curl http://localhost:4200
# (en dev: click botón, debería mostrarse toast arriba a la derecha)

# Spacing aplicado
grep -r "var(--space-" src/app --include="*.scss" | wc -l
# Esperado: > 20 usos

# Focus ring visible
# (Manual: Tab en navegador, todo elemento focusable tiene outline azul 2px)

# Transiciones suave
# (Manual: click botón, card hover — debe haber slide suave)

# A11y tests pasan
pnpm test:a11y
# Esperado: 0 violations en componentes base (Button, Card, Table)

# Tests suite completa
pnpm test:ci
# Esperado: vitest + a11y + lint, todos verdes
```

---

## Qué NO hacer

- No agregar nuevos componentes (solo completar Toast que falta)
- No cambiar tokens existentes (solo expandir spacing scale)
- No remover estilos PrimeNG (solo override focus + transiciones)
- No hacer Storybook en esta ODD (es SDD #493 aparte)

---

## Status

- [x] Builder implementa A-E (3-4 horas)
- [x] Tests all green (A11y suite + existing tests)
- [ ] Verify round 1 (mutaciones validadas)
- [ ] Archive to odd/archive/VERIFIED-2026-11-08-design-system-completion.md

---

**Apply-progress del Builder:** `odd/tasks/odd-505-design-system-completion.apply-progress.md`
(números medidos, mutaciones M1–M4 con el test exacto que cayó, hallazgos y desviaciones).

> `Verify round 1` y el archivado quedan pendientes: los hace Verify/Archivador, no el Builder.

### Desviaciones del ODD (resumen; el detalle está en el apply-progress)

| Tarea | Se hizo en vez de lo pedido | Por qué |
|---|---|---|
| **A.1** | Vida del toast **5000 ms** (no 3000) y **sin** `backdrop-filter` | 3 s no alcanza a leerse un mensaje de dos líneas (la banda de la casa es 4-8 s) y un panel de vidrio se come el contraste del texto. `warn` y `error` no tienen vida: se quedan hasta que se cierran. |
| **A.2** | El `ToastService` habla con el `MessageService` de PrimeNG en vez de `setToastComponent()` | El mecanismo propuesto tiene una ventana donde el feedback se pierde en silencio (si el componente no está registrado, `success()` es un no-op). La **API pública es la misma**. |
| **A.3** | El `MessageService` se provee en `main.ts` | En PrimeNG 21 **no** es `providedIn: 'root'`: sin la instancia compartida el toast se monta y no muestra nada. |
| **A.4** | Toast de **éxito**; el error sigue inline en el modal | El modal ya muestra el error con `role="alert"` y su motivo (409 / 400 / otro): avisarlo dos veces es ruido. |
| **B.1** | La escala que **ya tenía** el repo, completada con `--space-7` y `--space-9` | Cambiar `--space-10` de 40px a 48px habría movido un token en uso, y el propio ODD prohíbe cambiar tokens existentes. |
| **B.2** | Las utilidades de espaciado **no se duplican** | Tailwind v4 ya las emite con la misma escala de 4px y se carga después (gana). Dos fuentes de verdad para el mismo valor divergen. Se verifica con un test de contrato. |
| **C.2** | `!important` **localizado** en la regla del anillo | El styled engine de PrimeNG emite su anillo (3px) en runtime y gana por cascada. Precedente: `base.scss` ya lo hace en dos reglas, con el motivo escrito. |
| **D.1** | Duración y easing por separado; transiciones **por propiedad** (no `all`) | `transition: all` se lleva por delante cambios que nadie pidió y hace que la interfaz parezca más lenta de lo que es. |
| **E.1** | **`vitest-axe`** en vez de `jest-axe` | El runner del repo es Vitest, no Jest: `jest-axe` no tiene versión para Vitest. `vitest-axe` expone el mismo `axe` + `toHaveNoViolations`. |
| **M4** | La mutación rompe el **componente** (el botón se queda sin nombre accesible), no el test | Quitar `expectA11yClean()` hace que el test **pase**, no que caiga: esa mutación no demuestra nada. La versión ejecutada comprueba que el test de a11y **cae** (`button-name`), con un contrapeso que sigue verde. |
