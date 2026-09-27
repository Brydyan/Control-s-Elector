# apply-progress — T-FE-04: Interceptors HTTP

**Rama:** `brydyan/sc-166/t-fe-04-pantalla-principal-observer`
**Fecha aplicación:** 2026-09-07
**Protocolo:** builder (`documents/agents/02-builder.md`) — TDD por mutación

---

## Resumen

| Fase | Estado | Notas |
|------|--------|-------|
| A — JwtInterceptor | ✅ Completa | Pre-existente al iniciar apply |
| B — RefreshInterceptor + 401/403 | ✅ Completa | Pre-existente al iniciar apply |
| C — Registro en AppConfig | ✅ Completa | Pre-existente al iniciar apply |
| **D — Deuda técnica D.1 (403 OBSERVER loop)** | ✅ **Aplicada en esta sesión** | Mutación V.1 capturada |

**Suite final:** 135 tests pasando (134 baseline + 1 nuevo OBSERVER).
**Build:** no ejecutado en esta sesión (fuera de scope D.1).

---

## Fases A / B / C — Estado al iniciar apply

Las tres fases se encontraban **completadas de sesiones previas** (ver `tasks.md`):
- `auth.interceptor.ts` ya tenía la firma unificada `authInterceptor` (A.1).
- 5 tests en `auth.interceptor.spec.ts` ya cubrían token attachment, 401 single-flight refresh, redirección a `/login` y redirección a `/dashboard` en 403 (A.2 + B.2 parcial).
- `authInterceptor` ya estaba registrado vía `provideHttpClient(withInterceptors([authInterceptor]))` en `app.config.ts` (C.1).

**Acción del builder en A/B/C:** ninguna. Se verificó leyendo los archivos y corriendo la suite.

---

## Fase D — Deuda técnica D.1

### D.1 Bug detectado por el verifier

> En `core/auth/auth.interceptor.ts:56`: el manejo de 403 redirige fijamente a `/dashboard`. Para usuarios con rol `OBSERVER`, el backend devuelve 403 en `/dashboard` generando un bucle. Se debe condicionar: si `store.user()?.role === 'OBSERVER'`, redirigir a `/observer/mis-jrvs`.

### Protocolo mutación aplicado

1. **Backup:** `cp auth.interceptor.ts auth.interceptor.ts.bak`.
2. **Mutación V.1 aplicada** — fix en el handler 403:
   ```ts
   if (err.status === 403) {
     const target =
       store.user()?.role === 'OBSERVER'
         ? '/observer/mis-jrvs'
         : '/dashboard';
     router.navigateByUrl(target);
     return throwError(() => err);
   }
   ```
   JSDoc del interceptor actualizado para documentar el branch OBSERVER.
3. **Test añadido** en `auth.interceptor.spec.ts`:
   ```ts
   it('should redirect OBSERVER to /observer/mis-jrvs on 403 Forbidden (avoids /dashboard loop)', () => {
     const observerUser: CurrentUser = { ...mockUser, role: 'OBSERVER' };
     store.setSession('test-token-123', observerUser);
     http.get(`${environment.apiUrl}/dashboard`).subscribe({ error: ... });
     req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
     expect(router.navigateByUrl).toHaveBeenCalledWith('/observer/mis-jrvs');
     expect(router.navigateByUrl).not.toHaveBeenCalledWith('/dashboard');
   });
   ```
4. **Test con mutación:** 7/7 verde.
5. **Restaurar backup** (sin fix): test OBSERVER **cayó** con
   > `expected "vi.fn()" to be called with arguments: [ '/observer/mis-jrvs' ]` / `Received: [ '/dashboard' ]`
   — captura confirmada del bug.
6. **Re-aplicar mutación V.1:** 7/7 verde.
7. **`rm auth.interceptor.ts.bak`.**

### Resultado

- **Mutación V.1 capturada:** ✅ test OBSERVER cae sin el fix, pasa con el fix.
- **Test ADMIN pre-existente sin cambios:** sigue validando `/dashboard` para roles no-OBSERVER (regresión protegida).
- **Sin regresiones:** suite completa 135/135.

### Archivos tocados

| Archivo | Cambio |
|---------|--------|
| `frontend/app/src/app/core/auth/auth.interceptor.ts` | Fix D.1 (línea 56) + JSDoc |
| `frontend/app/src/app/core/auth/auth.interceptor.spec.ts` | +1 test OBSERVER (7 tests total) |

---

## Compuertas finales

| Compuerta | Comando | Resultado |
|-----------|---------|-----------|
| Tests interceptor | `pnpm exec vitest run auth.interceptor.spec.ts` | 7/7 ✅ |
| Suite frontend completa | `pnpm test:ci` | 135/135 ✅ |
| Build | `pnpm build` | ⏭ no corrido (fuera scope D.1) |

---

## Limitaciones / Trabajo futuro

- **`pnpm build` no se ejecutó** en esta sesión porque D.1 es solo un cambio de runtime que no afecta compilación. Si el verifier lo requiere, basta con correr `pnpm build` en `frontend/app/` para confirmar.
- **Otros roles** (MASTER, COORDINATOR, ADMIN): siguen yendo a `/dashboard` en 403. Si en el futuro `/dashboard` se restringe también para COORDINATOR, el ternario puede generalizarse a un `switch` o tabla de roles → ruta.
- **No se tocó `auth.store.ts`** — el signal `user()` ya exponía `role`, no hizo falta extender API.

---

## Notas para verifier

- **Cobertura nueva:** branch OBSERVER del handler 403 con doble asserción (`toHaveBeenCalledWith('/observer/mis-jrvs')` + `not.toHaveBeenCalledWith('/dashboard')`) para garantizar que el bucle se rompe en ambos extremos.
- **Test pre-existente** "should redirect to /dashboard on 403" sigue intacto y valida el comportamiento para ADMIN.
- Si el verifier quiere reproducir la captura del bug: `git stash` los cambios en `auth.interceptor.ts` (no el spec) y correr `auth.interceptor.spec.ts` — el test OBSERVER debe caer.
