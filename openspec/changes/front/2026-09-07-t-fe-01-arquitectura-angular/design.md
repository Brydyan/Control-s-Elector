# Design — T-FE-01: Arquitectura Angular Base

## D1 — Standalone Components vs NgModules

**Decisión:** 100% componentes standalone, sin NgModules.

**Alternativa descartada — NgModules + SharedModule.** Funciona, pero:
- Require declarar en módulo + re-declarar en shared → boilerplate.
- Lazy-load es más verboso.
- En Angular 17+, standalone es el camino forward; NgModules será deprecated.

## D2 — tsconfig Path Mappings

**Decisión:** Configurar `paths` en tsconfig.json:
```json
{
  "compilerOptions": {
    "paths": {
      "@core/*": ["src/app/core/*"],
      "@shared/*": ["src/app/shared/*"],
      "@features/*": ["src/app/features/*"],
      "@layouts/*": ["src/app/layout/*"]
    }
  }
}
```

**Alternativa descartada — importar con rutas relativas.** `import from '../../../shared/components'`. Frágil al mover archivos, difícil de leer.

## D3 — Ubicación de servicios comunes

**Decisión:** Solo en `core/`. Shared es solo componentes y pipes.

**Alternativa descartada — servicios en shared.** La intención de "core" es singleton services. Mezclar con componentes confunde a quien lee.

## D4 — Estructura de barrels

**Decisión:** Un `index.ts` en cada carpeta principal (auth, services, components). Re-exporta públicos.

**Alternativa descartada — no usar barrels.** El importador hace `from 'src/app/core/auth'` en lugar de `from 'src/app/core/auth/auth.service'`. Menos archivos en import, pero oculta la estructura. A favor: ergonomía. En contra: discovery.

**Elegimos barrels:** Clara la estructura, fácil para quien ofrece la API.
