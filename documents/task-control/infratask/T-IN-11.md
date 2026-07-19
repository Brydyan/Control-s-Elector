# T-IN-11 — GitHub Actions (CI/CD básico)

**Estado:** TODO
**Dificultad:** Intermedio
**Tiempo estimado:** 1 hora
**Depende de:** T-IN-07, T-IN-09 ✅

---

## ¿Qué vas a construir?

Workflows GitHub Actions: automatización que corre tests en PRs y depliega en `main` (Railway + Vercel).

---

## Archivos a crear

```
.github/workflows/
  ci.yml       ← Build + tests en PR
  deploy.yml   ← Deploy en push a main
```

---

## Paso 1 — `ci.yml` (Continuous Integration)

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
    branches: [main, develop]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: cd backend/electoral-api && ./mvnw clean test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
      - run: cd frontend/app && npm install && npm run build
```

---

## Paso 2 — `deploy.yml` (Continuous Deployment)

`.github/workflows/deploy.yml`:

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Deploy to Railway
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
        run: |
          curl -L https://railway.app/install.sh | bash
          railway deploy --service electoral-api

  deploy-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Deploy to Vercel
        env:
          VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}
          VERCEL_PROJECT_ID: ${{ secrets.VERCEL_PROJECT_ID }}
          VERCEL_ORG_ID: ${{ secrets.VERCEL_ORG_ID }}
        run: |
          npm install -g vercel
          cd frontend/app
          vercel deploy --prod --token $VERCEL_TOKEN --yes
```

---

## Paso 3 — Configurar secrets

En GitHub Repo → **Settings** → **Secrets and variables** → **Actions**:

Agregá:
- `RAILWAY_TOKEN`: obtén de Railway Dashboard → Account → API Token
- `VERCEL_TOKEN`: obtén de Vercel Dashboard → Settings → Tokens
- `VERCEL_PROJECT_ID`: obtén de Vercel Project → Settings
- `VERCEL_ORG_ID`: obtén de Vercel Account → Settings

---

## Checklist

- [ ] Workflows creados en `.github/workflows/`
- [ ] Secrets configurados en GitHub
- [ ] Push a main dispara deploy automático
- [ ] PR a main corre tests antes de merge
