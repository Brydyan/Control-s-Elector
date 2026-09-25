# Tasks: 2026-09-25-fe-vitest-ionic-cleanup-race

Origen: `FRONTEND_ISSUES.md`, commit `ab8c47c3796eba019e7ba06f90308faf2b6663c8`
Rama sugerida: `fix/fe-vitest-ionic-cleanup-race`

Orden obligatorio: **A antes que B, y B antes que C.**
A constata la línea base y la compuerta permisiva. B aplica el aislamiento. C ejecuta las mutaciones obligatorias.

---

## A · Línea base y comprobación de compuerta

- [ ] **A.1** — Comprobar estado limpio de git: `git status --short`.
- [ ] **A.2** — Verificar la presencia de `continue-on-error: true` en `.github/workflows/ci.yml:132`. Anotar en `apply-progress.md`.
- [ ] **A.3** — Ejecutar la suite completa de pruebas unitarias:
  ```bash
  cd frontend/app && pnpm test:ci
  ```
  Comprobar recuento exacto de tests (118 tests en 26 suites).

---

## B · Implementación y endurecimiento de CI

- [ ] **B.1** — Desacoplar `app.component.spec.ts` de la inicialización de Stencil/Ionic:
  En `frontend/app/src/app/app.component.spec.ts`, utilizar `TestBed.overrideComponent(AppComponent, { set: { imports: [], schemas: [CUSTOM_ELEMENTS_SCHEMA] } })` o stubear los elementos `ion-app` e `ion-router-outlet`.
- [ ] **B.2** — Verificar si `frontend/app/vite.config.ts` requiere ajuste adicional en `test.deps.inline` o `test.server.deps.inline` para módulos de `@ionic`.
- [ ] **B.3** — Retirar la línea `continue-on-error: true` de `.github/workflows/ci.yml`.
- [ ] **B.4** — Eliminar el archivo temporal `FRONTEND_ISSUES.md`.

---

## C · Las mutaciones obligatorias

Protocolo: `cp` al `.bak`, mutar, correr, **anotar el nombre exacto del test que cayó**, restaurar con `cp`, `rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **C.1** — RF-TEST-02. En `frontend/app/src/app/app.component.spec.ts`, mutar la aserción a:
  ```typescript
  expect(app).toBeFalsy();
  ```
  Ejecutar `pnpm test:ci`.
  **Debe caer el test de `AppComponent`.**
  Anotar el nombre exacto del test caído en `apply-progress.md`. Restaurar `.bak`.

- [ ] **C.2** — RF-TEST-03 (Compuerta CI). En `frontend/app/src/app/shared/ui/button/button.component.spec.ts`, forzar un fallo:
  ```typescript
  expect(true).toBe(false);
  ```
  Ejecutar `pnpm test:ci`.
  Comprobar que el comando **retorna código de salida distinto de 0** (`echo $?` != 0) y que sin `continue-on-error` bloquearía CI.
  Anotar en `apply-progress.md`. Restaurar `.bak`.

---

## Qué NO hacer en esta fase

- **No** actualizar versiones de paquetes en `pnpm-lock.yaml` (ni Vitest, ni Ionic, ni Angular). El equipo de frontend no está disponible.
- **No** modificar la lógica ni los componentes productivos de `app.component.ts`. Solo se interviene el archivo de test `app.component.spec.ts` y la configuración de pruebas.
- **No** utilizar retrasos artificiales (`sleep`, `setTimeout`, `waitFor`) para enmascarar la condición de carrera.
- **No** mantener `continue-on-error: true` en `.github/workflows/ci.yml`.

---

## Compuertas antes de dar por terminada la fase

```bash
cd frontend/app
pnpm test:ci
echo "exit=$?"
```
- Total de tests: 118 pasando (26 suites).
- 0 rechazos de promesas no manejados (`Unhandled Rejection`).
- Código de salida: `exit=0`.

```bash
grep -n "continue-on-error" .github/workflows/ci.yml
# Debe retornar vacío para el job frontend-test

test ! -f FRONTEND_ISSUES.md && echo "FRONTEND_ISSUES.md eliminado correctamente"
```
