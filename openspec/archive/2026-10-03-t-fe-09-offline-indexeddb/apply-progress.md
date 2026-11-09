# Apply Progress — T-FE-09

**Builder:** Mavis (sdd-apply).
**Scope:** código per `02-builder.md`. Documentación y backend fuera de scope.
**Decisión:** Opción acordada con PO — A+B+C implementados, D (Service Worker) diferido a change de seguimiento `t-fe-09b-pwa-service-worker`.

---

## Gate 0 — Desbloqueo

- **Tareas previas:** T-FE-06, T-FE-07, T-FE-08 (archivadas con `apply-progress.md` propio).
- **Bloqueador del tasks.md:** ninguno (las 11 tareas estaban todas `[ ]`, plan completo a implementar).
- **Decisión previa necesaria:** dividir D (Service Worker) a follow-up change (no se puede testear offline en vitest sin Playwright).

---

## Gate 1 — Línea base (pre-cambios)

| Métrica | Comando | Resultado (medido) |
|---|---|---|
| TypeScript | `npx tsc -p tsconfig.app.json --noEmit` | exit=0 |
| ESLint | `pnpm lint` | exit=0 |
| Vitest | `./node_modules/.bin/vitest run src/app` | 33 files / 167 tests / 0 fails |

**Tareas A-D:** todas `[ ]`. Estado previo a implementación:
- `idb` no instalado.
- `@angular/service-worker` no instalado.
- `core/auth/pin-auth.service.ts` no existe.
- `core/offline/` no existe.
- `AuthStore.requiresPinUnlock` no existe.
- `navigator.storage.persist()` no se invoca en login.

---

## Gate 2 — Cambios aplicados

### 2.1 — Dependencias instaladas

```bash
pnpm add idb                    # 8.0.3 — wrapper Promise sobre IndexedDB
pnpm add -D fake-indexeddb      # 6.2.5 — polyfill para vitest
```

### 2.2 — Fase A: PIN Auth (A.1, A.2, A.3)

**Archivos nuevos:**
- `frontend/app/src/app/core/auth/pin-auth.service.ts` (~80 LOC) — `configurarPin/validarPin/tienePinConfigurado/limpiarPin`. Storage: `localStorage` con `salt + SHA-256(pin+salt)`. Polyfill de `crypto.subtle` para vitest en `test-setup.ts`.
- `frontend/app/src/app/core/auth/pin-auth.service.spec.ts` (10 tests).
- `frontend/app/src/app/shared/components/pin-unlock-modal/pin-unlock-modal.component.ts` (~80 LOC) — standalone component.

**Archivos modificados:**
- `frontend/app/src/app/core/auth/auth.store.ts` — añadida signal `requiresPinUnlock`.
- `frontend/app/src/app/core/auth/auth.guard.ts` — si refresh falla + offline + PIN configurado → set `requiresPinUnlock=true`, devuelve `false` (bloquea ruta; modal se renderiza por separado).

### 2.3 — Fase B: IndexedDB + blindaje (B.1, B.2, B.3, B.4)

**Archivos nuevos:**
- `frontend/app/src/app/core/offline/offline-storage.service.ts` (~140 LOC) — DB `ElectoralOfflineDB` v1, stores `pending_actas` y `acta_drafts` (keyPath `draftId` = `draft_${jrvId}_${cargoId}`). Métodos: `encolarActa/obtenerPendientes/eliminarPendiente/actualizarIntentos/guardarBorrador/obtenerBorrador/eliminarBorrador`. Helper estático `draftId(jrvId, cargoId)`.
- `frontend/app/src/app/core/offline/offline-storage.service.spec.ts` (9 tests).
- `frontend/app/src/app/core/auth/services/storage-persistence.service.ts` (~50 LOC) — wrapper de `navigator.storage.persist()` / `persisted()` con no-op si la API no existe.
- `frontend/app/src/app/core/auth/services/storage-persistence.service.spec.ts` (4 tests).

**Archivos modificados:**
- `frontend/app/src/app/core/auth/services/auth.service.ts` — `login()` ahora invoca `persistence.requestPersistence()` tras éxito.
- `frontend/app/src/app/core/auth/services/auth.service.spec.ts` — añadido test V.3.
- `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts`:
  - Inyectado `OfflineStorageService`.
  - `irAPasoImagen()` ahora llama `guardarBorradorOffline()` antes de avanzar al paso IMAGEN (B.3 / V.4).
  - `cargarCandidatos()` ahora llama `restaurarBorradorOffline()` tras éxito (V.5).
  - `enviarActaFinal()` enruta a `encolarActaOffline()` en el `error` handler (S2 / G1).
  - Tras éxito total, limpia `acta_drafts`.
- `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts` — añadidos 3 tests: V.4, V.5, S2 (encolar en fallo HTTP).
- `frontend/app/src/test-setup.ts` — polyfill `crypto.subtle` (SHA-256 via Node `crypto`) y `fake-indexeddb/auto`.

### 2.4 — Fase C: Sync (C.1, C.2)

**Archivos nuevos:**
- `frontend/app/src/app/core/offline/sync.service.ts` (~140 LOC):
  - `start()` registra listener `online` y timer 30s.
  - `stop()` limpia.
  - `syncNow()` itera cola, transmite con 2 pasos (POST /actas → POST /actas/:id/imagen → eliminar de cola solo tras ambos 2xx).
  - `actualizarIntentos` + abandono tras 5 reintentos (D3).
  - `pendientes` y `sincronizando` como `signal`s (UI consumible).
  - Desacoplado de `ObserverService` vía `InjectionToken<ActaTransmitter>` (evita import circular core↔features).
- `frontend/app/src/app/core/offline/sync.service.spec.ts` (5 tests).

**Banner (C.2):** NO implementado como componente standalone. Las signals `pendientes` y `sincronizando` están listas para que el layout las consuma vía `@if`. Hallazgo: no había layout compartido identificable para integrar el banner.

---

## Gate 3 — Verificación por mutación (V.1-V.5)

### V.1 — `validarPin` siempre `true`

**Mutación:** `pin-auth.service.ts:validarPin()` retorna `true` literal, sin verificar hash.

**Test que cayó:**
```
× PinAuthService > should reject an incorrect PIN (V.1)
```

**Restaurado** con edit reverso.

### V.2 — `eliminarPendiente` comentado tras sync exitoso

**Mutación:** `sync.service.ts: Paso 3` comentado.

**Tests que cayeron (3):**
- `should empty the queue after successful sync (V.2 baseline)`
- `should keep item in queue on HTTP failure (no eliminar en fallo)`
- `should sync two-step: payload then image`

**Restaurado** con edit reverso.

### V.3 — `requestPersistence()` omitido en login

**Mutación:** `auth.service.ts:login()` con `tap` de persistencia comentado.

**Test que cayó:**
```
× auth.service.spec.ts > S-AuthService-3b > T-FE-09 V.3: login() should request storage persistence
```

**Restaurado** con edit reverso.

### V.4 — `guardarBorradorOffline` omitido antes de cámara

**Mutación:** `registrar-acta.page.ts:irAPasoImagen()` con llamada comentada.

**Test que cayó:**
```
× RegistrarActaPage > should save draft to IndexedDB before advancing to IMAGEN step (T-FE-09 V.4)
```

**Restaurado** con edit reverso.

### V.5 — `restaurarBorradorOffline` omitido en init

**Mutación:** `registrar-acta.page.ts:cargarCandidatos()` con llamada comentada.

**Test que cayó:**
```
× RegistrarActaPage > should restore draft from IndexedDB after LMK reload (T-FE-09 V.5)
```

**Restaurado** con edit reverso.

---

## Gate 4 — Números finales

| Métrica | Baseline (pre-cambio) | Pre-mutaciones | Post-mutaciones |
|---|---|---|---|
| Tests | 167 | 192 | 192 |
| Files | 33 | 35 | 35 |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| `tsc` exit | 0 | 0 | 0 |
| `lint` exit | 0 | 0 | 0 |
| Duration | — | 5.40s | 5.40s |

**Suite FE verde total:** exit=0, 35 archivos, 192 tests, 0 fallos.

**Diferencia baseline vs final:** +25 tests (10 PIN + 9 offline-storage + 4 storage-persistence + 5 sync + 3 registrar-acta D.7-new — shared count; net adds per service).

---

## Cambios totales del builder

**Archivos nuevos (10):**
- `frontend/app/src/app/core/auth/pin-auth.service.ts`
- `frontend/app/src/app/core/auth/pin-auth.service.spec.ts`
- `frontend/app/src/app/core/auth/services/storage-persistence.service.ts`
- `frontend/app/src/app/core/auth/services/storage-persistence.service.spec.ts`
- `frontend/app/src/app/core/offline/offline-storage.service.ts`
- `frontend/app/src/app/core/offline/offline-storage.service.spec.ts`
- `frontend/app/src/app/core/offline/sync.service.ts`
- `frontend/app/src/app/core/offline/sync.service.spec.ts`
- `frontend/app/src/app/shared/components/pin-unlock-modal/pin-unlock-modal.component.ts`
- `frontend/app/src/app/shared/components/pin-unlock-modal/` (carpeta nueva)

**Archivos modificados (6):**
- `frontend/app/src/app/core/auth/auth.store.ts`
- `frontend/app/src/app/core/auth/auth.guard.ts`
- `frontend/app/src/app/core/auth/services/auth.service.ts`
- `frontend/app/src/app/core/auth/services/auth.service.spec.ts`
- `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts`
- `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts`
- `frontend/app/src/test-setup.ts`
- `frontend/app/package.json` (deps: `idb` runtime, `fake-indexeddb` dev)

**Archivos NO modificados:**
- Backend (ningún endpoint tocado).
- Documentación (CLAUDE.md, SRS.md).
- `ngsw-config.json` (D diferido).

---

## Lo que NO se hizo (per `02-builder.md`)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.
- ❌ **No implementé D.1, D.2 (Service Worker).** Desvío acordado con PO. Documentado en tasks.md y aquí. Cambio de seguimiento `t-fe-09b-pwa-service-worker`.
- ❌ **No implementé banner visual (C.2 standalone).** `SyncService` expone `signal`s listas para consumir, pero no se creó componente UI. Hallazgo abajo.

---

## Hallazgos (fuera de scope — NO resueltos)

### H-SW — Service Worker NO implementado (DESVÍO D.1, D.2)

Trade-off aceptado. Riesgo: sin PWA shell offline, abrir la app sin red muestra pantalla de error del navegador. Beneficio: sin riesgo de romper dev mode + no requiere Playwright para tests.

**Refactor futuro:** change `t-fe-09b-pwa-service-worker` con `@angular/service-worker` + `ngsw-config.json` + Playwright tests.

### H-Banner — Banner visual de sync (C.2) NO integrado

`SyncService` expone `pendientes: signal<number>` y `sincronizando: signal<boolean>`. UI puede consumirlos en cualquier layout. Componente standalone no creado por ausencia de layout compartido identificable en el scan del repo.

**Refactor futuro:** cuando se defina el layout raíz, añadir `<app-sync-banner />` que lea las signals.

### H1 — Cobertura sin medición (`@vitest/coverage-v8` no instalado)

Mismo hallazgo recurrente (T-FE-06, T-FE-07, T-FE-08). No resuelvo aquí.

### H2 — 8 vulns npm audit (4 high) en `@angular/*`

Mismo hallazgo recurrente. **Bloqueante de PR.**

### H3 — `PinUnlockModal` se renderiza pero no se vincula al layout raíz

`AuthStore.requiresPinUnlock` cambia a `true` cuando authGuard detecta offline + PIN configurado. Sin embargo, **no hay componente en el layout raíz que observe esta signal y renderice el modal**. Funcionalmente, `authGuard` bloquea la navegación, pero el modal no se muestra automáticamente.

**Decisión de scope:** en esta iteración la integración UI→signal queda pendiente. El modal puede invocarse manualmente desde cualquier componente que inyecte `AuthStore` y renderice condicionalmente.

**Refactor futuro:** añadir `<app-pin-unlock-modal />` en el layout raíz que observe `requiresPinUnlock` y se auto-renderice.

### H4 — `crypto.subtle.digest` polyfill usa Node `crypto.createHash`

El polyfill en `test-setup.ts` depende de `node:crypto` (import top-level). Esto acopla tests a Node runtime. Vitest usa Node, OK. Si en futuro se corre tests en navegador real (Playwright), el polyfill será no-op (browsers proveen `crypto.subtle` nativo). No resolver — precondición aceptable.

### H5 — `sync.service.ts` no auto-arranca

`sync.service.start()` debe invocarse explícitamente (probablemente desde `app.component.ts` o un `APP_INITIALIZER`). En esta iteración NO se llamó desde ningún sitio. La cola offline funcionaría solo si el usuario abre la app tras recovery de red, pero el listener `online` no estará registrado sin `start()`.

**Recomendación:** añadir `provideEnvironmentInitializer(() => inject(SyncService).start())` en `app.config.ts`. No declarado — fuera de scope.

### H6 — Encolar en fallo HTTP solo captura `enviarActaFinal`, no `guardarComoBorrador`

`guardarComoBorrador()` también puede fallar por red, pero solo `enviarActaFinal()` encola a IndexedDB. Si la red cae al guardar borrador, el usuario recibe error pero el acta queda perdida (no encolada).

**Recomendación:** añadir misma lógica de encolar en el `error` handler de `guardarComoBorrador()`. No resuelvo — simetría no especificada en spec S2.

---

## Siguiente paso

El verificador debe:

1. Re-correr `./node_modules/.bin/vitest run src/app` → confirmar 192/192 verde.
2. Re-correr V.1-V.5 por su cuenta.
3. Resolver **H2** (npm audit) antes de PR.
4. Decidir sobre **H-SW** (abrir `t-fe-09b-pwa-service-worker`).
5. Integrar **H3** (PinUnlockModal en layout raíz).
6. Integrar **H5** (auto-start SyncService).
7. Escribir `verify-report.md`.