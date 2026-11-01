# Apply Progress — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

**Builder:** Mavis (sdd-apply).
**Scope:** implementación de `tasks.md` según `documents/agents/02-builder.md`.
**Change:** `openspec/changes/front/2026-09-25-t-fe-13-capture-and-run-foto-primero/`.
**Rama de trabajo:** `brydyan/sc-361/t-fe-13-modo-foto-primero-capture-run-y-cotas`.
**HEAD al empezar:** `0d51b68` (*Merge origin/develop: resolver conflicto en registrar-acta.page.ts*).
**Base real (merge-base con `develop`):** `9c0b212`.

> **No se hizo commit, push, archivo ni `verify-report.md`.** Todo queda en el árbol de trabajo,
> según el rol Builder. Los archivos modificados son 9 + los 2 artefactos de esta carpeta.

---

## Gate 0 — Estado inicial y línea base

### 0.1 — `git status --short` al llegar a la rama

```
 M frontend/app/proxy.conf.json
 M frontend/app/vite.config.ts
```

**No estaba limpio.** Los dos archivos son configuración local de desarrollo (proxy a
`http://localhost:8080` + `server.proxy` de Vite) y **su contenido es idéntico al ya commiteado
en la rama** (`git diff <rama> -- <archivo>` no devuelve nada). Se apartaron con `git stash push`
para poder cambiar de rama, se comprobó la identidad byte a byte contra la copia en `/tmp` y se
descartó el stash. **Al empezar el trabajo, el árbol estaba limpio.**

### 0.2 — Línea base medida (no estimada)

La línea base se midió **sobre `9c0b212`** (la base real de la rama), con los cambios de esta
fase en un stash:

```bash
$ git stash push -u -m "tfe13-wip-baseline-measure"
$ git checkout develop          # 9c0b212
$ cd frontend/app && pnpm test:ci
```

```
 Test Files  54 passed (54)
      Tests  446 passed (446)
```

```
$ pnpm lint          → exit 0   (All files pass linting.)
$ pnpm format:check  → exit 0   (All matched files use Prettier code style!)
```

| Archivo de spec | Línea base |
| --- | --- |
| `registrar-acta.page.spec.ts` | 24 tests |
| `jrv-cargos.page.spec.ts` | 2 tests |
| `offline-storage.service.spec.ts` | 14 tests |
| **Total repo** | **54 files / 446 tests** |

**Discrepancia con la SDD:** la nota que circulaba en la sesión anterior decía "42 suites / 241
tests". **Es falso para este árbol**: el repo tiene **54 archivos de spec** y la línea base real
es **446 tests**. Las mediciones previas se habían hecho sobre `develop` viejo o sobre otro árbol.

### 0.3 — Hallazgo grave en el HEAD (arreglado en esta fase)

El commit `0d51b68` ("resolver conflicto en registrar-acta.page.ts") **commiteó el archivo con los
marcadores de conflicto sin resolver**:

```
<<<<<<< HEAD
…
=======
…
>>>>>>> origin/develop
```

Consecuencias medidas: declaraciones duplicadas (`DELAY_GUARDADO_BORRADOR_MS`, `registrarCambio`),
mezcla de estado plano y signals, y el import de `ActaStorageService` sin su campo inyectado.
**Ese HEAD no compila.** El archivo se reconstruyó limpio en esta fase (Gate 1), conservando
todo el comportamiento de `develop` (signals, foco, `descripcionDiferencia`, `actaEncolada`,
idempotencia, cola offline) más lo que exige T-FE-13.

---

## Gate 1 — Cambios aplicados

### A.2 — Capa de almacenamiento (IndexedDB `acta_drafts`)

**Ya estaba en HEAD** (`0d51b68`, +110 líneas en `offline-storage.service.ts`): `ActaDraft` con
`imageBlob: Blob | null` + `imageName`, tipo `ActaDraftSinFoto`, `guardarBorrador()` con merge
que **preserva la foto**, `guardarFotoBorrador()` que **preserva los votos**,
`obtenerBorrador()` normaliza a `null` y `obtenerBorradoresDeJrv()` indexado por `cargoId`.

**Verificado y completado en esta fase:** el spec del almacén pasó de **14 → 18 tests** (ver D.1).
El archivo de servicio **no se modificó** en esta fase.

### A.2 — Capa de pantalla (wizard)

`registrar-acta.page.ts` reescrito:

- **Sin `localStorage` en todo el flujo.** Se eliminó el uso de `ActaStorageService`: el borrador
  durable es IndexedDB y nada más (D4).
- `guardarBorradorOffline()` (votos, debounced 400 ms) y `guardarFotoOffline()` (foto, **inmediata
  y por su propio canal**) — dos escrituras separadas para que ninguna pise a la otra.
- `abrirCamara(input)` persiste el borrador **antes** de abrir la cámara (patrón "Guardar antes de
  capturar": Android LMK mata el proceso Chrome con la cámara abierta).
- `restaurarBorradorOffline()` restaura **votos y foto**; `restaurarFotoBorrador()` reconstruye el
  `File` y la vista previa, y descarta a propósito cualquier valor que venga como `string`
  (Base64) o que no sea un `Blob`.

### A.1 — Reordenamiento del wizard

- `type Step = 'IMAGEN' | 'VOTOS' | 'REVISION'`, `currentStep = signal<Step>('IMAGEN')`.
- `STEP_LABELS` = `['Foto', 'Votos', 'Revisión']`, `STEP_INDEX` = IMAGEN 0 / VOTOS 1 / REVISION 2.
- Paso 1 (IMAGEN): botón **"Continuar a Digitación de Votos"** (visible sólo cuando ya hay foto) y
  opción secundaria **"Digitar votos primero"** (excepción documentada en D1).
- Paso 2 (VOTOS): "Continuar a Revisión Final" (valida el checksum al pulsar y explica la
  diferencia) y "Continuar con Inconsistencia".
- Paso 3 (REVISIÓN): resumen, "Guardar Borrador" y "Transmitir Acta".
- El flujo ya no pasa por la evidencia: **la foto va primero**.

### B.1 / B.2 / B.3 — Cotas y cuadre estricto

`registrar-acta.page.ts`:

| Getter | Comportamiento medido |
| --- | --- |
| `alertaSufragantes` | `null` ≤350 · `'Advertencia: El padrón estándar es de 350 electores. Verifique si es una mesa especial.'` >350 · `'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes.'` >500 |
| `nivelAlertaSufragantes` | `null` / `'warning'` / `'error'` |
| `esSufragantesValido` | `totalSufragantes > 0 && totalSufragantes <= 500` |
| `excedeTopeSufragantes` | `totalSufragantes > 500` (el padrón vacío lo bloquea el checksum, no la cota) |
| `isChecksumValid` | `totalSufragantes > 0 && totalVotosComputados === totalSufragantes` (1:1 estricto, sin margen ni 3x) |

`registrar-acta.page.html`: banner `.cota-banner` con modificadores `--warning` / `--error`;
`role="status"` para el aviso y `role="alert"` para el error. El botón **"Transmitir Acta"** queda
`disabled` con `excedeTopeSufragantes`, y `enviarActaFinal()` / `guardarComoBorrador()` abortan con
el mensaje del límite antes de tocar el servidor.

`registrar-acta.page.scss`: estilos del banner con tokens del theme
(`--color-status-warning-*`, `--color-status-danger-*`, `--radius-md`, `--space-*`); **0 colores
crudos** (`pnpm check:tokens` → *Token guard: no violations*).

### C.1 — Captura Rápida de Actas

`jrv-cargos.page.ts` / `.html` / `.scss`:

- Botón de cabecera **"Captura Rápida de Actas (Foto Primero)"** (`data-testid="btn-captura-rapida"`).
- Panel `p-dialog` (focus trap + Escape + overlay, el mismo patrón que `app-confirm-dialog`) que
  recorre **una fila por dignidad**: estado (Foto capturada / Sin foto), vista previa y acciones
  "Tomar foto" / "Retomar foto" y "Digitar votos".
- La foto se guarda como **`Blob` binario** con `guardarFotoBorrador()` y la **misma clave** que el
  formulario de registro (`draft_{jrvId}_{cargoId}`): captura rápida y formulario comparten un
  único borrador por dignidad.
- Al abrir, lee `obtenerBorradoresDeJrv()` para saber qué actas ya tienen foto (el veedor no
  fotografía dos veces la misma acta).
- Las vistas previas son efímeras (`URL.createObjectURL`) y se revocan en `ngOnDestroy`.

---

## Gate 2 — Tests (D.1 / D.2)

### D.1 — Tests añadidos

| Archivo | Antes | Después | Δ |
| --- | --- | --- | --- |
| `registrar-acta.page.spec.ts` | 24 | **38** | +14 |
| `jrv-cargos.page.spec.ts` | 2 | **6** | +4 |
| `offline-storage.service.spec.ts` | 14 | **18** | +4 |
| **Total repo** | **446** | **468** | **+22** |

Cobertura de lo que pedía D.1:

- **Inicio en paso IMAGEN por defecto** → `T-FE-13 A.1: el wizard arranca en el paso IMAGEN (Foto Primero)`.
- **Persistencia en IndexedDB al capturar y recuperación** →
  `T-FE-13 A.2: la foto se guarda en IndexedDB (acta_drafts) al capturarla` y
  `T-FE-13 A.2: la foto del borrador se restaura tras un cierre por LMK`.
- **Ausencia de `localStorage` y de Base64** →
  `T-FE-13 A.2: el borrador NO se escribe en localStorage ni en Base64`
  (`vi.spyOn(Storage.prototype, 'setItem')` + `localStorage.length === 0` + el blob no es string).
- **Advertencia con 380 y bloqueo con 501** →
  `T-FE-13 B.1: con 380 sufragantes hay advertencia, no bloqueo`,
  `T-FE-13 B.1: con 501 sufragantes hay error y el envío queda bloqueado`,
  `T-FE-13 B.1: con 2500 sufragantes (error de tipeo) el envío queda bloqueado`,
  `T-FE-13 B.1: con 500 sufragantes la cota todavía se cumple`.
- **Cuadre exacto 1:1** → `T-FE-13 B.2: el cuadre es 1:1 estricto, sin margen ni 3x`.
- Banners → `T-FE-13 B.3: el banner de cota se pinta con el nivel correcto` y
  `T-FE-13 B.3: la revisión muestra la cota y bloquea "Transmitir Acta"`.
- Flujo Foto Primero → 4 tests `T-FE-13 A.1:*` (uno por cada rama del flujo).
- Captura Rápida → 4 tests `T-FE-13 C.1:*`.

### D.2 — Medición

El literal `npm test -- --run` de la SDD **no aplica a este repo**: `package.json` define `test` como
`vitest` (modo watch, **cuelga**) y `test:ci` como `vitest run`. La medición se hizo con el
equivalente no interactivo:

```bash
$ cd frontend/app && pnpm test:ci
```

```
 Test Files  54 passed (54)
      Tests  468 passed (468)
   Duration  8.44s
```

`exit=0`, **0 errores** y **0 rechazos de promesas no manejadas**.

---

## Gate 3 — Mutaciones (V.1 / V.2 / V.3)

Protocolo aplicado en las tres: `cp` al `.bak` → mutación → corrida → **anotación del nombre
exacto del test que cayó** → `cp` de vuelta → borrado del `.bak` → `git status --short` **idéntico**
antes y después. **Nunca se usó `git checkout --`.**

Archivo mutado en las tres: `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts`.

### V.1 — Quitar la validación de > 500 sufragantes

**Mutación:** `const TOPE_SUFRAGANTES_CNE = 500;` → `= 5000;` (línea 58).

```bash
$ npx vitest run src/app/features/observer/pages/registrar-acta
```

Tests caídos (4):

- `T-FE-13 B.1: con 501 sufragantes hay error y el envío queda bloqueado`
- `T-FE-13 B.1: con 2500 sufragantes (error de tipeo) el envío queda bloqueado`  ← el que nombra la SDD
- `T-FE-13 B.3: el banner de cota se pinta con el nivel correcto`
- `T-FE-13 B.3: la revisión muestra la cota y bloquea "Transmitir Acta"`

```
 Tests  4 failed | 34 passed (38)
```

Restaurado: `TOPE_SUFRAGANTES_CNE = 500` (línea 58) y `git status --short` idéntico.

### V.2 — Cambiar el paso inicial a 'VOTOS'

**Mutación:** `readonly currentStep = signal<Step>('IMAGEN');` → `signal<Step>('VOTOS')` (línea 156).

Tests caídos (4):

- `T-FE-13 A.1: el wizard arranca en el paso IMAGEN (Foto Primero)`  ← el flujo Foto Primero
- `T-FE-13 A.1: tras capturar la foto aparece "Continuar a Digitación de Votos"`
- `T-FE-13 A.1: "Digitar votos primero" permite saltar la foto`
- `should have file input with accept="image/*" and capture="environment" (S3)`

```
 Tests  4 failed | 34 passed (38)
```

Restaurado: `signal<Step>('IMAGEN')` (línea 156) y `git status --short` idéntico.

### V.3 — Sustituir IndexedDB por `localStorage.setItem`

**Mutación:** en `guardarBorradorOffline()`, `await this.offlineStorage.guardarBorrador({…})` →
`localStorage.setItem('acta_drafts', JSON.stringify({…}))`.

Tests caídos (4):

- `T-FE-13 A.2: el borrador NO se escribe en localStorage ni en Base64`  ← el de arquitectura offline/no-localstorage
- `T-FE-13 A.2: el autoguardado de los votos NO borra la foto ya guardada`
- `S6: el paso de votos persiste un borrador para no perder lo escrito`
- `S6: al cambiar de paso se guarda el borrador antes de navegar`

```
 Tests  4 failed | 34 passed (38)
```

Restaurado desde el `.bak` y `git status --short` idéntico. **Verificación de integridad:** los
archivos mutados quedaron byte a byte iguales a su estado previo a las mutaciones (comparados
contra la copia en `/tmp/tfe13-backup`).

---

## Gate 4 — Compuertas finales (medidas)

```bash
$ cd frontend/app
$ pnpm lint          → exit 0   (All files pass linting.)
$ pnpm format:check  → exit 0   (All matched files use Prettier code style!)
$ pnpm build         → exit 0   (Application bundle generation complete.)
$ pnpm test:ci       → exit 0   (54 files / 468 tests)
$ pnpm check:tokens  → exit 0   (Token guard: no violations.)
```

- `pnpm build`: **1 warning en total** y es preexistente
  (`NG8113: ButtonComponent is not used within the template of DashboardPage`,
  `src/app/features/dashboard/dashboard.page.ts:44`) — **0 warnings** en los archivos de esta fase.
- `pnpm format:check` falló en 6 archivos tras la primera redacción; se corrigió con `pnpm format`
  y volvió a `exit 0` **sin tocar ningún archivo ajeno a la fase** (el `git status --short` final
  sigue teniendo los mismos 9 archivos).

### Archivos modificados por esta fase

```
 M frontend/app/src/app/core/offline/offline-storage.service.spec.ts
 M frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.html
 M frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.scss
 M frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.spec.ts
 M frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.ts
 M frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.html
 M frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.scss
 M frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts
 M frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts
 M openspec/changes/front/2026-09-25-t-fe-13-capture-and-run-foto-primero/tasks.md
?? openspec/changes/front/2026-09-25-t-fe-13-capture-and-run-foto-primero/apply-progress.md
```

---

## Hallazgos (fuera de scope — no se tocaron)

1. **`ActaStorageService` quedó sin consumidores.**
   `frontend/app/src/app/features/observer/services/acta-storage.service.ts` era el único usuario de
   `localStorage` del flujo de registro y A.2/D4 lo prohíben. Con esta fase, su único importador
   desaparece. **No se borró**: eliminar un servicio compartido es decisión del equipo y el archivo
   puede seguir usándose desde otros flujos. Recomiendo retirarlo en un change aparte.

2. **La rama está detrás de `origin/develop`.** `origin/develop` (`c8bb737`, 5 commits por delante)
   **NO es ancestro** de la rama: el commit `0d51b68` dice "Merge origin/develop" pero tomó el
   `origin/develop` viejo (`9c0b212`). Antes de abrir el PR hay que mergear/rebasear
   `origin/develop` sobre la rama — y ese merge **vuelve a tocar `registrar-acta.page.ts`**, así que
   hay que resolverlo contra el archivo ya reconstruido de esta fase.

3. **El HEAD de la rama no compilaba** (marcadores de conflicto commiteados). Corregido en esta
   fase reconstruyendo el archivo; el merge del punto 2 tiene que respetar esa reconstrucción.

4. **`fake-indexeddb` no preserva el `Blob`** (medido). Por eso los asserts de persistencia comprueban
   *"no es string ni no es Base64"* y no `instanceof Blob`. El componente además tolera un blob
   aplanado: lo ignora y deja que el veedor vuelva a fotografiar, en vez de romper la pantalla.

5. **`tsc -p tsconfig.spec.json --noEmit` no es compuerta en este repo**: arrastra errores
   preexistentes en más de 20 specs (namespace `jasmine` no declarado, `global`/`fail` sin
   tipar, etc.). Los 3 errores que esta fase introducía sí se corrigieron; los compuertas reales son
   `pnpm lint` y `pnpm build`.

6. **`pnpm test` cuelga** (watch). Toda medición de esta fase usó `pnpm test:ci`.

7. **Los tests que mutan estado del componente fuera de un evento del DOM** disparan `NG0100`
   (`ExpressionChangedAfterItHasBeenCheckedError`) en modo desarrollo. Los tests de T-FE-13
   escriben por el DOM (`dispatchEvent`) para estar midiendo el flujo real y no un artefacto del
   framework. Patrón ya usado por los tests S5/S6 del archivo.

---

## Estado de las tareas

**Cerradas (11/11):** A.1 · A.2 · B.1 · B.2 · B.3 · C.1 · D.1 · D.2 · V.1 · V.2 · V.3

**No cerradas:** ninguna.

**Pendiente del rol Verify** (no corresponde al Builder): `verify-report.md`,
`fixes-required.md`, archivo del change y el PR.

---

## Ronda 2 — Verificación posterior (informe de estado)

Esta sección se añadió después de una ronda de verificación que **desplazó el árbol de trabajo**.

### Qué pasó

A las **17:25:22** un actor externo ejecutó `git stash` sobre el árbol y dejó la implementación
completa de T-FE-13 (12 archivos) en `stash@{0}`. Reconstruyó sólo
`registrar-acta.page.ts` y dejó el `.html` de `develop` → **árbol en rojo** (10 tests fallando) y
un `verify-report.md` con la premisa falsa de que "el Builder no escribió los specs".

### Qué se restauró

Los 12 archivos se recuperaron **sin `git checkout --`**, con `git show stash@{0}:<ruta> > <ruta>`:

```bash
$ git show "stash@{0}:frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts" \
    > frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts
```

**Medición tras restaurar (comprobada, no supuesto):**

```
lint=0  format=0  build=0  test:ci=0  tokens=0
Test Files  54 passed (54)
     Tests  468 passed (468)
```

### Dos archivos revertidos por estar fuera de scope

La ronda anterior había añadido un redirect `observer/registrar-acta` en
`frontend/app/src/app/app.routes.ts` y el ajuste del menú en
`backend/electoral-api-quarkus/.../014-menus-seed-aligned.sql`. Ese redirect **rompía**
`frontend/app/src/app/app.routes.spec.ts` (`rutas /observer/* usan loadComponent`). Se revirtieron
a HEAD por estar fuera del alcance de T-FE-13; el arreglo del menú queda como decisión del equipo
(el redirect debe vivir fuera del array `observerRoutes`). Detalle en `fixes-required.md` (CRITICAL 4).

### Resultado de la verificación

6 mutaciones ejecutadas por el verificador: **5 detectadas, 1 sobrevivió** (hueco de cobertura en
S3). Hallazgos con reproducción en `fixes-required.md`:

- **CRITICAL 1** — `abrirCamara()` abre la cámara sin esperar el guardado del borrador (D4 roto).
- **HIGH 2** — el input de archivo no se limpia: re-elegir el mismo archivo no actualiza la foto.
- **HIGH 3** — ningún test cubre «con 380 y la suma cuadrada, el acta se transmite» (S3).
- **CRITICAL 4** — el `verify-report.md` anterior tiene una premisa falsa.

**Estado final del árbol:** 54 files / 468 tests en verde; 9 archivos del change modificados y sin
commitear (el rol Builder no commitea).

---

## Ronda 3 — Aplicación de los fixes (rol Builder)

Encargo: *«corregir lo que dejan en el `fixes-required.md` es nuestra tarea como el rol
`02-builder.md`»*. Se aplican los tres hallazgos con reproducción; el cuarto (CRITICAL 4) es
administrativo.

### Qué se cambió

| Hallazgo | Archivo | Cambio |
| --- | --- | --- |
| **CRITICAL 1** | `registrar-acta.page.ts` (`abrirCamara`) | `void this.guardarBorradorOffline(); input.click();` → `void this.guardarBorradorOffline().then(() => input.click());` — la cámara se abre **después** de que el borrador quedó escrito (D4). |
| **HIGH 2** | `registrar-acta.page.ts` (`onFileSelected`) | `target.value = '';` tras persistir la foto: elegir de nuevo el mismo archivo vuelve a disparar `change`. |
| **HIGH 3** | `registrar-acta.page.spec.ts` | Test nuevo `T-FE-13 S3: con 380 sufragantes y la suma cuadrada, el acta se transmite`. |
| **CRITICAL 1 (test)** | `registrar-acta.page.spec.ts` | Test nuevo `T-FE-13 A.2: la cámara se abre sólo después de que el borrador quedó escrito`. |
| **HIGH 2 (test)** | `registrar-acta.page.spec.ts` | Test nuevo `T-FE-13 A.2: el input de archivo se limpia para poder repetir la captura`. |
| **CRITICAL 4** | `verify-report.md` | Banner **SUPERSEDED** con los números reales. `app.routes.ts` y `014-menus-seed-aligned.sql` ya revertidos (fuera de scope). |

### Comprobación de que los tests nuevos muerden

Revertir cada fix debe tumbar su test. Medido con `cp` al `.bak`, corrida del spec, restauración
con `cp` y `git status --short` idéntico:

| # | Fix revertido | Test que cayó |
| --- | --- | --- |
| F1 | `abrirCamara` vuelve a abrir sin esperar | `T-FE-13 A.2: la cámara se abre sólo después de que el borrador quedó escrito` |
| F2 | `onFileSelected` vuelve a no limpiar el input | `T-FE-13 A.2: el input de archivo se limpia para poder repetir la captura` |
| F3 | el envío vuelve a bloquearse desde 350 | `T-FE-13 S3: con 380 sufragantes y la suma cuadrada, el acta se transmite` |

**Ninguno es decorativo: los 3 caen.**

### Mutaciones del verificador, repetidas tras los fixes

Las 6 mutaciones M1–M6 se volvieron a correr: **6 de 6 detectadas**. La que sobrevivía antes
(M2, la cota 350 en vez de 500) **ahora cae** con el test de S3.

| # | Mutación | Test que cayó |
| --- | --- | --- |
| M1 | `@if (selectedImageFile)` → `@if (true)` | `T-FE-13 A.1: tras capturar la foto aparece "Continuar a Digitación de Votos"` |
| M2 | `enviarActaFinal()` bloquea desde 350 | `T-FE-13 S3: con 380 sufragantes y la suma cuadrada, el acta se transmite` **(antes sobrevivía)** |
| M3 | el botón de envío deja de deshabilitarse con 2500 | `T-FE-13 B.3: la revisión muestra la cota y bloquea "Transmitir Acta"` |
| M4 | `===` → `>=` en el cuadre | `T-FE-13 B.2: el cuadre es 1:1 estricto, sin margen ni 3x` + `S5: un voto por encima del padrón…` |
| M5 | `restaurarFotoBorrador()` no restaura | `T-FE-13 A.2: la foto del borrador se restaura tras un cierre por LMK` |
| M6 | `guardarFotoBorrador()` → `localStorage.setItem` | `T-FE-13 C.1: la captura rápida guarda la foto de la dignidad en IndexedDB` |

### Números medidos tras los fixes

```
$ cd frontend/app
$ pnpm lint          → exit 0
$ pnpm format:check  → exit 0
$ pnpm build         → exit 0   (1 warning preexistente en DashboardPage; 0 en este change)
$ pnpm check:tokens  → exit 0
$ pnpm test:ci       → exit 0
   Test Files  54 passed (54)
        Tests  471 passed (471)      # 468 + 3 tests de regresión
```

| Archivo de spec | Línea base | Ronda 2 | Ronda 3 |
| --- | --- | --- | --- |
| `registrar-acta.page.spec.ts` | 24 | 38 | **41** |
| `jrv-cargos.page.spec.ts` | 2 | 6 | 6 |
| `offline-storage.service.spec.ts` | 14 | 18 | 18 |
| **Total repo** | **446** | **468** | **471** |

### Cierre

`stash@{0}` (la red de seguridad de la ronda 2) se elimina: su contenido está superado por el
árbol actual. `stash@{1}` es el de GitHub Desktop y **no se toca**.
