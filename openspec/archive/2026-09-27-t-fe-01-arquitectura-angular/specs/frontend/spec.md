# Spec — T-FE-01: Arquitectura Angular Base

## Escenarios (GIVEN/WHEN/THEN)

### S1 — Estructura de carpetas existe

```gherkin
GIVEN    el proyecto Angular inicializado
WHEN     se revisa src/app/
THEN     existen carpetas: core/, shared/, features/, layout/
AND      cada carpeta contiene un index.ts
```

### S2 — Standalone component puede importar desde shared

```gherkin
GIVEN    un componente TestComponent en features/observer/
AND      ExampleComponent existe en shared/components/
WHEN     TestComponent importa ExampleComponent
AND      código compila sin error
THEN     TestComponent renderiza ExampleComponent correctamente
```

### S3 — tsconfig paths funcionan

```gherkin
GIVEN    TestComponent
WHEN     importa usar `import { X } from '@shared/components'`
THEN     TypeScript resuelve al archivo correcto
AND      IDE autocomplete funciona
```

### S4 — Barrel files re-exportan

```gherkin
GIVEN    shared/components/index.ts
WHEN     importa desde shared/components/
THEN     están disponibles: EstadoBadge, ConfirmDialog, etc. (los públicos)
AND      implementaciones privadas no se exportan
```

## Mutaciones

### M1 — Remover core/ — debe fallar

Si se borra `src/app/core/`, cualquier test que importe `@core/auth` debe fallar.

Verificación: intentar correr `ng build` → debe terminar con error.

### M2 — Cambiar path mapping — debe fallar

Si se edita tsconfig.json y se cambia `@core` a `@core-svc`, el import en los componentes debe fallar.

Verificación: cambiar path, correr `ng build`, restaurar, verificar que vuelve a compilar.

## Garantías

- **G1:** Estructura permite lazy-load de features (cada feature es un módulo independiente)
- **G2:** Servicios singleton viven en core, no se duplican
- **G3:** Imports son legibles y trazables (`@core/*` vs `../../../`)
