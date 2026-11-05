# Fixes Requeridos — T-FE-17 Ronda 2

**Criticidad**: 2 CRITICAL + 1 PARTIAL  
**Bloqueador de verificación**: SÍ  
**Ronda anterior**: Ronda 1 (C1 cerrado, C2/C3 parcial, W1 arreglado)  
**Status**: Baseline ROJO — 12 tests FAIL por ProxyZone

---

## CRITICAL 1 — ProxyZone Error en Tests

### 1. Síntoma

**Error**:
```
Error: Expected to be running in 'ProxyZone', but it was not found.
```

**Archivo**: `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts`

**Tests afectados**: 12 de 12 fallando en suite T-FE-17

**Comando que falla**:
```bash
cd frontend/app
pnpm test:ci -- coordinator-reportes.page.spec.ts --run
# Exit code: 1
```

**Síntomas en output**:
```
FAIL src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts
  × should load metrics on ngOnInit and display dashboard
    Error: Expected to be running in 'ProxyZone', but it was not found.
  × should calculate actas per hour correctly from timestamps
    Error: Expected to be running in 'ProxyZone', but it was not found.
  [+ 10 more tests con mismo error]
```

### 2. Causa Raíz

El test suite usa `fakeAsync()` y `tick()` pero TestBed **no importa el módulo de zona.js** que proporciona `ProxyZone`.

**Evidencia en código** (aprox.):
```typescript
// coordinator-reportes.page.spec.ts
describe('CoordinatorReportesPage', () => {
  let component: CoordinatorReportesComponent;
  let fixture: ComponentFixture<CoordinatorReportesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CoordinatorReportesComponent, /* ... */],
      // ❌ FALTA: providers con zona.js o BrowserAnimationsModule
    }).compileComponents();

    fixture = TestBed.createComponent(CoordinatorReportesComponent);
    component = fixture.componentInstance;
  });

  it('should load metrics...', fakeAsync(() => {
    // ❌ fakeAsync requiere ProxyZone (zona.js)
    // pero TestBed no la proporciona
    tick();  // ERROR: ProxyZone no encontrada
  }));
});
```

### 3. Reproducción

```bash
cd frontend/app
pnpm test:ci -- coordinator-reportes.page.spec.ts --run 2>&1 | grep -A 5 "ProxyZone"
# Salida: Error: Expected to be running in 'ProxyZone', but it was not found.
```

### 4. Fix Propuesto

**Opción A** (Recomendada): Importar `BrowserAnimationsModule` en TestBed

```typescript
// ANTES
beforeEach(async () => {
  await TestBed.configureTestingModule({
    imports: [
      CoordinatorReportesComponent,
      // ...
    ],
    // ❌ falta zona.js
  }).compileComponents();
});

// DESPUÉS
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

beforeEach(async () => {
  await TestBed.configureTestingModule({
    imports: [
      CoordinatorReportesComponent,
      BrowserAnimationsModule,  // ✅ Proporciona ProxyZone
      // ...
    ],
  }).compileComponents();
});
```

**Opción B** (Alternativa): Usar `waitForAsync()` en lugar de `fakeAsync()`

```typescript
// Si no se requiere tiempo simulado, cambiar:
it('should load metrics...', fakeAsync(() => {
  // ...
  tick();
}));

// A:
it('should load metrics...', waitForAsync(() => {
  // Espera async sin zona simulada
  expect(...).toBeTruthy();
}));
```

### 5. Verificación de Cierre

```bash
cd frontend/app
pnpm test:ci -- coordinator-reportes.page.spec.ts --run
# Esperado: ✓ 12/12 tests pasan
# Esperado: Exit code 0
# NO debe haber error "ProxyZone"
```

---

## CRITICAL 2 — Raw Hex Colours en SCSS

### 1. Síntoma

**Test falla**:
```
FAIL src/theme/token-guard.spec.ts > the token guard > should not find a violation in the app
```

**Archivo**: `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.scss`

**Raw colours encontrados**:
- Línea 34: `color: #fff;` (btn-retry)
- Línea 52: `color: #fff;` (banner-progreso)
- Línea 339: `color: #fff;` (btn-descargar)

**Regla violada**: "raw-colour — is a raw colour outside the theme layer"

### 2. Causa Raíz

Ronda 1 documentó fixes para raw **rgba** (líneas 59, 100, 118), pero el componente también tiene **hexadecimales directos** `#fff` que no fueron arregladas:

```scss
// LÍNEA 34 — RAW HEX
.btn-retry {
  color: #fff;  // ❌ Raw colour
}

// LÍNEA 52 — RAW HEX
.banner-progreso {
  color: #fff;  // ❌ Raw colour
}

// LÍNEA 339 — RAW HEX
.btn-descargar {
  color: #fff;  // ❌ Raw colour
}
```

### 3. Reproducción

```bash
cd frontend/app
pnpm test:ci -- src/theme/token-guard.spec.ts --run
# Salida incluye:
# "raw-colour src/app/.../coordinator-reportes.page.scss:34 \"#fff\" is a raw colour"
# "raw-colour src/app/.../coordinator-reportes.page.scss:52 \"#fff\" is a raw colour"
# "raw-colour src/app/.../coordinator-reportes.page.scss:339 \"#fff\" is a raw colour"
```

### 4. Fix Propuesto

Crear custom property para blanco en `src/theme/tokens.scss` y referenciar en SCSS:

**En `src/theme/tokens.scss`**:
```scss
--color-text-inverse: #fff;  // O usar nombre existente si ya existe
```

**En `coordinator-reportes.page.scss`**:
```scss
// LÍNEA 34
.btn-retry {
  - color: #fff;
  + color: var(--color-text-inverse);
}

// LÍNEA 52
.banner-progreso {
  - color: #fff;
  + color: var(--color-text-inverse);
}

// LÍNEA 339
.btn-descargar {
  - color: #fff;
  + color: var(--color-text-inverse);
}
```

### 5. Verificación de Cierre

```bash
cd frontend/app
pnpm test:ci -- src/theme/token-guard.spec.ts --run
# Esperado: ✓ should not find a violation in the app
# Esperado: Exit code 0
# NO debe haber "raw-colour #fff"
```

---

## PARTIAL: Theme Tokens C2 (Ronda 1)

### 1. Status

**Tokens**: `--color-surface-default`, `--color-status-danger-strong`

**Estado Ronda 1**: NO DECLARADOS (violations encontradas)  
**Estado Ronda 2**: Token guard NO reporta violations para estos tokens

**Conclusión**: Probablemente fueron declarados en `src/theme/tokens.scss` o mapeados a existentes.

### 2. Verificación Pendiente

Para confirmar cierre:
```bash
grep -n "color-surface-default\|color-status-danger-strong" src/theme/tokens.scss
# Esperado: Ambos tokens declarados con valores
```

Si grep no encuentra nada:
- Verificar en template o SCSS si fueron reemplazados por otros tokens
- Si siguen siendo referencias sin declarar, es bloqueador

---

## Resumen de Acciones

| Crítico | Categoría | Archivo | Línea(s) | Acción |
|---------|-----------|---------|----------|--------|
| C1 | Zone.js | coordinator-reportes.page.spec.ts | beforeEach | Importar `BrowserAnimationsModule` en TestBed |
| C2 | Theme | coordinator-reportes.page.scss | 34, 52, 339 | Mapear `#fff` a `--color-text-inverse` (o similar) |
| PARTIAL | Theme | src/theme/tokens.scss | — | Verificar que `--color-surface-default` y `--color-status-danger-strong` están declarados |

---

## Orden Recomendado de Cierre

1. **Primero C1** — Habilita ejecución de 12 tests
2. **Luego C2** — Cierra token-guard violations
3. **Verificar PARTIAL** — Confirmar C2 de Ronda 1 cerrado
4. **Re-ejecutar compuertas**:
   ```bash
   pnpm lint        # exit 0
   pnpm test:ci     # exit 0 (todos los tests PASS)
   pnpm build       # exit 0
   pnpm test:coverage # ≥70% en T-FE-17
   ```
5. **Resultado**: Baseline VERDE → Mutaciones M1 y M2 pueden ejecutarse

---

## Notas de Arquitecto

**Hallazgo de Ronda 2**: Los tests fueron creados en Ronda 1 pero nunca ejecutados en su contexto real (ProxyZone no disponible). Esta es la razón por la cual mutaciones M1 y M2 no pueden verificarse hasta ahora. Ronda 2 expone que la "verificación" de Ronda 1 fue parcial: se confirmó que el código compila y el linter pasa, pero **la suite de tests no estaba habilitada para ejecución**.

**Impacto**: Retrasa mutaciones 1 ronda más, pero identifica defecto real (zona.js) que de no haberse visto causaría regresión silenciosa post-merge.
