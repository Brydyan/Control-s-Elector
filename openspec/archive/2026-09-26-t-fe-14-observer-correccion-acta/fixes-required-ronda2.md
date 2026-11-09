# Fixes Required — T-FE-14 Ronda 2

**Ejecutor**: Agent (Ronda 2)  
**Fecha**: 2026-11-04  
**Estado**: 5 CRITICAL + 1 REGRESIÓN (Mutación 1)

---

## CRITICAL Fixes Bloqueantes

### FIX-1: Quitar `aria-live="polite"` de banner alertable

**Archivo**: `correccion-acta.page.html`  
**Línea**: 44–46  
**Cambio requerido**: Eliminar `aria-live="polite"` O cambiar `role="alert"` a `role="status"`

**Opción A (conservador)** — Remover aria-live (el `role="alert"` ya implica `aria-live="assertive"`):
```html
<div
  class="dispute-banner"
  role="alert"
  data-testid="dispute-banner"
>
```

**Opción B (mejor para ux)** — Cambiar a `role="status"` + `aria-live="polite"` para no interrumpir cada keystroke:
```html
<div
  class="dispute-banner"
  role="status"
  aria-live="polite"
  data-testid="dispute-banner"
>
```

**Impacto**: Violación WCAG 4.1.3 (Status Messages). Algunos lectores de pantalla anuncian dos veces o resuelven conflicto incompatiblemente.

---

### FIX-2: Crear elemento con `id="help-sufragantes"` y vincular

**Archivo**: `correccion-acta.page.html`  
**Referencia rota**: Línea 134 (`aria-describedby="help-sufragantes"`)

**Cambio requerido**: Añadir elemento describedby visible o oculto:

**Opción A (texto visible)** — Texto de ayuda bajo el input:
```html
<div class="total-row">
  <label for="total-sufragantes" class="total-label">
    Total Sufragantes
    <span class="required">*</span>
  </label>
  <input
    id="total-sufragantes"
    type="number"
    min="0"
    max="1000"
    step="1"
    [value]="totalSufragantes()"
    (ngModelChange)="actualizarTotalSufragantes($event)"
    [disabled]="submitting()"
    aria-label="Total de sufragantes"
    aria-describedby="help-sufragantes"
    class="input-total"
    data-testid="input-total-sufragantes"
  />
  <span id="help-sufragantes" class="help-text">
    Entre 1 y 500 sufragantes (CNE).
  </span>
</div>
```

**Opción B (aria-live para screen readers)** — Oculto visualmente:
```html
<span id="help-sufragantes" class="sr-only">
  Rango: 1 a 500 sufragantes según norma CNE.
</span>
```

**Impacto**: Violación WCAG 1.3.1 (Info and Relationships). Usuarios con lectores de pantalla pierden restricción de rango.

---

### FIX-3: Añadir texto de estado cuando botón está deshabilitado

**Archivo**: `correccion-acta.page.html`  
**Ubicación**: Línea 233–243 (botón "Enviar Corrección")

**Cambio requerido**: Mostrar razón por qué está bloqueado:

```html
<div class="form-actions">
  <!-- Explicación inline cuando checksum es inválido -->
  @if (!checksumValido) {
    <div class="button-explanation" role="alert">
      <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
      <span>
        @if (esCpccs()) {
          La suma de votos debe ser ≤ {{ totalSufragantes() * 3 }}.
        } @else {
          La suma de votos debe ser exactamente {{ totalSufragantes() }}.
        }
      </span>
    </div>
  }
  
  <app-button
    variant="primary"
    size="md"
    (click)="enviarCorreccion()"
    [disabled]="botonEnvioBloqueado"
    [loading]="submitting()"
    data-testid="btn-enviar-correccion"
  >
    <i class="pi pi-check" aria-hidden="true"></i>
    @if (submitting()) { Enviando... } @else { Enviar Corrección }
  </app-button>
  <!-- ... resto del código ... -->
</div>
```

**Impacto**: Violación WCAG 1.4.3 (Contrast) + heurística de visibilidad de estado. Veedor no entiende por qué botón está muerto.

---

### FIX-4: Cambiar `(change)` a `(ngModelChange)` en inputs de totales

**Archivo**: `correccion-acta.page.html`  
**Líneas**: 131 (Total Sufragantes), 149 (Votos Blancos), 166 (Votos Nulos)

**Cambio requerido**:

```html
<!-- Total Sufragantes (línea ~124-138) -->
<div class="total-row">
  <label for="total-sufragantes" class="total-label">
    Total Sufragantes
    <span class="required">*</span>
  </label>
  <input
    id="total-sufragantes"
    type="number"
    min="0"
    max="1000"
    step="1"
    [(ngModel)]="totalSufragantesValue"
    (ngModelChange)="actualizarTotalSufragantes($event)"
    [disabled]="submitting()"
    aria-label="Total de sufragantes"
    aria-describedby="help-sufragantes"
    class="input-total"
    data-testid="input-total-sufragantes"
  />
</div>

<!-- Votos Blancos (línea ~140-154) -->
<div class="total-row">
  <label for="votos-blancos" class="total-label">Votos Blancos</label>
  <input
    id="votos-blancos"
    type="number"
    min="0"
    [max]="MAX_VOTOS_ABSOLUTO"
    step="1"
    [(ngModel)]="votosBlancoValue"
    (ngModelChange)="actualizarVotosBlancos($event)"
    [disabled]="submitting()"
    aria-label="Votos blancos"
    class="input-total"
    data-testid="input-votos-blancos"
  />
</div>

<!-- Votos Nulos (línea ~156-170) -->
<div class="total-row">
  <label for="votos-nulos" class="total-label">Votos Nulos</label>
  <input
    id="votos-nulos"
    type="number"
    min="0"
    [max]="MAX_VOTOS_ABSOLUTO"
    step="1"
    [(ngModel)]="votosNuloValue"
    (ngModelChange)="actualizarVotosNulos($event)"
    [disabled]="submitting()"
    aria-label="Votos nulos"
    class="input-total"
    data-testid="input-votos-nulos"
  />
</div>
```

**Cambios en TS** (`correccion-acta.page.ts`):
```typescript
// Añadir signals para two-way binding
readonly totalSufragantesValue = signal(0);
readonly votosBlancoValue = signal(0);
readonly votosNuloValue = signal(0);

// Actualizar en procesarActaCargada
private procesarActaCargada(acta: ActaResponse): void {
  if (acta.estado !== 'EN_DISPUTA') {
    this.router.navigate(['/observer/mis-jrvs']);
    return;
  }

  this.motivoDisputa.set(acta.motivoDisputa || null);
  this.coordinadorNombre.set(acta.coordinadorNombre || null);
  this.urlEvidencia.set(acta.urlEvidencia || null);
  this.totalSufragantes.set(acta.totalSufragantes);
  this.totalSufragantesValue.set(acta.totalSufragantes); // ← NUEVO
  this.votosBlancos.set(acta.votosBlancos);
  this.votosBlancoValue.set(acta.votosBlancos); // ← NUEVO
  this.votosNulos.set(acta.votosNulos);
  this.votosNuloValue.set(acta.votosNulos); // ← NUEVO
  // ...
}
```

**Impacto**: Violación de especificación "Recalcula checksum inmediatamente ante cada pulsación." Actualmente, checksum se actualiza al blur, no en keystroke.

---

### FIX-5: Mostrar toast/aviso antes de redirigir si acta no es EN_DISPUTA

**Archivo**: `correccion-acta.page.ts`  
**Línea**: 177–181 (método `procesarActaCargada`)

**Cambio requerido**: Inyectar servicio de toast e invocar:

```typescript
// En clase (agregar import y inject)
import { ToastService } from '../../../../shared/services'; // o la ruta correcta

export class CorreccionActaPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverApi);
  private readonly toastService = inject(ToastService); // ← NUEVO
  // ...

  private procesarActaCargada(acta: ActaResponse): void {
    if (acta.estado !== 'EN_DISPUTA') {
      this.toastService.info('El acta ya no está en disputa.'); // ← NUEVO
      this.router.navigate(['/observer/mis-jrvs']);
      return;
    }
    // ... resto ...
  }
}
```

**Impacto**: Violación WCAG 4.1.3 (Status Messages). Veedor no entiende por qué fue redirigido.

---

## Regresión Detectada

### REG-1: Mutación 1 No Mata Test — Cobertura Falsa

**Problema**: Test de `botonEnvioBloqueado` valida solo el GETTER, no el binding en template.

**Evidencia**:
```typescript
// Test actual (corre pero no toca template)
it('should mark checksum invalid if sufragantes is 0', () => {
  fixture.detectChanges();
  component.totalSufragantes.set(0);

  expect(component.checksumValido).toBe(false);
  expect(component.botonEnvioBloqueado).toBe(true); // ← getter, no template
});
```

**Cambio requerido**: Agregar test que valide el binding en template:

```typescript
it('should disable button in template when checksum is invalid', () => {
  fixture.detectChanges();
  component.totalSufragantes.set(0);
  fixture.detectChanges();

  const submitButton = fixture.debugElement.query(
    By.css('app-button[data-testid="btn-enviar-correccion"]')
  );
  expect(submitButton.componentInstance.disabled).toBe(true);
});
```

**Impacto**: Falsa sensación de seguridad. Cambios futuros en template podrían romper funcionalidad sin detectarse.

---

## Resumen de Acciones

| FIX ID | Archivo | Tipo | Prioridad | Bloqueante |
|--------|---------|------|-----------|-----------|
| FIX-1 | correccion-acta.page.html | ARIA | CRITICAL | ✅ |
| FIX-2 | correccion-acta.page.html | ARIA + Visibility | CRITICAL | ✅ |
| FIX-3 | correccion-acta.page.html | UX/Accessibility | CRITICAL | ✅ |
| FIX-4 | correccion-acta.page.html + .ts | Functional | CRITICAL | ✅ |
| FIX-5 | correccion-acta.page.ts | UX/Feedback | CRITICAL | ✅ |
| REG-1 | correccion-acta.page.spec.ts | Test Coverage | MAJOR | ✅ |

**Estimación**: 2–3 horas de trabajo + 1 hora de testing.

---

## Nota sobre apply-progress.md

El reporte de apply-progress.md afirma que el código está "listo para review y merge" pero:

1. **Ignore explícitamente los hallazgos de Ronda 1** — No menciona los 5 CRITICAL
2. **Dice que mutaciones se verificaron**, pero Ronda 2 demuestra que Mutación 1 NO mata test (regresión)
3. **No ejecutó live mutation testing** — Parece que solo simuló o copió resultados

Este es un indicio de que el reporte de implementación fue completado sin considerar los hallazgos críticos de accesibilidad/UX.
