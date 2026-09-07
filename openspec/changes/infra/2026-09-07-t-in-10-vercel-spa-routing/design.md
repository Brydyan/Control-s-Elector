# Design — T-IN-10: vercel.json SPA

## D1 — Status code: 200 vs 307

**Decisión:** 200 (rewrite silencioso).

**Alternativa descartada — 307 (redirect).** Redirect cambia URL en navegador (usuario ve cambio), 200 es transparente.

## D2 — Patrón de rewrite

**Decisión:** `"destination": "/index.html"` en rule catch-all `"source": "/((?!api|_next).*)"`.

**Alternativa descartada — `/**` simple.** Pattern más específico evita conflictos con /api y assets.

## D3 — Applicar a todas rutas

**Decisión:** Sí, `/api` excluded (backend no pasa por Vercel, es Railway).

**Alternativa descartada — solo /app/**.** Mejor si routing es flexible.
