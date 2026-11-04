# Verify Report — Ronda 3 T-FE-14: Corrección de Acta Disputada en Campo (OBSERVER)

**Fecha**: 2026-11-04  
**Rama**: brydyan/sc-367/t-fe-14-observer-correccion-de-acta-en-disputa  
**HEAD**: 1add0f0 (árbol limpio)  
**Veredicto**: **FIXES REQUIRED** — Los 6 CRITICAL de Ronda 2 persisten SIN ARREGLAR.

---

## Inspección Visual — 6 CRITICAL

| # | CRITICAL | Línea | Estado | Evidencia |
|---|----------|-------|--------|-----------|
| C1 | `role="alert"` + `aria-live="polite"` contradicción (WCAG 4.1.3) | `correccion-acta.page.html:44-45` | ❌ SIN ARREGLAR | Aún presente: `<p role="alert" aria-live="polite"...>` |
| C2 | `aria-describedby` referencia inexistente (WCAG 1.3.1) | `correccion-acta.page.html:134` | ❌ SIN ARREGLAR | Input con `aria-describedby="help-sufragantes"` pero elemento `id="help-sufragantes"` no existe en DOM |
| C3 | Botón deshabilitado sin explicación visible | `correccion-acta.page.html:237` | ❌ SIN ARREGLAR | Botón `[disabled]="botonEnvioBloqueado"` sin texto explicativo adyacente ni `aria-describedby` |
| C4 | Inputs number usan `(change)` no `(input)` / `(ngModelChange)` | `correccion-acta.page.html:131,149,166` | ❌ SIN ARREGLAR | Tres inputs totales aún usan `(change)` → no actualizan en real-time |
| C5 | Redirección silenciosa sin feedback usuario | `correccion-acta.page.ts:177-179` | ❌ SIN ARREGLAR | Navega sin toast/mensaje visible. Comentario en código: "no está en scope" |
| C6 | Mutación 1 no causa caída de tests (cobertura falsa) | `correccion-acta.page.spec.ts` | ❌ SIN ARREGLAR | Test valida getter, no binding template |

---

## Mutaciones — Resultados

### Mutación 1: Quitar checksum del binding button

**Línea modificada**: `correccion-acta.page.html:237`  
**Cambio**: `[disabled]="botonEnvioBloqueado"` → `[disabled]="false"`  
**Test objetivo**: "should mark checksum invalid if sufragantes is 0"

**Resultado Ronda 3**:
```
TESTS PASS (529/529) ❌ FALSO NEGATIVO CONFIRMADO
```

**Conclusión**: El test no valida el binding HTML. El getter `botonEnvioBloqueado` retorna `true` cuando checksum es inválido, pero el test jamás verifica que el template lo use. Si alguien cambia `[disabled]="botonEnvioBloqueado"` a `[disabled]="false"` o `[disabled]="submitting()"`, el test sigue pasando → **COBERTURA FALSA CONFIRMADA**.

---

### Mutación 2: Comentar validación EN_DISPUTA

**Línea modificada**: `correccion-acta.page.ts:177-179`  
**Cambio**: Comentar bloque `if (acta.estado !== 'EN_DISPUTA')`  
**Test objetivo**: "should redirect to mis-jrvs if acta is not in EN_DISPUTA state"

**Resultado Ronda 3**:
```
1 FAILED | 528 PASSED ✅ CAÍDA ESPERADA
```

**Conclusión**: Test funciona correctamente. La validación de estado sigue presente en código.

---

### Mutación 3: Multiplicar totalSufragantes × 2

**Línea modificada**: `correccion-acta.page.ts:252`  
**Cambio**: `totalSufragantes: this.totalSufragantes()` → `totalSufragantes: this.totalSufragantes() * 2`  
**Test objetivo**: "should call corregirActa with correct payload and navigate on success"

**Resultado Ronda 3**:
```
1 FAILED | 528 PASSED ✅ CAÍDA ESPERADA
```

**Conclusión**: Test valida payload correctamente. Payload se envía sin mutación.

---

## Compuertas

| Compuerta | Ronda 2 | Ronda 3 | Delta |
|-----------|--------|--------|-------|
| `pnpm format:check` | ✅ exit 0 | ✅ exit 0 | — |
| `pnpm lint` | ✅ 0 warnings | ✅ 0 warnings | — |
| `pnpm test:ci --run` | ✅ 529 tests | ✅ 529 tests | — |
| `pnpm build` | ✅ exit 0 | ✅ exit 0 | 1 warning (DashboardPage, no relacionado) |

**Todas compuertas en verde**. No hay cambios desde Ronda 2.

---

## Resumen Ejecutivo

### 6 CRITICAL Persisten

**Ronda 2 reportó**: 6 CRITICAL bloqueantes. Builder era responsable de arreglar.

**Ronda 3 verifica**: **Ninguno de los 6 fue arreglado en el código.**

1. **C1**: `aria-live="polite"` contradicción sigue en HTML línea 44-45
2. **C2**: `aria-describedby` referencia roota sigue en línea 134
3. **C3**: Botón deshabilitado sin explicación sigue en línea 237
4. **C4**: Inputs number con `(change)` sin cambiar siguen en líneas 131, 149, 166
5. **C5**: Redirección silenciosa sin feedback sigue en línea 177-179
6. **C6**: Test binding mutation **confirma cobertura falsa**: Mutación 1 debería caer, pero pasa

### Mutaciones Confirmadas

- **Mutación 1**: ❌ PASA (falso negativo) → evidencia de que C6 no fue arreglado
- **Mutación 2**: ✅ CAYE (esperado) → validación EN_DISPUTA está presente
- **Mutación 3**: ✅ CAYÓ (esperado) → payload se construye correctamente

---

## Veredicto Final

**🚫 BLOCKED — FIXES REQUIRED**

**Razones de bloqueo:**

1. **6 CRITICAL sin resolver** bloques despliegue a campo (WCAG, UX, funcionalidad)
2. **C6 cobertura falsa confirmada**: Test mutation 1 pasa cuando debería fallar → debe agregarse test template binding
3. **Builder responsable** de implementar los fixes de Ronda 2 antes de pasar a siguiente ronda
4. **Compuertas verdes** pero son ignoradas por presencia de CRITICAL

**Acción requerida:**
- Implementar fixes para C1–C6 (ver `fixes-required.md` para opciones)
- Agregar test template binding para C6 (validar `[disabled]` en template, no solo getter)
- Re-ejecutar Ronda 4 para verificación

---

## Evidencia Técnica Adicional

### C1 — Banner Alert

```html
<!-- HTML línea 44-45 (sin arreglado) -->
<p role="alert" aria-live="polite" class="alertaDisputa">
  {{ acta.motivoDisputa }}
</p>
```

**Problema**: `role="alert"` implica `aria-live="assertive"`. Agregar `aria-live="polite"` crea conflicto WCAG 4.1.3.

### C2 — Aria Describedby

```html
<!-- HTML línea 134 (sin arreglado) -->
<input
  id="total-sufragantes"
  aria-describedby="help-sufragantes"
  ...
/>
<!-- id="help-sufragantes" NUNCA DECLARADO EN TEMPLATE -->
```

### C4 — Real-time Updates

```html
<!-- HTML línea 131 (sin arreglado, usa change, no input) -->
<input
  [value]="totalSufragantes()"
  (change)="actualizarTotalSufragantes($event.target.valueAsNumber)"
/>
<!-- Debería ser (input) o [(ngModel)] + (ngModelChange) -->
```

### C6 — Test Binding Validation Gap

```typescript
// Ronda 2 test (línea 118 spec.ts)
it('should enable send button when checksum is valid...', () => {
  expect(component.botonEnvioBloqueado).toBe(false);
  // ✅ Valida GETTER en TypeScript
  // ❌ NUNCA verifica template binding [disabled]="botonEnvioBloqueado"
});

// Mutación 1 aplicada:
// <button [disabled]="false"> en lugar de [disabled]="botonEnvioBloqueado">
// Test aún pasa → FALSO NEGATIVO
```

---

**Preparado por**: sdd-verify (verificación automática)  
**Timestamp**: 2026-11-04T17:30:00Z  
**Rama estado**: Clean (git status limpio)
