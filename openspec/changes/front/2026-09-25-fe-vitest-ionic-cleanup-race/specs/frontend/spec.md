# Spec: 2026-09-25-fe-vitest-ionic-cleanup-race

Área: frontend / testing / CI

---

## Requisitos

### RF-TEST-01: Ejecución limpia y determinista de tests en modo CI
La suite de pruebas unitarias (`pnpm test:ci`) debe ejecutarse en su totalidad (118/118 tests), reportar todos los tests en verde y salir con código `0` sin registrar rechazos de promesas no manejados (`Unhandled Rejection`) durante la fase de teardown de Vitest.

### RF-TEST-02: Aislamiento del componente raíz de aplicación
`AppComponent` debe poder inicializarse e instanciarse en entorno de pruebas unitarias sin desencadenar la carga asíncrona de Web Components de Stencil ni registrar dependencias externas fuera del ciclo de vida del módulo runner de Vitest.

### RF-TEST-03: Compuerta dura en CI sin excepciones
El workflow de integración continua `.github/workflows/ci.yml` debe ejecutar el paso de pruebas de frontend con compuerta estricta. Si cualquier test unitario falla, el job `frontend-test` debe fallar inmediatamente y bloquear el pipeline.

---

## Escenarios

### Escenario 1: Ejecución de la suite completa de frontend en CI
- **GIVEN** el proyecto frontend con sus 26 archivos de prueba y 118 casos de test
- **WHEN** se ejecuta `pnpm test:ci` en el directorio `frontend/app`
- **THEN** los 118 tests pasan satisfactoriamente
- **AND** el proceso finaliza con código de salida `0`
- **AND** no se emiten errores de `Vite module runner has been closed` en stderr

### Escenario 2: Prueba unitaria de AppComponent
- **GIVEN** la suite de pruebas unitarias de `AppComponent` (`app.component.spec.ts`)
- **WHEN** se ejecuta el test con `TestBed.createComponent(AppComponent)`
- **THEN** la instancia del componente es creada (`app` is truthy)
- **AND** no quedan promesas ni microtareas asíncronas huérfanas al cerrar el test

### Escenario 3: Bloqueo estricto ante regresiones reales en CI
- **GIVEN** el workflow de GitHub Actions en `.github/workflows/ci.yml`
- **WHEN** se produce una falla intencional en cualquier test unitario de frontend
- **THEN** el comando `pnpm test:ci` retorna código distinto de cero
- **AND** el job `frontend-test` falla, deteniendo el pipeline de CI
