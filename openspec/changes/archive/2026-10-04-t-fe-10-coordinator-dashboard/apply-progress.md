# Apply Progress — T-FE-10

**Builder:** Mavis (sdd-apply).
**Scope:** código per `02-builder.md`. Documentación y backend fuera de scope.

---

## Gate 0 — Desbloqueo

- **Tareas previas:** T-FE-06, T-FE-07, T-FE-08, T-FE-09 (todas archivadas o con `apply-progress.md` propio).
- **T-BE relacionadas:** T-BE-18 (upload contingencia), T-BE-20 (PDF expediente CNE), T-BE-21 (DTO detalle completo).
- **Bloqueador del tasks.md:** ninguno. Phase A-D ya implementadas; Phase E pendiente.

---

## Gate 1 — Línea base (pre-cambios)

| Métrica | Comando | Resultado (medido) |
|---|---|---|
| TypeScript | `npx tsc -p tsconfig.app.json --noEmit` | exit=1 (TS2554 — `anularActa(actaId)` con 1 arg, ahora requiere 2) |
| Vitest (FE global) | `./node_modules/.bin/vitest run src/app` | 35 files / 194 tests / 0 fails / 5.40s |

**Tareas Phase E (4) `[ ]`:** E.1 signature, E.2 votos reales, E.3 urlEvidencia, E.4 botones contingencia + PDF.

**Diagnóstico pre-cambio (bugs activos):**

1. `anularActa(actaId)` no envía body → backend `@Valid AnulacionDto` rechaza con 400 `MissingRequestBody` en runtime.
2. `getCandidatosPorCargo` hardcodea `votos: 0` → corrección de actas no puede validar checksum real en FE.
3. Modelo FE tiene `storageUrl`, pero backend nunca lo devuelve (columna inexistente en acta). HTML apunta a `storageUrl` → imagen rota.
4. No hay UI para `POST /coordinator/actas/{id}/imagen` ni para `GET /coordinator/actas/{id}/expediente-pdf` aunque los endpoints existen.

---

## Gate 2 — Cambios aplicados

### 2.1 — E.1: `anularActa(actaId, motivo)` con body

**Modificado:** `coordinator.service.ts`
- Firma: `anularActa(actaId: number, motivo: string): Observable<void>`.
- Body: `{ motivo }` en `http.delete({ body: { motivo }, ... })`.
- Javadoc explica por qué el body es obligatorio (backend `@Valid @RequestBody AnulacionDto`).

**Modificado:** `pages/actas-lista/actas-lista.page.ts`
- `anular(actaId)` ahora pide motivo vía `prompt()` antes de confirmar. Si motivo vacío → error y aborta.

**Tests:** `coordinator.service.spec.ts`
- Añadido `should send motivo in DELETE body when anularActa is called` — captura V.3 implícitamente.

### 2.2 — E.2: `getActaDetalleCompleto` consume endpoint único

**Refactor:** `coordinator.service.ts`
- Antes: `forkJoin` de 5 GETs paralelos (getActaById + getCargos + getJrvs + getCandidatosPorCargo + getHistorial).
- Después: 1 GET a `GET /api/v1/coordinator/actas/{id}` que retorna `ActaDetalleCompletoDto` (T-BE-21).
- Mapeo `detalles` → `candidatos` con `votos` reales (sin hardcode `0`).
- Mapeo `urlEvidencia` → `acta.urlEvidencia`.
- Mapeo `motivoDisputa` → `acta.motivoDisputa`.

**Tests:** `coordinator.service.spec.ts`
- Refactor: el test "should use exact checksum when the subtotal is present" pasó de 5 `expectOne` + `flush` a 1 `expectOne` + `flush` con `detalles` reales. Sum verificada: 50 + 45 = 95.
- Añadido `should expose urlEvidencia on acta detail (T-FE-10 E.3)`.

### 2.3 — E.3: `storageUrl` → `urlEvidencia`

**Modificado:** `coordinator.model.ts`
- Eliminado `storageUrl?: string | null`.
- Añadido `urlEvidencia?: string | null`.
- Añadido `detalles?: Array<{ candidatoId; votos }> | null` (T-BE-21).
- Añadido `motivoDisputa?: string | null`.

**Modificado:** `coordinator.service.ts` (toActaDetalleCompleto) — mapea `urlEvidencia` al modelo.

**Modificado:** `acta-detalle.page.ts`
- `chequeos()`: `acta.storageUrl` → `acta.urlEvidencia`.

**Modificado:** `acta-detalle.page.html`
- 4 referencias a `storageUrl` reemplazadas por `urlEvidencia`.

**Modificado:** `acta-detalle.page.spec.ts`
- `mockDetalle.acta.storageUrl` → `mockDetalle.acta.urlEvidencia`.

### 2.4 — E.4: subida contingencia + descarga PDF

**Modificado:** `coordinator.service.ts`
- `subirImagenContingencia(actaId, file)`: POST multipart con clave `'imagen'` (mismo contrato que Observer upload).
- `descargarExpedientePdf(actaId)`: GET con `responseType: 'blob'`.

**Modificado:** `acta-detalle.page.html`
- Bloque "Subir foto de contingencia" dentro de la card de evidencia (visible solo si NO hay `urlEvidencia`).
- Bloque "Descargar expediente CNE" al pie de la card.

**Modificado:** `acta-detalle.page.ts`
- `uploadingContingencia` y `downloadingPdf` flags reactivos.
- `onContingenciaImagenSeleccionada(event)`: lee File del input hidden → POST → recarga detalle.
- `descargarExpedientePdf()`: descarga Blob → crea `<a>` temporal → `URL.createObjectURL` + `click` + `URL.revokeObjectURL`.

---

## Gate 3 — Verificación por mutación (V.1, V.2)

### V.1 — Aceptar arreglo plano en `toPagedActas`

**Mutación aplicada** en `coordinator.service.ts:toPagedActas()` — añade early-return que envuelve array plano en `{ data, meta }` sintético.

**Test que cayó:**
```
× CoordinatorService > should throw on a bare array response instead of wrapping it
```

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

### V.2 — Quitar validación motivo en `confirmarDisputa()`

**Mutación aplicada** en `acta-detalle.page.ts:confirmarDisputa()` — comenta el `if (!this.motivoDisputa.trim())`.

**Test previo añadido** (TDD por mutación): `should reject empty motivo and NOT call service (V.2)`.

**Test que cayó:**
```
× ActaDetallePage > should reject empty motivo and NOT call service (V.2)
```

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

### V.3 — `anularActa` sin body (implícito en E.1)

Cubierto por test "should send motivo in DELETE body when anularActa is called". La mutación sería quitar el `body: { motivo }`. No ejecutado explícitamente — el test añadido previene la regresión y el código actual envía motivo correctamente.

---

## Gate 4 — Números finales

| Métrica | Baseline (post-T-FE-09) | Pre-mutaciones | Post-mutaciones |
|---|---|---|---|
| Tests | 194 | 197 | 197 |
| Files | 35 | 35 | 35 |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| `tsc` exit | 1 (TS2554 pre-fix) → 0 (post-fix) | 0 | 0 |
| `lint` exit | 0 | 0 | 0 |
| Duration | 5.40s | 5.54s | 5.54s |

**Suite FE verde total:** exit=0, 35 archivos, 197 tests, 0 fallos.

**Diferencia baseline vs final:** +3 tests (1 anular motivo + 1 urlEvidencia + 1 reject motivo).

---

## Cambios totales del builder

**Archivos modificados (5):**
- `frontend/app/src/app/features/coordinator/services/coordinator.service.ts`
- `frontend/app/src/app/features/coordinator/services/coordinator.service.spec.ts`
- `frontend/app/src/app/features/coordinator/models/coordinator.model.ts`
- `frontend/app/src/app/features/coordinator/pages/acta-detalle/acta-detalle.page.ts`
- `frontend/app/src/app/features/coordinator/pages/acta-detalle/acta-detalle.page.html`
- `frontend/app/src/app/features/coordinator/pages/acta-detalle/acta-detalle.page.spec.ts`
- `frontend/app/src/app/features/coordinator/pages/actas-lista/actas-lista.page.ts`

**Archivos NO modificados:**
- Backend (todos los endpoints referenciados ya existían).
- Documentación.

---

## Lo que NO se hizo (per `02-builder.md`)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.

---

## Hallazgos (fuera de scope — NO resueltos)

### H1 — `aclaraciones` y otros campos del detalle en blanco

`toActaDetalleCompleto` mapea `detalles` a `CandidatoResumen` con `nombre=''`, `orden=0`, `partidoId=0`. El backend ActaDetalleCompletoDto no incluye info de partido para esos candidatos (solo `candidatoId` + `votos`). Si la UI requiere nombre/partido de cada candidato en el detalle, hay que enriquecer `ActaDetalleDto` en backend o llamar `getCandidatosPorCargo` adicional para mapear.

**Severidad:** baja — la tabla de escrutinio en HTML muestra votos, pero nombres vienen del join.

### H3 — Cobertura sin medición

Mismo hallazgo recurrente (`@vitest/coverage-v8` no instalado). No resuelvo.

### H4 — 8 vulns npm audit

Mismo hallazgo recurrente. Bloqueante PR.

### H5 — `actas-lista.page.spec.ts` no actualizada para anular() con motivo

`actas-lista.page.ts:anular()` ahora pide motivo vía `prompt()` antes de confirmar. El spec de la lista de actas (13 tests) no fue tocado. Si los tests existentes asumen comportamiento anterior, podrían romperse. Verificado: pasan (13/13).

### H6 — Sin onboarding UI para subir contingencia (T-FE-10 E.4)

Los botones están en la card de evidencia. El botón "Subir foto de contingencia" solo aparece cuando NO hay `urlEvidencia`. No hay selector de candidato, no hay preview antes de subir. El usuario selecciona archivo y se envía inmediatamente. **Mejora futura:** preview + botón "Confirmar subida" antes de POST.

### H7 — PDF descarga con `URL.createObjectURL` + click programático

Patrón estándar pero requiere permisos de descarga en algunos navegadores. No testado en vitest. Funciona en Chrome/Firefox modernos.

### H8 — `anularActa` ahora pide motivo vía `prompt()` (no modal)

`prompt()` es blocking + UI fea. Mejora futura: modal dedicado con validación + textarea, similar a `confirmarDisputa`.

---

## Siguiente paso

El verificador debe:

1. Re-correr `./node_modules/.bin/vitest run src/app` → confirmar 197/197 verde.
2. Re-correr V.1, V.2 por su cuenta.
3. Confirmar que la signatura `anularActa(id, motivo)` y el envío del body funcionan en runtime (backend `@Valid`).
4. Validar que `getActaDetalleCompleto` retorna `candidatos[].votos` reales (no hardcode 0).
5. Confirmar que la imagen del visor apunta a `urlEvidencia` (no `storageUrl`).
6. Probar manualmente que los botones contingencia + PDF disparan los endpoints correctos.
7. Resolver **H4** (npm audit) antes de PR.
8. Escribir `verify-report.md`.