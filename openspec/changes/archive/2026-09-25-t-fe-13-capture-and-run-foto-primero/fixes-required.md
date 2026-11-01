# Fixes Required — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

**Ronda:** verificación posterior a la implementación (rol Verify).
**Rama:** `brydyan/sc-361/t-fe-13-modo-foto-primero-capture-run-y-cotas` · **HEAD:** `0d51b68`
**Árbol verificado:** 9 archivos modificados del change + `tasks.md` + estos artefactos.
**Compuertas medidas por el verificador (no copiadas del Builder):**

```bash
$ cd frontend/app
$ pnpm lint          → exit 0
$ pnpm format:check  → exit 0
$ pnpm build         → exit 0   (1 warning preexistente en DashboardPage; 0 en este change)
$ pnpm check:tokens  → exit 0   (Token guard: no violations)
$ pnpm test:ci       → exit 0   (Test Files 54 passed / Tests 468 passed)
```

> **Nota de alcance:** este archivo reemplaza al `fixes-required.md` de la ronda anterior, que se
> apoyaba en una premisa falsa (ver **CRITICAL 4**). Los hallazgos de abajo son **nuevos** y
> tienen reproducción ejecutada.

---

## Estado de aplicación — ronda Builder (cerrada)

Encargo aplicado: *«corregir lo que dejan en el `fixes-required.md`»*. Los cuatro hallazgos están
**resueltos**; los tres con código tienen test de regresión que **muerde** (verificar: revertir el
fix tumba su test).

| Hallazgo | Estado | Cómo se cerró |
| --- | --- | --- |
| **CRITICAL 1** | ✅ RESUELTO | `abrirCamara()` ahora abre la cámara **después** del guardado: `void this.guardarBorradorOffline().then(() => input.click());`. Test: `T-FE-13 A.2: la cámara se abre sólo después de que el borrador quedó escrito`. |
| **HIGH 2** | ✅ RESUELTO | `onFileSelected()` limpia el input (`target.value = ''`) para que re-elegir el mismo archivo vuelva a disparar `change`. Test: `T-FE-13 A.2: el input de archivo se limpia para poder repetir la captura`. |
| **HIGH 3** | ✅ RESUELTO | Test `T-FE-13 S3: con 380 sufragantes y la suma cuadrada, el acta se transmite`. La mutación M2 (bloquear desde 350) **ahora cae**. |
| **CRITICAL 4** | ✅ RESUELTO | `verify-report.md` marcado como **SUPERSEDED** con los números reales; `app.routes.ts` y `014-menus-seed-aligned.sql` revertidos (fuera de scope, rompían `app.routes.spec.ts`); `stash@{0}` eliminado. |

**Medición tras los fixes:** `lint=0 · format:check=0 · build=0 · check:tokens=0` ·
`Test Files 54 passed (54) / Tests 471 passed (471)` (`exit=0`).

**Mutaciones del verificador repetidas tras los fixes: 6 de 6 detectadas** (M2 sobrevivía antes).
Detalle en `apply-progress.md` § *Ronda 3 — Aplicación de los fixes*.

---

## CRITICAL 1 — La cámara se abre antes de que el borrador quede escrito

1. **Síntoma** — `abrirCamara()` dispara `input.click()` sin esperar a que el borrador electoral
   quede escrito en IndexedDB. La garantía "Guardar antes de capturar" (D4 / A.2) **no se cumple**:
   el patrón existe para sobrevivir al Low Memory Killer de Android, que mata el proceso de Chrome
   mientras la cámara está abierta. Con el orden actual, la cámara puede abrirse antes de que la
   escritura termine, y el borrador previo al disparo puede perderse justo en el instante que la
   evidencia corre más riesgo.

2. **Causa raíz** — `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts:680-683`:

   ```typescript
   abrirCamara(input: HTMLInputElement): void {
     void this.guardarBorradorOffline();   // ← no se espera
     input.click();                        // ← la cámara se abre ya
   }
   ```

   `guardarBorradorOffline()` es `async` (hace `await` de `db.get` + `db.put` sobre IndexedDB).
   El `void` descarta la promesa: las dos líneas se ejecutan en el mismo tick.

3. **Reproducción** — sonda de verificación (artefacto efímero, ya eliminado del árbol):

   ```bash
   cd frontend/app
   # sonda: el servicio de storage devuelve una promesa que NO resuelve;
   # se llama a abrirCamara() y se comprueba si la cámara se abrió.
   npx vitest run src/app/features/observer/pages/registrar-acta/__verify-probe.spec.ts
   ```

   Salida medida contra el código actual:

   ```
   FAIL  PROBE T-FE-13 — guardar antes de capturar > no abre la cámara hasta que el borrador quedó escrito
   AssertionError: expected true to be false // Object.is equality
     Tests  1 failed | 1 passed (2)
   ```

   Es decir: **`camaraAbierta` ya es `true` mientras el borrador sigue sin escribirse.**

4. **Fix propuesto** — esperar la escritura antes de abrir la cámara, y dejar el caso como test
   permanente:

   ```typescript
   abrirCamara(input: HTMLInputElement): void {
     void this.guardarBorradorOffline().then(() => input.click());
   }
   ```

   El test que hay que incorporar en `registrar-acta.page.spec.ts` es el de la sonda: con
   `guardarBorrador` pendiente, `abrirCamara()` **no** debe abrir la cámara; tras resolver la
   promesa, sí.

5. **Verificación de cierre** — la sonda pasa, y **al reintroducir el `void` sin espera la sonda
   vuelve a caer**. Con el fix aplicado, `pnpm test:ci` sigue en 54 files / 468 tests (o más, con el
   test nuevo incorporado).

---

## HIGH 2 — Volver a elegir el mismo archivo no actualiza la foto

1. **Síntoma** — en el paso IMAGEN el botón dice "Tomar otra fotografía" y el input de archivo es el
   mismo. Si el veedor elige **de nuevo el mismo archivo** (o la cámara genera un archivo con el
   mismo nombre y contenido), el navegador **no dispara `change`**: la previsualización no cambia y
   la foto que queda persistida en `acta_drafts` es la anterior. El acta se transmitiría con la
   evidencia equivocada sin ningún aviso.

2. **Causa raíz** — `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts:685-706`:
   `onFileSelected()` nunca limpia `target.value`, así que el input conserva la selección previa y
   el navegador la considera "sin cambios".

   El mismo flujo en la Captura Rápida **sí lo hace**, lo que confirma que es un descuido y no una
   decisión: `frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.ts:178`
   (`input.value = '';`).

3. **Reproducción** — por lectura del código y por el contrato del DOM (`input type="file"` sólo
   dispara `change` cuando la lista de archivos **cambia**). No es reproducible en `jsdom`: el
   entorno de test no implementa la supresión de `change` del navegador, así que un test unitario
   no la detecta — hay que verificarlo en navegador real o cubrirlo con el test del punto 4.

   ```bash
   # Evidencia de la asimetría entre los dos flujos:
   $ grep -n "input.value = ''" frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.ts
   178:      input.value = '';
   $ grep -n "target.value" frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts
   (sin resultados)
   ```

4. **Fix propuesto** — limpiar el input al final de `onFileSelected()`:

   ```typescript
   onFileSelected(event: Event): void {
     const target = event.target as HTMLInputElement;
     if (target.files && target.files[0]) {
       …
       void this.guardarFotoOffline(file);
       target.value = '';   // ← permite volver a elegir el mismo archivo
       return;
     }
     …
   }
   ```

5. **Verificación de cierre** — test que seleccione **dos veces el mismo archivo** y exija que
   `guardarFotoBorrador` se invoque **dos veces** (y que la previsualización apunte a la segunda).

---

## HIGH 3 — Ningún test cubre «con 380 y la suma cuadrada, el acta se puede transmitir»

1. **Síntoma** — la especificación S3 exige dos cosas: *«muestra un aviso amarillo»* **y** *«permite
   continuar si la suma cuadra exactamente con 380»*. La suite sólo verifica la primera. Los tests
   de B.1 consultan los getters (`alertaSufragantes`, `esSufragantesValido`,
   `excedeTopeSufragantes`), pero **ninguno comprueba que el envío ocurra** con 380. La suite no
   distingue un aviso de un bloqueo: una regresión que bloqueara el envío a partir de 350 (en vez
   de 500) pasaría inadvertida.

2. **Causa raíz** — falta el test de comportamiento (no de getter) del escenario S3 en
   `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts`.

3. **Reproducción** — mutación M2 (la corro el verificador, no se acepta la del `.md`):

   ```typescript
   // registrar-acta.page.ts — enviarActaFinal()
   if (this.excedeTopeSufragantes) {      // ← mutado
   if (this.alertaSufragantes !== null) { // ← bloquea desde 350
   ```

   ```bash
   $ cd frontend/app && npx vitest run src/app/features/observer/pages/registrar-acta
   Tests  1 failed | 38 passed (39)
   # el único fallo es la sonda de CRITICAL 1 (falla siempre, por diseño)
   # ⇒ la mutación NO la detecta ningún test
   ```

4. **Fix propuesto** — añadir a `registrar-acta.page.spec.ts`:

   ```typescript
   it('T-FE-13 S3: con 380 sufragantes y la suma cuadrada, el acta se transmite', () => {
     component.totalSufragantes = 380;
     component.votosPorCandidato.set(1, 380);
     observerServiceSpy.enviarActaDirecta.and.returnValue(of(/* acta */));

     component.enviarActaFinal();

     expect(observerServiceSpy.enviarActaDirecta).toHaveBeenCalled();
     expect(component.error()).toBeNull();
   });
   ```

5. **Verificación de cierre** — **la mutación M2 cae ese test** y el resto de la suite sigue verde.

---

## CRITICAL 4 — El `verify-report.md` de la ronda anterior tiene una premisa falsa

1. **Síntoma** — el `verify-report.md` y el `fixes-required.md` que estaban en la carpeta del change
   afirmaban que *«el Builder no incluyó los specs (D.1)»* y que la suite daba *«436 tests, 10
   fallando»*. **Es falso**: los specs estaban escritos y la suite daba **54 files / 468 tests en
   verde**. La causa fue la propia ronda anterior: hizo `git stash` de los **12 archivos** del
   Builder y reconstruyó sólo `registrar-acta.page.ts`, dejando el `.html` de `develop` → un árbol
   mitad y mitad, con 10 tests rojos.

2. **Causa raíz** — el `git stash` de la ronda anterior (`stash@{0}`, "WIP on
   brydyan/sc-361/…") dejó la implementación completa fuera del árbol de trabajo mientras el
   informe se escribía contra un estado que ya no era el del change.

3. **Reproducción** —

   ```bash
   $ git stash show --stat stash@{0} | tail -3
    frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts | 401 +++++
    …/registrar-acta/registrar-acta.page.ts                                     | 411 ++++++-----
    openspec/changes/front/2026-09-25-t-fe-13-capture-and-run-foto-primero/tasks.md | 30 +-
    12 files changed, 1271 insertions(+), 370 deletions(-)
   ```

   ```bash
   $ cd frontend/app && pnpm test:ci
    Test Files  54 passed (54)
         Tests  468 passed (468)     # contra el árbol restaurado
   ```

4. **Fix propuesto** — **regenerar `verify-report.md` contra el árbol actual** (o marcarlo
   explícitamente como *superseded* por esta ronda) y **eliminar `stash@{0}`** una vez aplicados los
   fixes de esta lista, para que nadie restaure por error una versión ya superada. Un informe con
   una premisa falsa en la carpeta del change induce al archivador a repetir trabajo ya hecho.

   Nota operativa: la ronda anterior también añadió un redirect `observer/registrar-acta` en
   `frontend/app/src/app/app.routes.ts` + el ajuste del menú en
   `backend/electoral-api-quarkus/.../014-menus-seed-aligned.sql`. Ese redirect **rompe**
   `frontend/app/src/app/app.routes.spec.ts` (`rutas /observer/* usan loadComponent`: el redirect no
   tiene `loadComponent`). Se **revirtieron** en esta ronda por estar fuera del alcance de T-FE-13 y
   por dejar la suite en rojo; si el equipo quiere el arreglo del menú, el redirect debe vivir
   **fuera** del array `observerRoutes` (o el spec debe tolerar las rutas de redirección).

---

## Qué NO hay que rehacer

- **La suite de T-FE-13 ya está escrita y en verde**: 38 tests en `registrar-acta.page.spec.ts`
  (+4 en `jrv-cargos.page.spec.ts`, +4 en `offline-storage.service.spec.ts`). El informe anterior
  decía que no existían: **sí existen**, y las 3 mutaciones V.1/V.2/V.3 del Builder las detects.
- **El invariante 1:1 estricto ya está en el backend**: `EnviarActaUseCase.validarSumaVotos`
  (`backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/acta/application/usecase/EnviarActaUseCase.java:76-84`)
  rechaza con 400 ante `total != totalSufragantes`. **No hay fórmula 3x que eliminar**: no toques
  esa validación.
- **El merge de las dos escrituras del borrador se sondeó**: 200 iteraciones con `guardarBorrador` y
  `guardarFotoBorrador` en vuelo a la vez → **0 fotos perdidas**. No hay que rehacerlo.
- **`ActaStorageService` sin consumidores**: ya está anotado en `apply-progress.md` (Hallazgos) como
  decisión de alcance. **No lo borres en este change**: es un servicio compartido y retirarlo es un
  change aparte.
- **El orden del wizard, los getters de cota, el banner y el `p-dialog` de Captura Rápida** están
  implementados y verificados por mutación (M1, M3, M4, M5, M6). No los reescribas.

---

## Mutaciones ejecutadas por el verificador

Todas con `cp` al `.bak`, corrida del spec, restauración con `cp` y `git status --short` idéntico
antes y después. **Nunca `git checkout --`.**

| # | Mutación | Test que cayó |
| --- | --- | --- |
| M1 | `@if (selectedImageFile)` → `@if (true)` (el botón deja de depender de la foto) | `T-FE-13 A.1: tras capturar la foto aparece "Continuar a Digitación de Votos"` |
| M2 | `enviarActaFinal()` bloquea con `alertaSufragantes !== null` (cota 350 en vez de 500) | **ninguno** → ver **HIGH 3** |
| M3 | `[disabled]="actaEncolada() \|\| excedeTopeSufragantes"` → `[disabled]="actaEncolada()"` | `T-FE-13 B.3: la revisión muestra la cota y bloquea "Transmitir Acta"` |
| M4 | `totalVotosComputados === totalSufragantes` → `>=` | `T-FE-13 B.2: el cuadre es 1:1 estricto, sin margen ni 3x` y `S5: un voto por encima del padrón se marca inválido sin recortarlo` |
| M5 | `restaurarFotoBorrador()` no restaura la foto | `T-FE-13 A.2: la foto del borrador se restaura tras un cierre por LMK` |
| M6 | `guardarFotoBorrador()` → `localStorage.setItem` | `T-FE-13 C.1: la captura rápida guarda la foto de la dignidad en IndexedDB` |

**5 de 6 mutaciones detectadas.** La que sobrevive (M2) es **HIGH 3**.
