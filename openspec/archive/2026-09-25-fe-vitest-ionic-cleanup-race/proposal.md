# Proposal — 2026-09-25 · Fix condición de carrera Vitest 5.0 + Ionic en cleanup

Origen: `FRONTEND_ISSUES.md`, commit `ab8c47c3796eba019e7ba06f90308faf2b6663c8` · Área: front / CI · Fecha: 2026-09-25

---

## Qué se construye

Solución definitiva a la condición de carrera durante la fase de limpieza de Vitest 5.0.0 con `@ionic/core` 8.8.13 que provoca `EXIT=1` (`Unhandled Rejection: Error: Vite module runner has been closed`) a pesar de que los 118/118 tests pasan.

Eliminación de la compuerta permisiva temporal `continue-on-error: true` en `.github/workflows/ci.yml:132` y retiro del archivo de seguimiento temporal `FRONTEND_ISSUES.md`.

## Por qué el estado declarado no es aceptable

Hoy `.github/workflows/ci.yml:132` tiene:

```yaml
- name: Run Unit Tests (Headless)
  working-directory: frontend/app
  run: pnpm test:ci
  continue-on-error: true
```

Tener `continue-on-error: true` en la suite de tests unitarios de frontend significa que **cualquier regresión real** introducida en componentes, guards, servicios de autenticación o formularios pasará en verde en CI sin alertar a nadie. El backend no puede ignorar silenciosamente la degradación de las compuertas de calidad del frontend.

## Causa Raíz

En `app.component.spec.ts`:
1. `AppComponent` importa directamente `IonApp` e `IonRouterOutlet` desde `@ionic/angular/standalone`.
2. Al ejecutar `TestBed.createComponent(AppComponent)`, Stencil / `@ionic/core` inicia la hidratación y registro asíncrono de Custom Elements con llamadas dinámicas a `import(...)` gestionadas por el `ViteModuleRunner`.
3. El test de `AppComponent` tarda apenas ~55ms y la suite completa concluye en ~3.8s.
4. Vitest cierra el worker y el runner de módulos de Vite inmediatamente al terminar los 118 tests.
5. Una microtarea rezagada de `@ionic/core` intenta resolver un módulo sobre el runner ya cerrado, disparando `Unhandled Rejection: Error: Vite module runner has been closed.` que Node/Vitest convierte en código de salida 1.

## Alcance

- Desacoplar `app.component.spec.ts` de la inicialización de Web Components pesados de `@ionic/core` mediante `CUSTOM_ELEMENTS_SCHEMA` o stubs de testing.
- Configurar `vite.config.ts` para asegurar aislamiento y teardown determinista de módulos en entorno jsdom.
- Remover `continue-on-error: true` de `.github/workflows/ci.yml`.
- Eliminar `FRONTEND_ISSUES.md`.
- Garantizar que `pnpm test:ci` finaliza con código de salida `0` y 118/118 tests pasando.

## Fuera de alcance

- **No** se actualizan versiones mayores o menores de Vitest, Vite, Angular ni Ionic en `pnpm-lock.yaml` (el equipo de frontend no está disponible; evitar roturas de API o Capacitor).
- **No** se altera la plantilla ni la funcionalidad productiva de `app.component.ts`.
- **No** se modifican tests ni código del backend Java.

## Criterio de cierre

1. `pnpm test:ci` en `frontend/app` corre los 118 tests, 118 pasan, 0 unhandled rejections y retorna código de salida `0`.
2. `.github/workflows/ci.yml` ejecuta `pnpm test:ci` con compuerta estricta (sin `continue-on-error: true`).
3. `FRONTEND_ISSUES.md` eliminado del repositorio.
4. Mutaciones ejecutadas y documentadas en `tasks.md`.
