# T-IN-11 — CI/CD (GitHub Actions)

**Estado:** PARCIAL — CI y workflows de build existen; falta el job de E2E
**Dificultad:** Intermedio
**Depende de:** T-IN-07 (backend en Railway), T-IN-09 (frontend en Cloudflare Pages)

---

## ¿Qué vas a construir?

Automatización: **tests en cada PR** y **despliegue en `main`/staging**.

> La versión anterior de esta tarea describía un `ci.yml` y un `deploy.yml` mínimos, con deploy a
> Railway y Vercel por token. La realidad del repo es otra: los workflows ya existen y son más
> ricos. Este documento describe **lo que hay** y lo que falta.

---

## Workflows que ya existen

| Workflow | Qué hace |
|---|---|
| `.github/workflows/ci.yml` | Checks requeridos por el ruleset de `develop`: tests backend + cobertura, `spotless:check`, ESLint, format check, build de producción y tests unitarios del frontend |
| `.github/workflows/ci-native.yml` | Build de la imagen nativa del backend (tarda ~19 min). Corre solo cuando cambia el módulo Quarkus |
| `.github/workflows/load-test-baseline.yml` | Corrida del load test de referencia |
| `.github/workflows/deploy.yml` | Build de imágenes Docker + rollout a **staging k3s** (`twintailserver`). **No es el despliegue de producción** |

### Cómo llega a producción

Producción **no se despliega desde GitHub Actions**:

- **Backend**: Railway tiene su propia integración con GitHub y construye desde `railway.json` →
  `backend/electoral-api-quarkus/Dockerfile.native`.
- **Frontend**: Cloudflare Pages tiene su propia integración con GitHub y construye
  `frontend/app/www` (ver T-IN-09).

Ventaja: no hay tokens de despliegue en GitHub Secrets, y el secreto no puede filtrarse desde
un workflow. Lo que sí vive en GitHub Secrets son credenciales de servicios (Docker Hub, Neon,
Cloudflare) para los jobs de CI.

---

## Lo que falta

- [ ] **Job de Playwright** para los E2E de caché (`e2e/cache-api-real.spec.ts` y
      `e2e/cache-datos-estaticos.spec.ts`). Hoy corren solo en local, contra la API real.
      El job necesita el backend arriba (mismo esquema que producción, sin mocks en la ruta de
      registro de acta) y las credenciales de prueba como secrets del repositorio.
- [ ] **Job de WebKit**: en esta máquina no es ejecutable; en CI sí debería estar.

---

## Checklist

- [x] `ci.yml` con los checks requeridos
- [x] `ci-native.yml` para la imagen nativa
- [x] `load-test-baseline.yml`
- [x] `deploy.yml` para staging
- [ ] Job de Playwright (E2E) en CI
- [ ] Job de Playwright en WebKit
