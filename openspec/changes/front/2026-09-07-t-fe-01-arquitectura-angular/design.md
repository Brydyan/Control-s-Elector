# Design — T-FE-01: Arquitectura Angular Base (Decisiones Implementadas)

## D1 — Standalone Components vs NgModules

**Decisión:** ✅ **YA IMPLEMENTADO** — 100% componentes standalone, sin NgModules.

**Evidencia:** `ng build` compila sin errores. Todos los componentes en features/ usan `@Component({standalone: true})`.

**Razón:** Angular 17+ hace standalone el estándar forward. NgModules es legacy. Decisión correcta.

---

## D2 — tsconfig Path Mappings

**Decisión:** ❌ **NO IMPLEMENTADO** — Mejora futura opcional.

**Estado actual:** Imports relativos funcionan bien (`import from '../../../shared/components'`).

**Alternativa considerada:** Configurar `paths` en tsconfig.json:
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

**Decisión:** **No es crítico ahora.** El proyecto funciona sin esto. Si la navegación entre imports se vuelve tedious, agregar en futuro T-FE-*.

**Razón:** Mejora cosmética, no funcional. Costo/beneficio bajo en este momento.

---

## D3 — Ubicación de servicios comunes

**Decisión:** ✅ **YA IMPLEMENTADO** — Solo en `core/`. Shared es solo componentes y utilities.

**Evidencia:** 
- core/auth/: AuthService, AuthStore, AuthGuard, AuthInterceptor
- core/menu/: MenuService
- core/network/: NetworkStatusService
- shared/: solo componentes UI, models, utilities (a11y, errors)

**Razón correcta:** Separación clara de responsabilidades. Servicios singleton en core, componentes/utilities en shared.

---

## D4 — Estructura de barrels (index.ts)

**Decisión:** ❌ **NO IMPLEMENTADO** — Mejora futura opcional.

**Estado actual:** Componentes se importan directamente por ruta:
```typescript
// Actual (funciona bien)
import { EstadoBadge } from 'src/app/shared/ui/badge/badge.component';

// Con barrels (legible, pero requiere index.ts)
import { EstadoBadge } from 'src/app/shared/ui';
```

**Decisión:** **No es crítico ahora.** Los imports actuales son explícitos y funcionan. Si el código crece y la legibilidad sufre, agregar barrels en futuro.

**Razón:** Mejora organizativa, no funcional. Mantener simple por ahora.

---

## D5 — Feature Structure por Rol

**Decisión:** ✅ **YA IMPLEMENTADO** — Features separadas por rol.

**Estructura actual:**
- features/observer/
- features/coordinator/
- features/admin/
- features/dashboard/
- features/auth/
- features/placeholder/

**Razón correcta:** Fácil agregar/remover features. Lazy-load es natural. Encapsulación clara.

---

## Conclusión

**4 de 5 decisiones YA IMPLEMENTADAS Y CORRECTAS.** Las 2 no implementadas (D2 path mappings, D4 barrels) son **mejoras futuras opcionales**, no críticas para el funcionamiento actual.
