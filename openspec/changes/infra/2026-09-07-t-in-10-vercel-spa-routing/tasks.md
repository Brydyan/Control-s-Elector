# Tasks — T-IN-10: vercel.json SPA Routing

## Fase A: Crear vercel.json

- [ ] **A.1** — Crear archivo `frontend/app/vercel.json`:
  ```json
  {
    "rewrites": [
      {
        "source": "/((?!api|_next).*)",
        "destination": "/index.html"
      }
    ]
  }
  ```

- [ ] **A.2** — Commit y push a GitHub

**Compuerta A:** Archivo en repo, sintaxis JSON válida.

## Fase B: Redeploy en Vercel

- [ ] **B.1** — Vercel detecta push, auto-redeploy
- [ ] **B.2** — Esperar build completo

**Compuerta B:** Deploy exitoso.

## Fase C: Test SPA routing

- [ ] **C.1** — Abrir app en https://control.twintailcs.xyz (o Vercel URL)
- [ ] **C.2** — Navegar a /observer/home (o cualquier ruta)
- [ ] **C.3** — Refrescar navegador (F5)
- [ ] **C.4** — Verificar:
  - No hay 404
  - URL sigue siendo /observer/home
  - Componente carga correctamente

- [ ] **C.5** — Test /api:
  ```bash
  curl -v https://control.twintailcs.xyz/api/auth/login
  ```
  Debe fallar con 401/400 (no 404), no es rewriteada.

**Compuerta C:** Refresh funciona, /api es transparente.

## Qué NO hacer

- **No usar `/**` pattern** — excluye /api, assets, etc.
- **No usar 301 redirect** — transparencia es mejor
- **No olvidar commit** — vercel.json debe estar en repo

## Compuertas

| Fase | Verifica | Expected |
|------|----------|----------|
| A | Archivo existe | vercel.json con rewrites válido |
| B | Vercel dashboard | Deploy exitoso |
| C | Refresh + URL test | No 404, /api funciona |

## Verificación por Mutación

- **V.1** — Comentar rule rewrite → refresh debe dar 404 → restaurar
- **V.2** — Cambiar destination a /404.html → refresh retorna 404 → restaurar
