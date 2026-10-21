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
| T4 | S4 | Shell de aplicación Sakai (sidebar, topbar, configurador de tema) sobre `layout/` existente | inline | done |
| T4a | S4, S6 | Prerrequisitos del shell: Tailwind 4 + `tailwindcss-primeui` + variante `dark` + `ThemeService` que activa el modo oscuro | inline | done |
| T5 | S3, S4 | Migrar primitivas propias de `shared/ui` y `shared/components` a componentes PrimeNG | inline | done (#122, #129) |
| T6 | S3, S4 | Migrar features por rol (dashboard, admin, coordinator, observer, auth) a PrimeNG | inline | done (#123-#127) |
| T7 | S3, S4 | Retirar Ionic del runtime, identidad real de Android y habilitante PWA | inline | done (#127, #128) |
| T8 | S3, S4 | Mapear `Menu.icono` del backend (claves estables) a iconos PrimeNG | inline | done (#122) |

## Delivery — cadena de PRs

Estrategia: **stacked PRs sobre `develop`**. Se construye apilado (cada PR base es el branch del anterior) y se aterriza de a uno: cuando un PR se mergea, el siguiente se retargetea a `develop` con `gh pr edit <n> --base develop`. No se uso PR tracker porque GitHub no permite crearlo sin diff, y porque los slices son estados intermedios de una migracion visual: conviene revisarlos en orden, no aterrizarlos de a uno en `develop`.

El trabajo **no espera merges**: cada slice se construye sobre el branch del PR anterior, mergeado o no.

```text
develop
  └── refactor/frontend-sakai-01-theme          → #117  (T3)  Stack MIT
       └── refactor/frontend-sakai-02-tailwind   → #118  (T4a) Tailwind 4 + ThemeService
            └── refactor/frontend-sakai-03-shell → #119  (T4)  Shell Sakai
                 └── refactor/frontend-sakai-04-primitives      → #122 (T5) shared/ui → PrimeNG
                      └── refactor/frontend-sakai-05-dashboard-admin → #123 (T6) dashboard + admin
                           └── refactor/frontend-sakai-06-coordinator → #124 (T6) coordinator inbox
                                └── refactor/frontend-sakai-07-acta-detalle → #125 (T6) acta detalle
                                     └── refactor/frontend-sakai-08-observer → #126 (T6) observer
                                          └── refactor/frontend-sakai-09-login-ionic → #127 (T6,T7) login + retiro de Ionic
                                               └── refactor/frontend-sakai-10-android-pwa → #128 (T7) Android + PWA
                                                    └── refactor/frontend-sakai-11-shared-components → #129 (T5) shared/components → PrimeNG
```

| PR | Rama | Unidades | Alcance | Diff |
|----|------|----------|---------|------|
| #117 | `...-01-theme` | T3 | Stack MIT: Angular 21 + PrimeNG 21 + preset institucional | 5.803 (537 authored) |
| #118 | `...-02-tailwind` | T4a | Tailwind 4 + `ThemeService` (modo oscuro funcional) | 521 (197 authored) |
| #119 | `...-03-shell` | T4 | Shell Sakai: `layout.service` por signals, wrapper, topbar, sidebar | 1.516 (neto +76) |
| #122 | `...-04-primitives` | T5 | `shared/ui` (8 primitivas propias) → componentes PrimeNG | 1.237 (neto −49) |
| #123 | `...-05-dashboard-admin` | T6 | Dashboard + admin (usuarios, sesiones) → PrimeNG | 1.048 (neto +24) |
| #124 | `...-06-coordinator` | T6 | Bandeja de actas (coordinator) → PrimeNG | 658 (neto −136) |
| #125 | `...-07-acta-detalle` | T6 | Detalle de acta (coordinator) → PrimeNG | 866 (neto −186) |
| #126 | `...-08-observer` | T6 | Observer: JRVs, cargos y registro de acta → PrimeNG | 216 (neto +38) |
| #127 | `...-09-login-ionic` | T6, T7 | Login → PrimeNG y **retiro de Ionic** del runtime | 534 (neto −334) |
| #128 | `...-10-android-pwa` | T7 | Identidad real de Android + service worker (PWA) | 107 |
| #129 | `...-11-shared-components` | T5 | `shared/components` → PrimeNG (paginator, confirm-dialog, pin-unlock) | 761 (neto −295) |

El lockfile generado es la única parte que no se puede partir sin romper la instalación: por eso #117 pide `size:exception` (5.266 líneas de lockfile de 5.803).

PR #115 y #116 (la primera cadena, sobre PrimeNG 22) quedaron cerrados y superados: ver L11.

Presupuesto de revision por PR: ver la columna Diff de la tabla anterior. El unico PR que pide `size:exception` es el #117, y solo por el lockfile generado.


## Decisions

### D2 — RESUELTO: el stack de UI queda en Angular 21 + PrimeNG 21 (MIT)
Hechos verificados contra el paquete instalado, el registro npm y los términos publicados:

| Versión | `LICENSE.md` | Depende de `@primeui/license-manager` | Peer Angular |
|---------|---------------|--------------------------------------|--------------|
| ≤ 17.18 | MIT | no | Angular ≤17 |
| 18 → 21.1.10 | **MIT** ("PRIMENG COMMUNITY VERSIONS LICENSE") | no | Angular 18→21 |
| 22.x | **PrimeUI License** (comercial) | **sí** | Angular 22 |

Consecuencias verificadas:
- El `LICENSE.md` de PrimeNG 22 exige una **license key válida**: "A missing, invalid, or expired key may cause the software to display a license notice." El paquete `primeng-license.mjs` inyecta un `div role="alert"` en el DOM. En los tests de este repo ya se observa `[PrimeUI] PrimeUI license is not configured.`
- La **Community License (gratis) excluye explícitamente** a los organismos públicos: "Not a public-sector body, government or government-funded organization". Este proyecto es del CNE → **no califica** para la licencia gratuita.
- La **Commercial License** cuesta **$599/dev** (precio de lanzamiento hasta el 2026-12-31; lista $799), perpetua, por desarrollador.
- La Community License dice: "open source projects are welcome to use the MIT-licensed versions of our libraries (**PrimeNG 21**, PrimeVue 4, PrimeReact 10, and earlier)".

Ramas evaluadas:
- **A) PrimeNG 22 + PrimeUI Commercial License** → cero cambio de código, pero compra y proceso de adquisición, y sin key la app muestra el aviso de licencia.
- **B) Angular 21 + PrimeNG 21 (MIT)** → gratis, sin key, sin aviso. Costo técnico del downgrading **Angular 22.2.0 → 21.2.x**: **TypeScript 6.0.3 se mantiene** (peer de Angular 21 = `>=5.9 <6.1`) y el frontend solo usa `computed`, `input`, `effect`, `afterNextRender` (todas disponibles en Angular 21). Además es **exactamente el pairing del template** (`sakai-ng` usa Angular 21 + PrimeNG 21.0.2 + @primeuix/themes ^2), así que el shell se porta sin traducción. Requisito: `@primeuix/themes` v2 usa `semantic.colorScheme.light/dark` en lugar de `light-dark()` de v3 → el preset necesita un ajuste.

**Decisión del usuario (2026-10-20):** *Angular 21 + PrimeNG 21 (MIT)*.

Rama ejecutada: **A) Angular 21 + PrimeNG 21 (MIT)**.

Detalle de la ejecución:
- Angular `22.2.0 → 21.2.25` (core, common, compiler, forms, platform-browser, platform-browser-dynamic, router, animations, compiler-cli, cli `21.2.27`, build-angular `21.2.27`, cdk `21.2.14`, angular-eslint `21.4.0`, language-service `21.2.25`).
- **TypeScript `6.0.3 → 5.9.3`**: obligatorio. `@angular/build@21` y `@angular-devkit/build-angular@21` declaran peer `typescript >=5.9 <6.0` (el `compiler-cli` acepta `<6.1`, pero el builder manda).
- `zone.js ~0.16.0` **se mantiene**: el peer de `@angular/core@21` es `~0.15.0 || ~0.16.0`.
- `primeng 22.1.3 → 21.1.10`, `@primeuix/themes 3.0.2 → 2.0.3`, `primeicons 8.0.2 → 7.0.0`, **`@primeicons/angular` eliminado** (Sakai usa las clases CSS `pi`, que es lo que ya hacía el shell).
- `tsconfig.app.json`: `ignoreDeprecations` `"6.0" → "5.0"` (valor aceptado por TS 5.9).
- `acta-detalle.page.html`: `data?.acta.urlEvidencia → data?.acta?.urlEvidencia`. El type-check de plantillas de Angular 21 con TS 5.9 rechaza el encadenamiento opcional parcial que Angular 22 aceptaba.
- El preset se reescribió para `@primeuix/themes` v2: los derivados (`color`, `contrastColor`, `hoverColor`, `activeColor`) ya no cuelgan de `semantic.primary` sino de `semantic.colorScheme.{light,dark}.primary`, y las superficies se movieron al `colorScheme`. Se añadió `colorScheme.dark.surface` con la rampa **slate** institucional (Aura usa zinc en oscuro, lo que rompía el sidebar `slate-900` del design system).

**Ninguna incompatibilidad más**: `providePrimeNG` ya usaba la API variadic en v21, `@analogjs/vite-plugin-angular@3.0.0-alpha.90` acepta `build-angular ^21`, `@ionic/angular@8` acepta `@angular/core >=16`, y el frontend solo usa `computed`, `input`, `effect` y `afterNextRender` (todas disponibles en Angular 21).

Verificación: **45 archivos, 265 tests, 0 fallos**; build verde; **cero avisos de licencia** (el `[PrimeUI] PrimeUI license is not configured` desapareció al dejar PrimeNG 22).

`pnpm peers check`: el aviso de `chokidar` **desapareció** (Angular 21 usa chokidar 5). Quedan dos: `@vitest/coverage-v8@5.0.3` quiere `vitest@5.0.2` (preexistente de la línea base) y `@angular/build@21` declara peer **opcional** `vitest ^4.0.8` (integración de test-runner que el proyecto no usa).

### D3 — Android: se conserva como funcionalidad, con habilitante PWA
Decisión del usuario (2026-10-20): "se quiere hacer esa parte android, ahí déjalo como funcionalidad".

Estado real verificado:
- `frontend/app/android/` es el proyecto Gradle del starter de Ionic/Capacitor. `capacitor.config.ts` tiene `appId: 'io.ionic.starter'` y `appName: 'app'` — **placeholders sin personalizar**.
- `webDir: 'www'` coincide con la salida del build (`ng build` → `www`). ✅
- **No hay Android SDK en la máquina** (`ANDROID_HOME` y `ANDROID_SDK_ROOT` sin definir, `~/Android/Sdk` inexistente). JDK 21 sí está. → El APK **no se puede compilar ni verificar en este entorno**; se puede configurar y dejar el build del APK para un entorno con SDK.
- **No hay service worker ni manifest PWA.** El soporte "offline" actual es IndexedDB (`idb`) + localStorage + Network Information API: los *datos* se cachean, pero **el shell de la app no**. En frío y sin señal, un observador en campo obtiene una página en blanco, no la app.

Recomendación: conservar `android/` y Capacitor como **objetivo de empaquetado**, y resolver el habilitante real —**service worker + manifest PWA**— en el PR6. Sin service worker, el APK es un wrapper que abre una app que no arranca sin señal. Además hay que corregir la identidad (`appId`/`appName`) antes de publicar un APK.

El shell Sakai es responsive (modos `static`/`overlay` + menú móvil), así que no bloquea el objetivo Android.

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
- La cadena está completa: #117 → #129, cada PR con su unidad de trabajo y su evidencia. No queda ningún componente de UI propio: las primitivas de `shared/ui` y los componentes de `shared/components` corren sobre PrimeNG.
- Pendiente de verificación en un entorno con Android SDK: el build del APK (el `appId` y el paquete ya están cambiados de forma mecánica, pero el Gradle no se puede correr acá).
- Pendiente de decisión del equipo: si el frontend ya tiene consumers, cada PR se mergea a `develop` en orden y el siguiente se retargetea a `develop` (`gh pr edit <n> --base develop`).

### L12 — T5 ejecutado: primitivas de `shared/ui` sobre PrimeNG (2026-10-20)
Commit `b695117`, PR **#122** (base: PR3).

Estrategia: **se conserva la API pública** de las 8 primitivas (selector, inputs, outputs) y solo cambia la implementación. Las 9 páginas consumidoras no se tocan en este slice.

Mapeo:
| Primitiva | Antes | Después |
|---|---|---|
| `app-button` | markup `.btn` propio + 120 líneas de SCSS | `p-button`, variantes → severidad, `sm`/`lg` → `small`/`large` |
| `app-badge` | markup `.badge` propio | `p-tag`, estados electorales → severidades |
| `app-card` | markup `.app-card` propio | `p-card`, slots de header/title/subtitle/acciones/footer |
| `app-error-state` | SVG + botón propios | `p-button` para el reintento, tokens de tema para los colores |
| `app-loading-state` | spinner CSS propio | `p-progressspinner`, conserva el texto para lector de pantalla |
| `app-skeleton` | markup `.skeleton` propio | `p-skeleton`, `shimmer` → `wave`, `circle` → shape |
| `app-empty-state` | markup propio + `ion-icon` | mismo layout sobre tokens de tema, icono traducido a PrimeIcons |
| `app-icon` | wrapper de `ion-icon` | **eliminado** — tenía cero consumidores |

Dos detalles que el revisor debe mirar:
1. **Los colores electorales sobreviven**: PrimeNG no tiene severidad para `CORREGIDA` (púrpura en el design system). Mapearla a la severidad neutra la habría fusionado visualmente con `BORRADOR`, así que el badge conserva el púrpura institucional con un override acotado. Los otros cinco estados mapean a `success`, `info`, `warn`, `danger` y `secondary`.
2. **El template del header de la card se declara siempre**: `p-card` resuelve su header con una content query, y una query no ve un template envuelto en un `@if`. La condición va *dentro* del template y una regla `:empty` oculta el contenedor del header cuando la card no tiene título, para no dejar padding vacío.

Nuevo archivo: `frontend/app/src/app/shared/ui/icon-map.ts` — traducción de nombres Ionic a clases PrimeIcons, usada por `empty-state` y reutilizable por las features en T6.

Evidencia observada:
- `pnpm test:ci` → **44 archivos, 271 tests, 0 fallos** (antes: 45 archivos, 265 tests; se eliminó el spec de `icon` y se agregaron 6 tests de mapeo).
- `pnpm build` → bundle generado.
- `pnpm lint` → todos los archivos pasan.
- Las primitivas pasaron de 1.027 a 947 líneas: **neto −80** aun con la integración de PrimeNG.

Riesgo: **item 3 (contratos que otros consumen)** — la API pública no cambia, pero el markup renderizado sí, así que las reglas que estilaban las clases internas viejas quedan muertas. La única fuera de `shared/ui` es `.badge-*` en `dashboard.page.scss`, que queda inerte porque la severidad ahora viene de PrimeNG; se limpia en T6.

### L11 — D2 ejecutado: downgrading a Angular 21 + PrimeNG 21 (2026-10-20)
Reconstrucción de la cadena de commits: el downgrading **se integra dentro del commit de la fundación** (T3) en lugar de quedar como un commit posterior, para que ninguna rama intermedia de la cadena exponga PrimeNG 22.

Commits resultantes sobre `develop`:
- `29dc8a9` — feat(frontend): add PrimeNG 21 with the institutional tokens
- `109f130` — feat(frontend): wire Tailwind 4 and add the theme service
- `c8934ce` — feat(frontend): replace the app shell with the Sakai layout

Evidencia observada:
- `pnpm test:ci` → **45 archivos, 265 tests, 0 fallos**.
- `pnpm build` → bundle generado.
- Aviso de licencia PrimeUI: **ausente** (`grep -ci license` sobre la salida de los tests → 0). Con PrimeNG 21 no existe `@primeui/license-manager`.
- `pnpm peers check` → el aviso de `chokidar` desapareció (Angular 21 usa chokidar 5). Quedan el de `vitest` ↔ `@vitest/coverage-v8` (preexistente) y el peer **opcional** `vitest ^4.0.8` de `@angular/build@21` (integración de test-runner no usada).

Dos correcciones de código que el downgradingowan:
1. `tsconfig.app.json`: `ignoreDeprecations` `"6.0"` → `"5.0"` (TS 5.9 rechaza `"6.0"`).
2. `acta-detalle.page.html`: `data?.acta.urlEvidencia` → `data?.acta?.urlEvidencia`. El encadenamiento opcional parcial es un bug latente que el type-check de Angular 22 no detectaba y el de Angular 21 sí.

### L10 — T4 ejecutado: shell Sakai (2026-10-20)
Archivos nuevos:
- `frontend/app/src/app/layout/layout.scss` — CSS estructural del shell (topbar fijo, sidebar fijo debajo, contenedor con margen según el modo de menú, estados `static`/`overlay`/colapsado, responsive con breakpoint en 768px). Registrado como stylesheet global en `angular.json` porque las clases las comparten layout, sidebar y topbar.
- `frontend/app/src/app/layout/layout.service.spec.ts` — 6 tests nuevos.

Archivos reescritos:
- `frontend/app/src/app/layout/layout.service.ts` — conserva la API pública (`sidebarOpen`, `desktopCollapsed`, `toggleSidebar`, `openSidebar`, `closeSidebar`) y agrega `menuMode` (`static`/`overlay`), `isDesktop`, `setMenuMode`/`toggleMenuMode` y seguimiento del `resize`.
- `frontend/app/src/app/layout/main-layout/main-layout.component.{ts,html}` — wrapper Sakai (`layout-wrapper`, `layout-main-container`, `layout-mask`) + `containerClass` derivado del estado del shell; conserva la lógica de metadatos de ruta (título/subtítulo/notificaciones).
- `frontend/app/src/app/layout/sidebar/sidebar.component.{ts,html}` — conserva **toda** la lógica de dominio (mapeo del catálogo de menú del backend, RBAC, fallback, `displayName`/`initials`, `logout`) y cambia solo la presentación: `ion-icon` → PrimeIcons, sections con clases del shell, cierre del drawer móvil al navegar.
- `frontend/app/src/app/layout/topbar/topbar.component.{ts,html}` — topbar Sakai: toggle de menú, marca, título/subtítulo, pill de estado de red, badge de solo lectura, notificaciones con `p-badge`, **toggle de tema claro/oscuro** conectado a `ThemeService`, y menú de usuario con `p-avatar` + `p-popover` que incluye el cierre de sesión.
- `frontend/app/src/app/layout/{sidebar,topbar}.component.spec.ts` — actualizados a los selectores nuevos; el topbar suma el caso del toggle de tema.

Archivos eliminados: `main-layout.component.scss`, `sidebar.component.scss` (300 líneas), `topbar.component.scss` (198 líneas) — su contenido vive ahora en el stylesheet global del shell.

Hallazgo: el mapeo de `menu.icono` a PrimeIcons **no requiere cambio de backend**. El catálogo guarda claves estables ("home", "document", "people", "pulse", …), no nombres de librería, así que el mapeo vive en el frontend y sobrevive a cualquier cambio de librería de iconos. Esto cierra T8 en su parte de frontend.

Evidencia observada:
- `pnpm test:ci` → **45 archivos, 264 tests, 0 fallos** (antes de T4: 44 archivos, 255 tests).
- `pnpm build` → bundle generado. Primer intento falló con `NG8002: Can't bind to 'ngClass'` (faltaba `CommonModule` en los imports del componente standalone) → corregido y verificado en verde.
- `pnpm peers check` → sin cambios respecto a la línea base.

Riesgo de esta unidad: **item 3 (contratos que otros consumen)** — el shell cambia la API pública de `LayoutService` (agrega, no quita) y el markup de `layout/`. La lógica de dominio del sidebar y la ruta del menú del backend no cambian, y sus tests lo cubren.

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

### L13 — T6 y T7 ejecutados: features, retiro de Ionic, Android y PWA (2026-10-20)
PRs **#123 a #128**.

Commits:
- `6eac0fe` + `cd3af27` — dashboard y admin a PrimeNG (PR #123), con el fix de los inputs
- `c52cf4c` — bandeja de actas (PR #124)
- `8c41ddf` — detalle de acta (PR #125)
- `9af0dcd` — observer (PR #126)
- `4758d7f` — login + retiro de Ionic del runtime (PR #127)
- `3e4ec50` — identidad real de Android + service worker (PR #128)

Aprendizajes que cambiaron la forma de trabajar:

1. **`p-inputtext` NO registra value accessor** en PrimeNG 21. `[(ngModel)]` sobre `<p-inputtext>` explota en runtime con NG01203. La forma correcta es `<input pInputText [(ngModel)]>`: el componente tiene el selector `[pInputText]`, se aplica sobre el input nativo y conserva el accessor de Angular. Lo mismo con `<textarea pTextarea>`.
2. **Los specs de admin no renderizaban el template** (no llamaban `fixture.detectChanges()`), por eso el bug de los inputs pasó verde. Corregido: ahora los specs crean la vista.
3. **El build es la puerta real**: en JIT los tests no fallan con un elemento desconocido (`NG8001`) ni con un tipo de binding incompatible; el compilador AOT sí. Por eso `pnpm build` va siempre junto a la suite.
4. **El `content query` de p-card no ve un `<ng-template>` envuelto en `@if`**: hay que declarar el template siempre y poner la condición DENTRO, más una regla `:empty` para que el contenedor vacío no deje padding.
5. **p-button aplica la severidad por variables de tema, no por clase** (no existe `p-button-danger`): para afirmar el mapeo hay que leer los getters del componente, no el DOM.
6. **p-steps se suscribe a `router.events`**: el mock del Router del spec necesita el stream aunque la página no navegue desde la tira.
7. **La app ya usaba build de Vite solo para Vitest**; el build real es esbuild. En este refactor eso no cambió, pero quedó documentado.
8. **Los tokens institucionales existen, pero las páginas los esquivaban**: los SCSS usaban `var(--text-muted, #64748b)` con fallbacks que el design system nunca define, asi que renderizaban el hex hardcodeado. Migrarlos a los tokens reales fue parte del trabajo de cada pagina.
9. **`Table` es el unico componente de PrimeNG 21 que no es standalone**: se importa `TableModule`, no `Table`.

Evidencia observada (en cada PR):
- `pnpm test:ci` → **44 archivos, 271 tests, 0 fallos**
- `pnpm build` → bundle generado
- `pnpm lint` → todos los archivos pasan
- `npx ng build --configuration production` → genera `ngsw.json`, `safety-worker.js` y `worker-basic.min.js`; el `ngsw.json` fue inspeccionado (app shell prefetch con 39 URLs, assets lazy, grupo `api-reads` con estrategia `freshness`, timeout 10 s).
- `grep -rn "ion-icon\|ionicons\|io.ionic.starter" src/ android/` → sin resultados (salvo la tabla de mapeo de iconos, que conserva a proposito los nombres legacy para que el catalogo del backend siga funcionando).

**Limite honesto**: el APK no se compilo ni verifico. No hay Android SDK en la maquina (`ANDROID_HOME` y `ANDROID_SDK_ROOT` sin definir, `~/Android/Sdk` inexistente); JDK 21 si esta. El cambio de identidad (`appId`, namespace, applicationId, paquete de la activity, deep-link scheme, nombre visible) es mecanico y completo, pero el build de Gradle necesita un entorno con el SDK.

### L14 — T5 cerrado: `shared/components` sobre PrimeNG (2026-10-20)
PR **#129** (commit `6fcebec`).

- `app-paginator` → `p-paginator` (con números de página, primero/último y selector de tamaño)
- `app-confirm-dialog` → `p-dialog` (el focus trap y el Escape los trae el componente)
- `app-pin-unlock-modal` → `p-dialog` + `p-inputotp` para los 4 dígitos
- **Eliminados**: `app-notificacion-bell` (ningún template lo usaba desde que el topbar paso a `p-badge`) y `focus-trap.directive` (solo lo necesitaba el confirm-dialog custom)

**Bug de integracion que la suite atrapo**: el `first` de `p-paginator` es el indice del primer registro, NO el numero de pagina. Con `page=1` y 20 filas, `first` debe ser 20. Sin la conversion (`first = page * size`, y `page = floor(first / rows)` al volver) el control reportaba "Pagina 1" mientras la tabla ya servia la segunda.

Evidencia: **42 archivos, 267 tests, 0 fallos**; build verde; lint limpio.

Con este PR queda **cero UI propia** en el frontend: todo lo que se ve lo dibuja PrimeNG. Lo que queda de CSS propio (1.925 lineas en las paginas + 370 en el shell) es geometria — grids, banners, listas — que PrimeNG no cubre y no debe cubrir.

### L15 — Cadena cerrada y follow-ups trackeados (2026-10-20)
La cadena quedó en **12 PRs: #117 → #130**. Verificacion de integridad: cada PR toca **solo** las areas de su unidad, sin contaminacion entre ramas.

Correccion de una hipotesis propia: se sospecho que PrimeNG emitia los tokens `--p-*` de paleta solo cuando un componente los usaba, lo que habria dejado reglas sin color. **La hipotesis era falsa**: el motor de estilos emite los 568 tokens del preset al cargar el tema. El unico token realmente inexistente era `--p-brand-primary`. El PR #130 unifica de todos modos el CSS propio sobre los tokens institucionales (robustez y coherencia) y agrega `primeng-theme.spec.ts`, que renderiza el tema y afirma que toda variable usada por el CSS propio existe.

Follow-ups trackeados como issues:

| Issue | Tema |
|---|---|
| #131 | **Verificacion visual** del refactor (bloqueante del merge): ninguna pagina se abrio en un navegador |
| #132 | Migrar el CSS propio (~2.774 lineas) a los tokens del sistema de diseno |
| #133 | Compilar y verificar el APK de Android (requiere un entorno con Android SDK) |
| #134 | Pistas de la API de PrimeNG 21: las trampas ya encontradas, con su evidencia |
