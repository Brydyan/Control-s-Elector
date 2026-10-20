# Frontend refactor a PrimeNG + Sakai

Objetivo: migrar el frontend Angular del repositorio al stack de PrimeNG usando el template Sakai como base visual, conservando la lógica de negocio, el contrato con el backend Spring Boot y los tests existentes.
Rama: `refactor/frontend-primeng-sakai` (base `origin/develop` @ 98966ef) — worktree en `../Control-s-Elector-worktree`.
Estrategia de entrega: `ask-on-risk` (work-unit commits en la rama de feature; push/PR quedan como decisión del usuario).
Runner de tests: `pnpm test:ci` (vitest, jsdom) en `frontend/app`; build: `pnpm build` (ng build, esbuild).

## Specs

### S1 — Worktree en directorio hermano
La refactorización del frontend debe ejecutarse en un worktree separado del directorio de trabajo actual, con el nombre `<nombre-directorio>-worktree`.

### S2 — Ámbito del refactor
En ese worktree se realiza la refactorización del frontend.

### S3 — Plantilla base: Sakai/PrimeNG
La plantilla de referencia para el refactor es Sakai con PrimeNG, en lugar de shadcn-admin, porque el frontend objetivo es Angular y no React.

### S4 — Personalización por encima del template
Debe ser posible aplicar personalizaciones por encima del template, sin quedar atado a su aspecto por defecto.

### S5 — Conservación del dominio (assumption)
Se asume que la lógica de negocio, el contrato HTTP con el backend y los tests existentes se conservan; el refactor es de capa de presentación, no de dominio.

### S6 — Identidad visual institucional (assumption)
Se asume que la paleta institucional ya definida en `frontend/app/src/theme/tokens.scss` debe sobrevivir al cambio de librería de componentes, en lugar de adoptar la paleta por defecto de la plantilla.

### S7 — Verificabilidad
Cada fase debe dejar el build (`pnpm build`) y la suite (`pnpm test:ci`) en verde, con la evidencia observada registrada en el Log.

## Tasks

| ID | Specs | Descripción | Ruta | Estado |
|----|-------|-------------|------|--------|
| T1 | S1, S2 | Worktree `../Control-s-Elector-worktree` sobre rama `refactor/frontend-primeng-sakai` (base `origin/develop`), CodeGraph indexado, `pnpm install` OK, build verde | inline | done (`98966ef`) |
| T2 | S3, S7 | Auditar compatibilidad de versiones (PrimeNG ↔ Angular 22 ↔ @primeuix/themes ↔ Tailwind 4) y fijar el conjunto de dependencias | inline | done |
| T3 | S4, S6 | Preset de PrimeNG derivado de `tokens.scss` + wiring de `providePrimeNG` + test de humo del tema | inline | done |
| T4 | S4 | Shell de aplicación Sakai (sidebar, topbar, configurador de tema) sobre `layout/` existente | pending | pending |
| T4a | S4, S6 | Prerrequisitos del shell: Tailwind 4 + `tailwindcss-primeui` + variante `dark` + `ThemeService` que activa el modo oscuro | inline | done |
| T5 | S3, S4 | Migrar primitivas propias de `shared/ui` a componentes PrimeNG | pending | pending |
| T6 | S3, S4 | Migrar features por rol (dashboard, admin, coordinator, observer, auth) a PrimeNG | pending | pending |
| T7 | S3 | Retirar Ionic y Capacitor: `ion-icon`→PrimeIcons, `ion-router-outlet`→`router-outlet`, `ion-app`→host, `ionic-theme.scss`→preset | pending | pending |
| T8 | S3, S4 | Mapear `Menu.icono` del backend (nombres Ionic) a iconos PrimeNG | pending | pending |

## Log

### L1 — Petición original (2026-10-20)
"abre un worktree diferente del directorio actual puede ser [nombre-directorio}-worktree, en este worktree haremos la refactoizacion del frontend con este repo que es plantilla de shadcn: https://github.com/satnaing/shadcn-admin"

### L2 — Corrección del usuario (2026-10-20)
"podemos usar en vez de shadccn porque esta hecho en react primeng sakai, pero la cuestion esta puedo poner encima del template mas personalizaiones?"

### L3 — Auditoría de stack (2026-10-20, T2)
Hechos verificados contra el registro npm y GitHub, no de memoria:
- `primeng@22.1.3` (publicado 2026-10-20) declara peer deps `@angular/core ^22.1.0`, `@angular/cdk ^22.1.0`, `@angular/common|forms|router|platform-browser ^22.1.0`. El proyecto usa Angular **22.2.0** → satisface el peer range. PrimeNG versiona en lockstep con Angular (19↔19, 20↔20, 21↔21, 22↔22).
- PrimeNG 22 **ya no** declara `@angular/animations` como peer (sí lo declaraba en 19 y 20) → usa `@primeuix/motion`.
- `@primeuix/themes@3.0.2` disponible; `tailwindcss-primeui@0.6.1` (peer `tailwindcss >=3.1.0`).
- `primefaces/sakai-ng` (MIT, 950★, default branch `master`, último push 2026-02-02) está en **Angular 21 + PrimeNG ^21.0.2 + @primeuix/themes ^2.0.0 + Tailwind 4.1.11 + tailwindcss-primeui ^0.6.1**, y construye con `ng build`.
- `sakai-react` quedó en 2024-10-18 (sin mantenimiento) → no es candidato.

Decisión: **Sakai-ng (Angular), no Sakai-react**. Requiere subir el template de Angular 21→22 y PrimeNG 21→22.

### L4 — Corrección sobre el build (2026-10-20)
En L3 y en la conversación se afirmó que el build ya era Vite. **Es incorrecto**: `angular.json` usa el builder `@angular-devkit/build-angular:application` (esbuild). `vite.config.ts` existe únicamente como runner de **Vitest** (`@analogjs/vite-plugin-angular` + `test.{environment:'jsdom',setupFiles,include}`). No hay pipeline de build Vite que mantener. Beneficio: el approach de build de Sakai-ng (`ng build`) encaja sin cambios.

### L5 — Auditoría del acoplamiento actual (2026-10-20)
- Ionic: `ion-icon` ×16, `ion-app` ×1, `ion-router-outlet` ×1, en 7 archivos HTML. Acoplamiento real bajo.
- Capacitor: **0 imports** de `@capacitor/*` en `src/`. `capacitor.config.ts` es boilerplate sin tocar (`appId: 'io.ionic.starter'`, `appName: 'app'`). No hay service worker ni manifest PWA: el soporte offline es IndexedDB (`idb`) + localStorage + Network Information API → todo browser-estándar, replicable en Angular sin Costura.
- Distribución del código de producción (5.863 LOC en 60 `.ts`): ~2.169 LOC portables (services/store/repository/dto/model) y ~3.195 LOC Angular-específicos (components/pages/guard/interceptor).
- `shared/ui/` contiene 8 primitivas propias (badge, button, card, empty-state, error-state, icon, loading-state, skeleton) = 1.027 LOC — PrimeNG las cubre nativamente.
- Los `src/theme/{tokens,typography,utilities,variables}.scss` con paleta institucional (azul #2563eb, superficies slate, sidebar oscuro) ya existen en `origin/develop`.

### L6 — Mecanismos de personalización sobre Sakai (respuesta a S4)
1. **Design tokens de PrimeNG** (`@primeuix/themes` v3, `definePreset`): cada atributo visual de cada componente es un token (paleta primary, form field size, border radius, focus ring, escala tipográfica, sombras). Se deriva un preset propio desde Aura y se sobrescribe solo lo necesario — sin forkear componentes ni CSS a mano.
2. **App Configurator** (`app.configurator.ts` en `src/app/layout/component/`): panel flotante en vivo para preset, color primario, modo oscuro y modo de menú; los defaults viven en el signal `layoutConfig` de `LayoutService` (`preset: 'Aura'`, `primary: 'emerald'`).
3. **Tailwind CSS 4** ya integrado en Sakai-ng (`tailwindcss-primeui`): utilidades para layout/espaciado por encima de los componentes PrimeNG.
4. **Pass-through (`pt`)** por nodo DOM en cualquier componente PrimeNG, sin forkearlo.
5. **Layout por signals**: `menuMode` static/overlay, estados de sidebar, `startViewTransition` para el cambio de tema.

### L7 — Distribución estimada
El refactor se estima en varias entregas (>400 líneas), por lo que este documento se crea antes de la primera escritura de código. T3 (~150 líneas) es la primera unidad con commit propio.

### L8 — T3 ejecutado: fundación de tema PrimeNG (2026-10-20)
Archivos nuevos:
- `frontend/app/src/theme/primeng-preset.ts` — `definePreset(Aura, …)` con la rampa institucional en `semantic.primary` y `primitive.blue`, y la escala de radios mapeada a los stops de PrimeUI. Valores literales (no referencias `{blue.*}`) para que el test pueda afirmarlos sin runtime de variables CSS.
- `frontend/app/src/theme/primeng-theme.ts` — configuración de tema y `DARK_MODE_CLASS = 'app-dark'` (mismo selector que usa Sakai).
- `frontend/app/src/theme/primeng-preset.spec.ts` — 8 tests.

Archivos modificados:
- `frontend/app/src/main.ts` — `providePrimeNG({ theme: primengTheme, ripple: true })`.
- `frontend/app/angular.json` — `node_modules/primeicons/primeicons.css` en `styles`.
- `frontend/app/package.json` — `primeng@22.1.3`, `@angular/cdk@22.2.3`, `@primeuix/themes@3.0.2`, `@primeicons/angular@8.0.2`, `primeicons@8.0.2`.

Evidencia observada:
- `pnpm test:ci` → **43 archivos, 249 tests, 0 fallos** (antes de T3: 42 archivos, 241 tests).
- Chequeo de mutación: con `primary.500 = '#ff0000'` el test de rampa institutional **falla** (1 failed / 7 passed); revertido → 8 passed. El test detecta la regresión.
- `pnpm build` → bundle generado en `www` (1.9 MB). Los warnings restantes (Sass `@import` deprecado en `admin-sesiones.page.scss`, NG8113 `ButtonComponent` sin uso en `dashboard.page.ts`, NG8107 en `acta-detalle.page.html`, presupuesto de `anyComponentStyle`) son **preexistentes** y ajenos a T3.
- `pnpm peers check` → los 3 avisos (chokidar, vitest ↔ @vitest/coverage-v8) son **preexistentes** y ajenos a PrimeNG; PrimeNG 22 no introduce ninguno.

Hallazgo relevante: la rampa institucional de `tokens.scss` **es la rampa Tailwind blue con un offset de un paso** (su `--primitive-blue-500` = `#2563eb` = Tailwind `blue-600`). Por eso el preset mapea `primary.500 → blue.600` y `primary.600 → blue.700`, conservando `--color-brand-primary` y `--color-brand-primary-strong` exactos.

## Pending / Next
- T4: shell Sakai (sidebar, topbar, App Configurator con `updatePreset`/`updateSurfacePalette` en runtime) sobre `layout/`; los prerrequisitos (Tailwind 4, variante `dark`, `ThemeService`) ya están listos.
- T7 requiere decidir el destino de `frontend/app/android/` y `capacitor.config.ts` (boilerplate sin usar).

### L9 — T4a ejecutado: prerrequisitos del shell (2026-10-20)
Archivos nuevos:
- `frontend/app/src/theme/tailwind.css` — entry de Tailwind v4 con `@plugin 'tailwindcss-primeui'`, `@custom-variant dark` ligada a `app-dark` y la capa base de compatibilidad de `border-color` (misma config que Sakai).
- `frontend/app/src/theme/theme.service.ts` — servicio de tema: signal `darkMode`, persistencia en `localStorage`, y aplicación **síncrona** de la clase `app-dark` en `<html>`.

Archivos modificados:
- `frontend/app/angular.json` — `src/theme/tailwind.css` añadido **último** en `styles` (orden de Sakai: las utilidades deben ganar al CSS global legacy).
- `frontend/app/pnpm-workspace.yaml` — `'@tailwindcss/oxide': true` (pnpm había insertado un placeholder; el binario nativo es requisito de Tailwind v4).
- `frontend/app/package.json` — `tailwindcss@4.1.11`, `@tailwindcss/postcss@4.1.11`, `tailwindcss-primeui@0.6.1`.

Evidencia observada:
- RED real: `npx vitest run src/theme/theme.service.spec.ts` → `Failed to resolve import "./theme.service"`.
- Primer GREEN: 4/6. Los 2 fallos eran reales: `effect()` es asíncrono y la clase no llegaba antes del primer render (flash de tema incorrecto al boot). **Corrección de producto, no del test**: se eliminó el `effect` y la clase se aplica de forma sincrónica en el constructor y en `setDarkMode`. → 6/6 verde.
- `pnpm test:ci` → **44 archivos, 255 tests, 0 fallos**.
- `pnpm build` → bundle generado; CSS principal 85.7 kB con `@import 'tailwindcss'` **resuelto** (0 residuos), preflight aplicado (`box-sizing:border-box`), `--color-gray-200` presente y variante `app-dark` presente. Tailwind v4 es JIT: aún no emite `.flex` porque ningún template usa utilidades todavía.
- `pnpm peers check` → los mismos 3 avisos preexistentes; Tailwind no introduce ninguno.

Nota de orden de cascada: el CSS de PrimeNG se inyecta **sin capa** (`cssLayer: false`, el default de PrimeNG 22, igual que Sakai). El CSS de la app legacy también es sin capa, así que conserva prioridad sobre las utilidades de Tailwind — la UI actual no cambia visualmente mientras el shell nuevo entra en T4.
