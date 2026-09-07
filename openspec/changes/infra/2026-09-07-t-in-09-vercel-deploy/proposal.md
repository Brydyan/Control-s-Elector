# Proposal — T-IN-09: Deploy Angular en Vercel

## Qué se construye
Deploy frontend Angular en Vercel: plataforma global CDN que sirve app desde edge locations cercanas a usuarios, con SPA routing automático.

## Por qué
- CDN global (baja latencia mundial)
- Deploy automático desde GitHub
- SPA routing nativo (refresh en /observer/home no da 404)
- Preview URLs en PRs
- SSL/TLS automático

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Vercel | crear proyecto, conectar GitHub | custom domain (handle separado) |
| Build | `ng build` automático | cache optimization |
| Routing | SPA rewrite (index.html) | redirect rules |
| Environment | vars de frontend (SUPABASE_URL, etc.) | secrets (no necesarios) |
| Preview | PR deployments automáticos | staging env |

## Criterio de aceptación

- [ ] Cuenta Vercel creada (GitHub login)
- [ ] Proyecto conectado a GitHub repo
- [ ] Deploy en main automático
- [ ] App accesible en https://control.twintailcs.xyz
- [ ] Refresh en /observer/home funciona (no 404)
- [ ] Test: dos navegadores, cache test (assets desde CDN)

## Fuera de alcance

- Next.js (no, es puro Angular)
- Cloudflare Workers
- Edge functions

## Dependencias

- T-IN-07 (API backend en Railway)
- T-FE-01…T-FE-12 (frontend código listo)

## Decisiones a tomar

**D1 — Build command:** `ng build --configuration production`.

**D2 — Output dir:** `dist/app/browser/`.
