# apply-progress — T-FE-05: Pantalla de Login

**Rama:** `brydyan/sc-166/t-fe-04-pantalla-principal-observer`
**Fecha aplicación:** 2026-09-07
**Protocolo:** builder (`documents/agents/02-builder.md`) — TDD por mutación

---

## Resumen

| Fase | Estado | Notas |
|------|--------|-------|
| A — Componente + Formulario reactivo | ✅ Completa | Pre-existente al iniciar apply |
| B — Lógica de login + integración | ⚠ Parcial al iniciar → ✅ completa tras B.3 | **B.3 aplicado en esta sesión** |
| C — Error handling + UX | ✅ Completa | Pre-existente al iniciar apply |

**Tarea única pendiente al iniciar:** B.3 — redirección segregada por rol.
**Suite final:** 143 tests pasando (135 baseline + 8 nuevos `login.page.spec.ts`).
**Build:** no ejecutado en esta sesión (fuera de scope B.3).

---

## Fases A / B (parcial) / C — Estado al iniciar apply

Las fases A, B.1, B.2 y C se encontraban **completadas de sesiones previas**:

- `features/auth/login/login.page.{ts,html,scss}` existían con standalone component, ReactiveFormsModule, signals (`loading`, `errorMessage`, `showPassword`), validaciones de email/password, mapeo de errores HTTP (`readError`), y SCSS responsive.
- `submit()` ya invocaba `auth.login(credentials)` con `finalize` para resetear `loading`.

**Acción del builder en A/B/C:** ninguna. Se verificó leyendo los archivos y corriendo la suite.

---

## Fase B.3 — Alineación por Rol (Pendiente para Builder)

### Bug detectado

> `login.page.ts:51` invocaba `this.router.navigateByUrl('/dashboard')` de forma incondicional tras login exitoso. Para usuarios con rol `OBSERVER`, el backend denegaría `/dashboard` con 403, entrando en el bucle manejado por `auth.interceptor.ts:56` (también corregido en T-FE-04 D.1).

### Decisión técnica

**Leer `session.user.role` directamente del Observable retornado por `auth.login()`** en vez de inyectar `AuthStore`. Ventajas:

- No acopla la vista al store — el session ya viene en el `next` del observable.
- Menos superficie de mock en tests (no hay que inicializar AuthStore).
- Patrón consistente con el interceptor (donde sí se lee del store porque no hay session en el 403).

### Protocolo mutación aplicado

1. **Backup:** `cp login.page.ts login.page.ts.bak`.
2. **Mutación V.1 aplicada** — fix en `submit()`:
   ```ts
   next: (session) => {
     const target =
       session.user.role === 'OBSERVER'
         ? '/observer/mis-jrvs'
         : '/dashboard';
     this.router.navigateByUrl(target);
   }
   ```
3. **`login.page.spec.ts` creado desde cero** (no existía):
   - 4 tests de redirección por rol (S-Login-1a OBSERVER, 1b ADMIN, 1c MASTER, 1d COORDINATOR)
   - 2 tests de loading lifecycle (éxito + error)
   - 1 test de mapeo errorMessage en 401 (S-Login-3)
   - 1 test guard de form inválido
4. **Test con mutación:** 8/8 verde.
5. **Restaurar backup** (sin fix): test `S-Login-1a` **cayó** con
   > `expected '/observer/mis-jrvs', received '/dashboard'`
   — captura confirmada del bug.
6. **Re-aplicar mutación V.1:** 8/8 verde.
7. **`rm login.page.ts.bak`.**

### Resultado

- **Mutación V.1 capturada:** ✅ test S-Login-1a cae sin el fix, pasa con el fix.
- **Tests de regresión por rol** (ADMIN/MASTER/COORDINATOR): validan que `/dashboard` sigue funcionando para esos roles.
- **Tests auxiliares:** loading lifecycle, errorMessage en 401, guard de form inválido — todos verdes.
- **Sin regresiones:** suite completa 143/143.

### Archivos tocados

| Archivo | Cambio |
|---------|--------|
| `frontend/app/src/app/features/auth/login/login.page.ts` | Fix B.3 (línea 51) + comentario |
| `frontend/app/src/app/features/auth/login/login.page.spec.ts` | **Creado** — 8 tests |

---

## Compuertas finales

| Compuerta | Comando | Resultado |
|-----------|---------|-----------|
| Tests login.page | `pnpm exec vitest run login.page.spec.ts` | 8/8 ✅ |
| Suite frontend completa | `pnpm test:ci` | 143/143 ✅ |
| Build | `pnpm build` | ⏭ no corrido (fuera scope B.3) |

---

## Limitaciones / Trabajo futuro

- **`pnpm build` no se ejecutó** — B.3 es solo un cambio de runtime. Si el verifier lo requiere, basta con `pnpm build` en `frontend/app/`.
- **Inconsistencia detectada (no bloqueante)** entre `design.md` D2 y `tasks.md` A.2: D2 dice "sin toggle de visibilidad (MVP)", A.2 dice "con toggle de visibilidad". La implementación actual (`login.page.html:67-79`) tiene el toggle. Respeté lo implementado porque A.2 estaba marcado completo y verificado en el HTML. Si se quiere alinear `design.md` con el código, queda como T-FE-XX futuro.
- **No se añadió integración E2E** del flujo login → redirección por rol contra backend real. El coverage unitario cubre los 4 roles. Para CI se podría añadir un Playwright e2e que cree 4 usuarios (uno por rol) y verifique el redirect post-login contra el stack levantado.

---

## Notas para verifier

- **Cobertura nueva del bug B.3:** test `S-Login-1a` con doble asserción (`toHaveBeenCalledWith('/observer/mis-jrvs')` + `not.toHaveBeenCalledWith('/dashboard')`).
- **Regresión protegida** por tests `S-Login-1b/c/d` (ADMIN/MASTER/COORDINATOR → `/dashboard`).
- Si el verifier quiere reproducir la captura del bug: `git stash` los cambios en `login.page.ts` (no el spec) y correr `login.page.spec.ts` — el test S-Login-1a debe caer.
- **Consistencia con T-FE-04 D.1:** mismo patrón de fix aplicado en `auth.interceptor.ts:56`. Si uno se rompe, el otro lo cubre. Pero la fuente de verdad es el login (no el 403): un OBSERVER que haga login debe ir directo a `/observer/mis-jrvs` sin pasar por `/dashboard`.
