# Spec — T-IN-11: GitHub Actions CI/CD

## Escenarios

### S1 — CI en PR

```gherkin
GIVEN    PR abierto a main
WHEN     cambios pusheados
THEN     GitHub Actions dispara workflow ci.yml
AND      corre mvn test + ng test en paralelo
AND      checks status en PR (pasa/falla)
```

### S2 — Tests fallan, PR bloqueado

```gherkin
GIVEN    PR con código que rompe test
WHEN     CI corre
THEN     checks marcan falla (red X)
AND      PR no puede mergearse (branch protected rule)
```

### S3 — CD en main

```gherkin
GIVEN    PR mergeado a main
WHEN     commit llega a main
THEN     GitHub Actions dispara workflow cd.yml
AND      corre tests nuevamente (guardrait)
AND      si pasan, despliega a Railway + Vercel
```

### S4 — Deploy visible en Railway + Vercel

```gherkin
GIVEN    CD running
WHEN     deploy completa
THEN     nueva versión en Railway
AND      nueva versión en Vercel
AND      ambos endpoints responden con nuevos cambios
```

## Mutaciones

### M1 — Remover CI trigger

Si se comenta `on: [pull_request]`, PR no dispara tests. Verificación: abrir PR, CI checks no aparecen.

### M2 — Remover CD trigger

Si se comenta `on: push` branch main, merge no dispara deploy. Verificación: push a main, Vercel no redeploys.

## Garantías

- **G1:** CI en PRs
- **G2:** Tests deben pasar antes de merge
- **G3:** CD solo en main
- **G4:** Deploy automático
