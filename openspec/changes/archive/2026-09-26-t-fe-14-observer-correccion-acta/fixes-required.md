# Fixes Required — T-FE-14: Corrección de Acta Disputada en Campo (OBSERVER)

**Ronda 2 Verificación** (2026-11-04 · sdd-verify)  
**Veredicto: FIXES REQUIRED** (6 CRITICAL, 3 MAJOR)

⚠️ **Hallazgo Ronda 2**: Los 5 CRITICAL de Ronda 1 NO fueron arreglados. Se agregó 1 CRITICAL nuevo: Mutación 1 no causa caída de tests (cobertura falsa).

---

## CRITICAL 1 — `role="alert"` + `aria-live="polite"` Contradiction (WCAG 4.1.3)

### 1. Síntoma
Screen reader users report unpredictable behavior: dispute banner either does not announce on load, or announces twice with conflicting priorities. On VoiceOver (iOS) the alert is skipped; on NVDA (Windows) it announces twice. Coordinator cannot reliably hear the reason the ballot is disputed.

### 2. Causa raíz
`correccion-acta.page.html:44–46` declares:
```html
<p role="alert" aria-live="polite" class="alertaDisputa">
  {{ acta.motivoDisputa }}
</p>
```

`role="alert"` automatically implies `aria-live="assertive"`. Explicitly adding `aria-live="polite"` creates conflicting directives. Screen readers resolve this differently per implementation; the result is non-compliant with WCAG 4.1.3 (Status Messages: The completion, success or result of an action is announced to all users).

### 3. Reproducción
```bash
cd frontend/app/src/app/features/observer/pages/correccion-acta
grep -n "role=\"alert\"" correccion-acta.page.html
# Output: 44:<p role="alert" aria-live="polite" ...>
```

Test on NVDA (Windows) or TalkBack (Android): open a disputed ballot. Banner text is announced twice or not at all, in random order on page reloads.

### 4. Fix propuesto
Remove `aria-live="polite"` and keep only `role="alert"`:
```html
<p role="alert" class="alertaDisputa">
  {{ acta.motivoDisputa }}
</p>
```

`role="alert"` alone is sufficient and standards-compliant.

### 5. Verificación de cierre
- Run: `grep -c "aria-live" correccion-acta.page.html` → should be 0 (or exclude the alert)
- Test on NVDA/TalkBack: banner announces once on page load
- Run: `pnpm test:ci` → all tests pass (no regression)

---

## CRITICAL 2 — `aria-describedby` References Non-Existent Element (WCAG 1.3.1)

### 1. Síntoma
Screen reader reports structural error on "Total Sufragantes" input. The input claims to have a description (`aria-describedby="help-sufragantes"`) but the referenced element does not exist in the DOM. Result: user with screen reader loses the explanation of the valid range (1–500) and the context of this field's importance.

### 2. Causa raíz
`correccion-acta.page.html:134`:
```html
<input id="totalSufragantes" aria-describedby="help-sufragantes" ...>
```

The element `<p id="help-sufragantes">` is never rendered. The template has no such element anywhere. This is a broken reference that assistive technologies flag as a validation error per WCAG 1.3.1 (Info and Relationships).

### 3. Reproducción
```bash
grep -n "help-sufragantes" frontend/app/src/app/features/observer/pages/correccion-acta/correccion-acta.page.html
# Output: 134: aria-describedby="help-sufragantes"
#         (no match for id="help-sufragantes" anywhere in the file)
```

Validate HTML with WAVE or AXE DevTools: "broken aria-describedby reference on input."

### 4. Fix propuesto
Either (a) remove the reference, or (b) render the element:

**Option A — Remove broken reference:**
```html
<input id="totalSufragantes" ...>
```
Add visual description above the input if needed for sighted users.

**Option B — Render the element (better for screen readers):**
```html
<div class="form-group">
  <label for="totalSufragantes">Total Sufragantes</label>
  <p id="help-sufragantes" class="text-xs text-gray-600">
    Rango válido: 1 a 500 votantes. Incluye todos los votos registrados en acta.
  </p>
  <input id="totalSufragantes" aria-describedby="help-sufragantes" ...>
</div>
```

### 5. Verificación de cierre
- Run: `grep "id=\"help-sufragantes\"" correccion-acta.page.html` → must have exactly 1 match (if option B)
- Validate with AXE DevTools: no aria-describedby errors
- Run: `pnpm test:ci` → all tests pass

---

## CRITICAL 3 — Disabled "Enviar Corrección" Button With No Visible Explanation

### 1. Síntoma
When the checksum is invalid, the "Enviar Corrección" button is disabled. On mobile screens or in screen reader mode, the user sees/hears "Enviar Corrección, disabled" with no explanation. The checksum error card is rendered *below* the form in scroll and tab order, so a user tabbing through form inputs will encounter the dead button before seeing why it is locked.

### 2. Causa raíz
`correccion-acta.page.html:233–243`:
```html
<button [disabled]="botonEnvioBloqueado() || submitting()">
  Enviar Corrección
</button>
```

The button is disabled by the `botonEnvioBloqueado()` condition, which depends on `!checksumValido`. However, there is no `aria-describedby` or inline error message associated with the button, and the checksum card is rendered after the form, outside natural reading order.

### 3. Reproducción
```bash
# Visual test: open form on mobile (600px viewport)
# Tab through form fields, reach the button
# No inline message explains why button is disabled
# Scroll down to see checksum card

# Screen reader test: tab to button
# Announcement: "Enviar Corrección, button, disabled"
# No explanation of why or how to fix it
```

### 4. Fix propuesto
**Option A — Move checksum card above form/button:**
```html
<correccion-acta-checksum [acta]="acta" [estado]="checksumValido"></correccion-acta-checksum>

<form>
  <!-- form inputs -->
</form>

<button [disabled]="...">Enviar</button>
```

**Option B — Add inline error below button:**
```html
<button [disabled]="botonEnvioBloqueado()">Enviar Corrección</button>

@if (botonEnvioBloqueado() && !submitting()) {
  <p class="text-red-600 text-sm mt-2" role="alert">
    ✓ Corrige la suma de votos para poder enviar. 
    Suma actual: {{ sumaCandidatos() }}, Sufragantes: {{ totalSufragantes() }}
  </p>
}
```

**Option C — Use aria-describedby:**
```html
<button [disabled]="botonEnvioBloqueado()" aria-describedby="checksum-status">
  Enviar Corrección
</button>

<div id="checksum-status">
  <correccion-acta-checksum ...></correccion-acta-checksum>
</div>
```

### 5. Verificación de cierre
- Visual test: button disabled state has explanation visible at same level
- Screen reader test: tab to button, hear explanation in aria-describedby or adjacent text
- Run: `pnpm test:ci` → all tests pass
- Verify no new console warnings about aria references

---

## CRITICAL 4 — Number Inputs Use `(change)` Instead of `(input)` / `(ngModelChange)`

### 1. Síntoma
User enters "350" in "Total Sufragantes" field one digit at a time (3, 35, 350). The checksum card is supposed to update in real-time per spec ("Recalcula el checksum inmediatamente ante cada pulsación"), but it does not. Checksum only updates when the user blurs the field (clicks away or tabs out). In a fast-paced field operation, the veedor expects live feedback and gets stale data.

### 2. Causa raíz
`correccion-acta.page.html:131, 149, 166`:
```html
<!-- Candidate votes use (ngModelChange) — correct, fires on keystroke -->
<input [(ngModel)]="..." (ngModelChange)="recalcularChecksum()">

<!-- Total fields use (change) — incorrect, fires only on blur -->
<input (change)="actualizarTotal()" ...>
```

The `(change)` event fires only when focus leaves the input field (blur). The `(ngModelChange)` event (or `(input)`) fires on every keystroke. Spec requires real-time update; implementation does not deliver.

### 3. Reproducción
```bash
# Manual test:
# 1. Open form
# 2. Type "350" slowly in Total Sufragantes (watch checksum card)
# 3. Checksum does NOT update until you click elsewhere or press Tab
# 4. Type candidate votes (checksum DOES update per keystroke)
```

### 4. Fix propuesto
Replace `(change)` with `(input)` or `(ngModelChange)` on all three total fields:

**Option A — Use (input) event:**
```html
<input (input)="actualizarTotal()" ...>
```

**Option B — Use [(ngModel)] + (ngModelChange) for consistency:**
```html
<input [(ngModel)]="totalSufragantes" (ngModelChange)="recalcularChecksum()" ...>
```

Apply to lines 131 (Total Sufragantes), 149 (Votos Blancos), 166 (Votos Nulos).

### 5. Verificación de cierre
- Manual test: type "350" in Total Sufragantes, checksum updates per keystroke
- Run: `pnpm test:ci` → all tests pass (especially "should recalculate checksum on keystroke")
- Verify no console warnings about ngModel binding

---

## CRITICAL 5 — Silent Redirection When Acta Not EN_DISPUTA

### 1. Síntoma
Veedor opens a form link to a disputed ballot. Backend returns the acta in state APROBADA, CORREGIDA, or PENDIENTE (e.g., another coordinator already resolved it). The page silently navigates to `/observer/mis-jrvs` with no message. In field conditions (slow network, mid-operation), the user has no idea why the form disappeared. They may spend time searching for the acta or re-entering data.

### 2. Causa raíz
`correccion-acta.page.ts:177–181`:
```typescript
procesarActaCargada(acta: ActaResponse) {
  if (acta.estado !== 'EN_DISPUTA') {
    this.router.navigate(['/observer/mis-jrvs']);
    return;
  }
  // ... load form
}
```

The code correctly redirects, but provides no user-facing notification. The code itself has a comment: `// Mostrar toast de aviso sería ideal aquí, pero no está en scope`. The omission is acknowledged but not addressed.

### 3. Reproducción
```bash
# Backend setup: acta with ID 42 in state APROBADA (not EN_DISPUTA)

# Test flow:
# 1. Navigate to /observer/jrv/1/acta/42/corregir
# 2. Page loads spinner
# 3. Component calls getActaParaCorregir(42)
# 4. Response: acta.estado = "APROBADA"
# 5. ngOnInit checks estado, redirects silently
# 6. User finds themselves on /observer/mis-jrvs with no explanation
```

### 4. Fix propuesto
Add user-facing feedback before redirection:

**Option A — Use navigation state + detect on destination page:**
```typescript
// correccion-acta.page.ts
if (acta.estado !== 'EN_DISPUTA') {
  this.router.navigate(['/observer/mis-jrvs'], {
    state: { 
      mensaje: `El acta no se encuentra en disputa (estado: ${acta.estado}). 
                Verifique con coordinación.` 
    }
  });
  return;
}
```

Then on mis-jrvs.page.ts read the state and show a toast.

**Option B — Set error before redirecting:**
```typescript
if (acta.estado !== 'EN_DISPUTA') {
  this.error.set(`Acta no disponible (estado: ${acta.estado})`);
  this.router.navigate(['/observer/mis-jrvs']);
  return;
}
```

Then render the error on the destination page.

**Option C — Show inline toast immediately:**
```typescript
import { ToastService } from '../../core/toast/toast.service';

if (acta.estado !== 'EN_DISPUTA') {
  this.toast.error('El acta no se encuentra en estado de disputa para corrección.');
  setTimeout(() => this.router.navigate(['/observer/mis-jrvs']), 2000);
  return;
}
```

### 5. Verificación de cierre
- Manual test: request an acta in non-EN_DISPUTA state, verify toast/message appears before navigation
- Run: `pnpm test:ci` → test "should redirect to mis-jrvs if acta is not in EN_DISPUTA state" must verify the user-facing message is set
- Verify no silent redirects in browser network tab

---

## MAJOR 1 — `max="1000"` Inconsistent With CPCCS Logic

(Non-blocking but should be addressed in next cycle)

**Ubicación**: `correccion-acta.page.html:128`

**Problema**: `TOPE_SUFRAGANTES_CNE = 500` pero HTML usa `max="1000"`. Si es tabla CPCCS y usuario necesita ingresar 750 sufragantes, browser native constraint lo bloquea con tooltip confuso.

**Fix**: Usar binding dinámico:
```html
<input [max]="TOPE_SUFRAGANTES_CNE">
```

O remover el max y dejar que Angular validate la lógica.

---

## MAJOR 2 — Header "Acta ID" Visible During Loading

(Non-blocking minor UX)

**Ubicación**: `correccion-acta.page.html:15–20`

**Problema**: Encabezado renderizado mientras spinner carga, ID visible antes de confirmación.

**Fix**: Usar skeleton o mover dentro del bloque condicional.

---

## MAJOR 3 — `role="alert"` Interrupts Per Keystroke

(Non-blocking but disruptive on screen readers)

**Ubicación**: `correccion-acta.page.html:222` (checksum error)

**Problema**: Cada keystroke que transiciona checksum invalido→válido dispara `role="alert"`.

**Fix**: Cambiar a `aria-live="polite"` para validación inline; reservar `role="alert"` para error tras intento fallido de envío.

---

## CRITICAL 6 — Mutation 1 Does Not Cause Test Failure (False Positive in Coverage)

### 1. Síntoma
**Ronda 1 reported**: Mutación 1 (removing checksum validation from button) **causes test "should mark checksum invalid..."** to fail.

**Ronda 2 executed identical mutation**: Test **PASSES** when it should **FAIL**. The test coverage metric is **false**: if the template binding were broken, tests would NOT catch it.

This represents a critical gap in test quality assurance. A mutation that should be lethal passes the test suite.

### 2. Causa raíz
`correccion-acta.page.spec.ts` — Test validates the TypeScript getter (`component.botonEnvioBloqueado`), NOT the HTML binding:

```typescript
// Current test (Ronda 1 & 2)
it('should mark checksum invalid if sufragantes is 0', () => {
  component.totalSufragantes.set(0);
  expect(component.botonEnvioBloqueado()).toBe(true);
  // ✅ Validates GETTER, not HTML binding [disabled]="botonEnvioBloqueado"
});
```

The getter correctly returns `true` when checksum is invalid. But if the HTML binding were changed from:
```html
[disabled]="botonEnvioBloqueado"
```
to:
```html
[disabled]="submitting()"
```

The test still passes because it never checks the template. The button would become **clickable while checksum is invalid**, allowing invalid data to submit — but tests pass.

**Mutation execution (Ronda 2):**
```bash
# Changed line 242 in correccion-acta.page.html:
# <button [disabled]="botonEnvioBloqueado">  →  <button [disabled]="false">
pnpm test:ci --run
# Result: ✅ ALL TESTS PASS (including "should mark checksum invalid...")
```

The test does not verify the template binding is wired correctly.

### 3. Reproducción
```bash
cd frontend/app/src/app/features/observer/pages/correccion-acta

# Confirm current test passes
pnpm test:ci --run
# Output: ✅ 529 tests pass

# Apply Mutation 1: Remove disabled binding
sed -i 's/\[disabled\]="botonEnvioBloqueado"/[disabled]="false"/' correccion-acta.page.html

# Run tests again
pnpm test:ci --run
# Output: ✅ 529 tests pass (NO FAILURE — FALSE NEGATIVE)

# Restore
git checkout correccion-acta.page.html
```

Manual verification:
```bash
# In browser, open component
# Trigger invalid checksum (e.g., set totalSufragantes = 0)
# Button is correctly disabled (getter works)

# But test never checks: did the template bind the getter?
# If binding is broken, button appears enabled while checksum invalid
```

### 4. Fix propuesto
**Add a template binding test** to verify the HTML actually uses the getter:

**Option A — Add E2E/integration test:**
```typescript
it('should disable send button in template when checksum is invalid', () => {
  // Trigger invalid state
  component.totalSufragantes.set(0);
  component.ngOnInit(); // ensure computed signals update
  fixture.detectChanges();

  // Query the actual button element
  const button = fixture.debugElement.query(By.css('button[data-testid="btn-enviar"]'));
  
  // Verify it is disabled in the rendered DOM
  expect(button.nativeElement.disabled).toBe(true);
  
  // Verify the binding is correct
  expect(button.nativeElement.getAttribute('[disabled]')).toBeTruthy();
});
```

**Option B — Mutation-aware test:**
```typescript
it('MUTATION: botonEnvioBloqueado must block submit', () => {
  component.totalSufragantes.set(0);
  fixture.detectChanges();
  
  const button = fixture.debugElement.query(By.css('button[type="submit"]'));
  expect(button.nativeElement.disabled).toBe(true);
  
  // If mutation removes getter from [disabled], this fails
});
```

**Option C — Template snapshot test:**
Use Angular's `ng-snapshot-testing` or similar to verify template structure:
```typescript
it('should have checksum validation in button disabled binding', () => {
  const template = fixture.debugElement.nativeElement.innerHTML;
  expect(template).toContain('[disabled]="botonEnvioBloqueado"');
});
```

### 5. Verificación de cierre
- **Add template binding test** per Option A or B above
- **Re-run mutation**: Apply Mutation 1 again (change `[disabled]` binding), verify test **FAILS**
- **Run full suite**: `pnpm test:ci` → all 530+ tests pass (new test included)
- **Code coverage**: Verify coverage tool reports the template binding as covered
- **Manual test**: Open component, break checksum, verify button is disabled visually

---

## Resumen de Blockers

| ID | Tipo | Descripción | Bloqueante |
|---|---|---|---|
| C1 | Accesibilidad | `role="alert"` + `aria-live="polite"` contradicción (WCAG 4.1.3) | ✅ SÍ |
| C2 | Accesibilidad | `aria-describedby` referencia elemento inexistente (WCAG 1.3.1) | ✅ SÍ |
| C3 | UX Operacional | Botón deshabilitado sin explicación visible | ✅ SÍ |
| C4 | Funcionalidad | Inputs number no actualizan checksum en tiempo real | ✅ SÍ |
| C5 | UX Operacional | Redirección silenciosa sin feedback de usuario | ✅ SÍ |
| C6 | Test Coverage | Mutación 1 no causa caída de tests (cobertura falsa) | ✅ SÍ |

**Todos los CRITICAL bloquean despliegue a campo.**
