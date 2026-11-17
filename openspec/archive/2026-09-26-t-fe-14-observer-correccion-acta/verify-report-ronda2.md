# Verify Report — T-FE-14: Corrección de Acta Disputada (Ronda 2)

**Verificador**: Agent (Ronda 2)  
**Fecha**: 2026-11-04  
**Rama**: brydyan/sc-367/t-fe-14-observer-correccion-de-acta-en-disputa  
**HEAD**: 1add0f0  
**Veredicto**: **FIXES REQUIRED** (5 CRITICAL aún sin arreglar, Mutation 1 no mata test)

---

## Compuertas Ejecutadas (Ronda 2)

### frontend/app gates

| Compuerta | Comando | Resultado | Exit Code |
|-----------|---------|-----------|-----------|
| format:check | `pnpm format:check` | ✅ PASS | 0 |
| lint | `pnpm lint` | ✅ PASS (0 errores) | 0 |
| test:ci --run | `pnpm test:ci --run` | ✅ PASS (529 tests, 61 archivos) | 0 |
| build | `pnpm build` | ✅ PASS (1 warning preexistente) | 0 |

**Comparativa vs Ronda 1**:
- Test Files: 61 (sin cambio)
- Tests: 529 (sin cambio)

---

## Auditoría de CRITICAL — Ronda 1 vs Ronda 2

### CRITICAL 1: `role="alert"` + `aria-live="polite"` — Contradicción WCAG 4.1.3

**Ubicación**: `correccion-acta.page.html:44–46` (banner de disputa)

**Estado Ronda 1**: ❌ FALLA — Conflicto ARIA detectado

**Estado Ronda 2**: ❌ **SIN ARREGLO** — Código idéntico

```html
<div
  class="dispute-banner"
  role="alert"
  aria-live="polite"
  data-testid="dispute-banner"
>
```

**Evidencia**: Línea 44-45 aún contienen ambos atributos contradictorios.

---

### CRITICAL 2: `aria-describedby="help-sufragantes"` Referencia Inexistente — WCAG 1.3.1

**Ubicación**: `correccion-acta.page.html:134` (input Total Sufragantes)

**Estado Ronda 1**: ❌ FALLA — Referencia rota (id no existe)

**Estado Ronda 2**: ❌ **SIN ARREGLO** — Código idéntico

```html
<input
  id="total-sufragantes"
  ...
  aria-describedby="help-sufragantes"
  ...
/>
```

**Búsqueda**: `id="help-sufragantes"` → NO ENCONTRADO en archivo HTML.

---

### CRITICAL 3: Botón "Enviar Corrección" Deshabilitado Sin Explicación Visible

**Ubicación**: `correccion-acta.page.html:233–243` (botón submit)

**Estado Ronda 1**: ❌ FALLA — Sin texto explicativo cuando `[disabled]="botonEnvioBloqueado"`

**Estado Ronda 2**: ❌ **SIN ARREGLO** — Código idéntico

```html
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
```

**Hallazgo**: No hay `<span>` o texto que explique por qué el botón está deshabilitado (e.g., "Suma de votos no coincide").

---

### CRITICAL 4: Event `(change)` en Inputs Number No Dispara por Keystroke

**Ubicación**: `correccion-acta.page.html:131, 149, 166` (Total Sufragantes, Votos Blancos, Votos Nulos)

**Estado Ronda 1**: ❌ FALLA — Inputs totales usan `(change)` en lugar de `(ngModelChange)`

**Estado Ronda 2**: ❌ **SIN ARREGLO** — Código idéntico

```html
<!-- Total Sufragantes (line 131) -->
<input
  id="total-sufragantes"
  type="number"
  ...
  [value]="totalSufragantes()"
  (change)="actualizarTotalSufragantes($event.target.valueAsNumber)"
  ...
/>

<!-- Votos Blancos (line 149) -->
<input
  id="votos-blancos"
  type="number"
  ...
  [value]="votosBlancos()"
  (change)="actualizarVotosBlancos($event.target.valueAsNumber)"
  ...
/>

<!-- Votos Nulos (line 166) -->
<input
  id="votos-nulos"
  type="number"
  ...
  [value]="votosNulos()"
  (change)="actualizarVotosNulos($event.target.valueAsNumber)"
  ...
/>
```

**Contraste**: Inputs de candidatos (línea 104) usan `(ngModelChange)` que dispara inmediatamente.

**Impacto**: Checksum NO se actualiza en tiempo real para estos tres campos. Veedor digita "350" en sufragantes y la tarjeta de checksum no refleja cambios hasta blur.

---

### CRITICAL 5: Redirección Silenciosa Si Acta No Está EN_DISPUTA

**Ubicación**: `correccion-acta.page.ts:177–181` (método `procesarActaCargada`)

**Estado Ronda 1**: ❌ FALLA — Redirección sin toast o mensaje visible

**Estado Ronda 2**: ❌ **SIN ARREGLO** — Código idéntico

```typescript
private procesarActaCargada(acta: ActaResponse): void {
  if (acta.estado !== 'EN_DISPUTA') {
    this.router.navigate(['/observer/mis-jrvs']);
    // Mostrar toast de aviso sería ideal aquí, pero no está en scope
    return;
  }
```

**Hallazgo**: Comentario en código reconoce el problema pero no lo arregla. Veedor NO ve explicación cuando es redirigido.

---

## Mutaciones Ejecutadas (Ronda 2)

### Mutación 1: Quitar `checksumValido` de `botonEnvioBloqueado` — **NO MATA TEST**

**Estrategia Ronda 1**: Mutación en template `[disabled]="false"`

**Estrategia Ronda 2**: Mutación en getter `botonEnvioBloqueado` en TS

**Cambio**: `correccion-acta.page.html:237` → `[disabled]="false"`

**Resultado**: ❌ **TEST NO CAYO** — 529 tests passed (529)

**Diagnóstico**: Test unitario valida el GETTER (`expect(component.botonEnvioBloqueado).toBe(true)`), no el binding en template. El test no ejecuta la navegación del usuario que presionaría el botón deshabilitado.

**Impacto CRITICAL**: La cobertura es falsa. El botón podría enviarse accidentalmente aunque `checksumValido` sea falso, porque el test solo verifica la lógica de la clase, no la sincronización con el template.

---

### Mutación 2: Quitar validación `EN_DISPUTA` — **MATA TEST** ✅

**Cambio**: Comentar bloque if en `correccion-acta.page.ts:177–181`

**Resultado**: ✅ **TEST CAYO** — `"should redirect to mis-jrvs if acta is not in EN_DISPUTA state"`

**Tests ejecutados**: 528 passed, 1 failed

---

### Mutación 3: Multiplicar `totalSufragantes × 2` en payload — **MATA TEST** ✅

**Cambio**: `correccion-acta.page.ts:252` → `totalSufragantes: this.totalSufragantes() * 2`

**Resultado**: ✅ **TEST CAYO** — `"should call corregirActa with correct payload and navigate on success"`

**Tests ejecutados**: 528 passed, 1 failed

---

## Síntesis

| Métrica | Ronda 1 | Ronda 2 | Cambio |
|---------|---------|---------|--------|
| **Format** | ✅ PASS | ✅ PASS | sin cambio |
| **Lint** | ✅ PASS | ✅ PASS | sin cambio |
| **Test Files** | 61 | 61 | sin cambio |
| **Tests** | 529 | 529 | sin cambio |
| **Build** | ✅ PASS | ✅ PASS | sin cambio |
| **CRITICAL sin arreglar** | 5 | 5 | **sin arreglarse** |
| **Mutación 1 mata test** | ✅ SÍ | ❌ **NO** | **REGRESIÓN** |
| **Mutación 2 mata test** | ✅ SÍ | ✅ SÍ | sin cambio |
| **Mutación 3 mata test** | ✅ SÍ | ✅ SÍ | sin cambio |

---

## Hallazgos Críticos

1. **apply-progress.md asegura "El código está listo para review y merge"**, pero:
   - Ninguno de los 5 CRITICAL de Ronda 1 fue arreglado
   - Mutación 1 NO mata test en Ronda 2 (REGRESIÓN de cobertura)
   - El código está byte-identical a Ronda 1

2. **Cobertura de prueba defectuosa**: Test de `botonEnvioBloqueado` valida el getter, no el binding. Si se comete un typo en template (`[disabled]="submitting()"` en lugar de `[disabled]="botonEnvioBloqueado"`), los tests no lo detectarían.

3. **Accesibilidad WCAG bloqueante**: 5 violaciones críticas aún sin resolver:
   - Contradicción ARIA (4.1.3)
   - Referencia rota aria-describedby (1.3.1)
   - Estado deshabilitado sin explicación visible
   - Checksum no actualiza en tiempo real
   - Redirección silenciosa

---

## Veredicto

**FIXES REQUIRED** — No ha habido mejora desde Ronda 1. Declare-los como "listos para merge" es incorrecto.
