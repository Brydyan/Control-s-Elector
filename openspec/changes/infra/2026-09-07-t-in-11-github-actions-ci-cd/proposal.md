# Proposal — T-IN-11: GitHub Actions CI/CD

## Qué se construye
Workflows GitHub Actions: automatización que corre tests en PRs y depliega en main (Railway + Vercel).

## Por qué
- Tests automáticos: evitar romper main
- Deploy automático: sin pasos manuales
- Preview en PRs: revisar cambios antes de merge

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| CI | tests backend (Maven), frontend (ng test) en PRs | coverage reports |
| CD | auto-deploy en main a Railway + Vercel | rollback automático |
| Workflows | ci.yml (PR tests), cd.yml (deploy main) | security scanning |
| Secrets | GitHub secrets para API keys | vault management |

## Criterio de aceptación

- [ ] `.github/workflows/ci.yml` creado: corre tests en PR
- [ ] `.github/workflows/cd.yml` creado: deploy en main
- [ ] Tests pasan: backend (Maven) + frontend (ng test)
- [ ] Deploy a Railway: API endpoint accesible
- [ ] Deploy a Vercel: app accesible
- [ ] Test: abrir PR, ver CI checks pasar, merge → auto-deploy

## Fuera de alcance

- Security scanning (SAST)
- Code coverage % gates
- Slack/email notifications

## Dependencias

- T-IN-07 (Railway setup)
- T-IN-09 (Vercel setup)

## Decisiones a tomar

**D1 — Trigger CD:** solo en `main` (no develop).

**D2 — Tests:** Maven + ng test (ambos deben pasar).
