# Fixes Required — 2026-09-25-fe-vitest-ionic-cleanup-race

**Fecha:** 2026-10-04  
**Rama:** `brydyan/sc-174/t-fe-12-componentes-compartidos`  
**HEAD:** `161ef78cd6d5234744eb35c8a08e9589d1b62224`  
**Estado:** RESOLVED (0 BLOQUEADORES ACTIVOS — Verificado en Ronda 4)

---

### [RESUELTO Y VERIFICADO] CRITICAL 1 — Carrera asíncrona de `@ionic/core` en `app.component.spec.ts` con `isolate: true`

1. **Estado anterior (Ronda 3)**  
   Con `isolate: true` en `vite.config.ts`, `pnpm test:ci` fallaba con `exit=1` y `Unhandled Rejection: Error: Vite module runner has been closed.` originado en `app.component.spec.ts`. La plantilla de `AppComponent` montaba `<ion-router-outlet>`, cuyo `connectedCallback` ejecutaba un import dinámico rezagado de Stencil (`p-Sh0ICmPV.js`) sobre el worker ya cerrado.

2. **Fixes aplicados por el Builder (Ronda 4)**  
   - En `frontend/app/src/app/app.component.spec.ts`: sobreescritura de plantilla con `template: '<div></div>'` dentro de `TestBed.overrideComponent`, evitando la renderización de `<ion-router-outlet>` en el DOM de prueba.
   - En `frontend/app/src/test-setup.ts`: protección defensiva con bloque `try-catch` alrededor de `addIcons(...)` contra llamadas huérfanas en teardown.

3. **Verificación empírica independiente (Ronda 4)**  
   - **`pnpm test:ci`:** 42/42 suites pasadas (100%), 240/240 tests pasados (100%), 0 errores, 0 unhandled rejections, `exit=0`.
   - **Stderr limpio:** 0 ocurrencias de `Vite module runner has been closed`, 0 ocurrencias de `Unhandled Rejection`.
   - **Mutación C.1 (`app.component.spec.ts`):** `expect(app).toBeFalsy()` hace fallar inmediatamente el test `AppComponent > should create the app` con `exit=1` sin emitir errores no manejados.
   - **Mutación C.2 (`button.component.spec.ts`):** `expect(true).toBe(false)` produce `exit=1` bloqueando la suite de CI sin enmascaramiento.
   - **Compuertas de compilación:** `pnpm lint` exit 0 (0 errores), `pnpm build` exit 0 (bundle generado exitosamente en `frontend/app/www`).

4. **Conclusión**  
   CRITICAL 1 queda formalmente **RESUELTO Y VERIFICADO**. No existen bloqueadores pendientes para este cambio.
