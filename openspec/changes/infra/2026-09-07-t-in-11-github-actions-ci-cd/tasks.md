# Tasks — T-IN-11: GitHub Actions CI/CD

## Fase A: Crear ci.yml (tests en PR)

- [ ] **A.1** — Crear `.github/workflows/ci.yml`:
  ```yaml
  name: CI

  on: [pull_request]

  jobs:
    backend-test:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        - uses: actions/setup-java@v3
          with:
            java-version: '21'
            distribution: 'temurin'
        - run: cd backend/electoral-api && mvn test

    frontend-test:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        - uses: actions/setup-node@v3
          with:
            node-version: '18'
        - run: cd frontend/app && npm install && npm test -- --watch=false --browsers=ChromeHeadless
  ```

- [ ] **A.2** — Commit y push

**Compuerta A:** .github/workflows/ci.yml en repo.

## Fase B: Crear cd.yml (deploy en main)

- [ ] **B.1** — Crear `.github/workflows/cd.yml`:
  ```yaml
  name: CD

  on:
    push:
      branches: [main]

  jobs:
    test:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        - uses: actions/setup-java@v3
          with:
            java-version: '21'
        - uses: actions/setup-node@v3
          with:
            node-version: '18'
        - run: cd backend/electoral-api && mvn test
        - run: cd frontend/app && npm install && npm test -- --watch=false --browsers=ChromeHeadless

    deploy:
      needs: test
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        # Railway auto-deploy (webhook trigger)
        # Vercel auto-deploy (webhook trigger)
        # Ambos automáticos via Vercel/Railway integración GitHub
  ```

- [ ] **B.2** — Commit y push

**Compuerta B:** .github/workflows/cd.yml en repo.

## Fase C: Test CI (abrir PR)

- [ ] **C.1** — Hacer commit, push a rama feature
- [ ] **C.2** — Abrir PR a main
- [ ] **C.3** — Ir a PR → Checks
- [ ] **C.4** — Ver ci.yml ejecutando (amarillo/en progreso)
- [ ] **C.5** — Esperar a que pase (verde) o falle (rojo)

**Compuerta C:** CI checks visibles en PR.

## Fase D: Test CD (merge a main)

- [ ] **D.1** — Merge PR a main
- [ ] **D.2** — Ir a Actions → CD workflow
- [ ] **D.3** — Ver deploy en progreso
- [ ] **D.4** — Esperar a que Railway + Vercel redeploy
- [ ] **D.5** — Verificar nuevos cambios en producción

**Compuerta D:** Deploy automático completado.

## Fase E: Proteger main branch

- [ ] **E.1** — En GitHub repo → Settings → Branches → Add rule
- [ ] **E.2** — Branch name pattern: `main`
- [ ] **E.3** — Enable: "Require status checks to pass before merging"
- [ ] **E.4** — Seleccionar checks: `backend-test` y `frontend-test`

**Compuerta E:** Main branch protegido, CI checks requeridos.

## Qué NO hacer

- **No correr CD en PRs** — solo en main
- **No hardcodear secrets** — usar GitHub Secrets
- **No esperar input manual** — automático es el punto

## Compuertas

| Fase | Verifica | Expected |
|------|----------|----------|
| A | File exists | .github/workflows/ci.yml en repo |
| B | File exists | .github/workflows/cd.yml en repo |
| C | PR → Checks | CI workflow running (yellow/green/red) |
| D | Actions tab | CD workflow running, Railway+Vercel redeploy |
| E | Branch settings | Require status checks enabled |

## Verificación por Mutación

- **V.1** — Comentar test step en CI → checks deberían no correr → restaurar
- **V.2** — Cambiar CD trigger a `push: branches: [develop]` → main push no dispara CD → restaurar
