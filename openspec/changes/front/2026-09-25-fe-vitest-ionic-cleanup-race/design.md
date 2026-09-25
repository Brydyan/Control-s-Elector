# Design — 2026-09-25 · Vitest + Ionic Cleanup Race

---

## D1 — Cómo neutralizar la carrera asíncrona de Ionic en `app.component.spec.ts`

**Decisión**: Desacoplar `app.component.spec.ts` de la hidratación de Web Components de Stencil (`IonApp`, `IonRouterOutlet`) mediante `TestBed.overrideComponent` con `CUSTOM_ELEMENTS_SCHEMA` o stubs vacíos de testing para standalone.

**Por qué**:
`AppComponent` es un contenedor raíz puramente estructural:
```html
<ion-app>
  <ion-router-outlet></ion-router-outlet>
</ion-app>
```
Su test unitario sólo verifica que la instancia exista (`expect(app).toBeTruthy()`). Importar `IonApp` e `IonRouterOutlet` de `@ionic/angular/standalone` desencadena la inicialización asíncrona de Web Components de Stencil en `jsdom`. Stencil emite dynamic `import(...)` fuera de la zona de Angular. Cuando el test termina en milisegundos y Vitest cierra el `ViteModuleRunner`, la promesa rezagada de Stencil choca contra un runner cerrado, causando un `Unhandled Rejection`.

Al suprimir la instanciación de los componentes de Stencil en este test unitario aislado, el runner de Vite no recibe microtareas huérfanas tras el teardown.

**Alternativa descartada — Mantener `continue-on-error: true` en `.github/workflows/ci.yml`.**
Oculta regresiones reales en CI. Si mañana un desarrollador rompe la lógica de autenticación o cálculo de votos en frontend, CI marcará éxito porque los fallos de test se ignoran.

**Alternativa descartada — Actualizar Vitest o Ionic a ciegas en `pnpm-lock.yaml`.**
El equipo de frontend no está disponible. Actualizar a Ionic 9 o Vitest 6/LTS puede cambiar APIs de componentes, romper compatibilidad con Capacitor 8.4 o Angular 20.3. Un equipo backend no debe forzar migraciones mayores de dependencias de frontend sin la presencia del equipo responsable.

**Alternativa descartada — Introducir retrasos artificiales (`sleep`/`setTimeout` en `afterAll`).**
Hacer esperar a Vitest para que Stencil termine sus imports es no determinista, vuelve lenta la suite y fallará en runners de CI con CPU variable.

---

## D2 — Configuración de Teardown e Inlining en `vite.config.ts`

**Decisión**: Asegurar que las dependencias de `@ionic` estén correctamente configuradas bajo `test.deps.inline` o que el runner de Vitest use `pool: 'forks'` / un teardown limpio si persiste alguna microtarea en el entorno jsdom.

**Alternativa descartada — `test.isolate: false`.**
Desactivar el aislamiento de módulos mezcla estados de singletons, stubs y TestBed entre los 26 archivos de prueba, provocando fallos en cascada dependientes del orden de ejecución.

---

## D3 — Restauración de la compuerta de CI y eliminación de deuda técnica

**Decisión**: Revertir inmediatamente el cambio de `.github/workflows/ci.yml:132`, eliminando la línea `continue-on-error: true`, y borrar el archivo `FRONTEND_ISSUES.md`.

**Motivo**:
El archivo `FRONTEND_ISSUES.md` se creó explícitamente como registro de un workaround temporal ("How to Remove This Workaround"). Una vez resuelta la causa raíz, mantener el archivo o la excepción en CI sería tolerar deuda técnica conocida.
