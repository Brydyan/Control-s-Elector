# Design — T-IN-09: Vercel Deploy

## D1 — Build command

**Decisión:** `ng build --configuration production` (estándar Angular CLI).

**Alternativa descartada — ng build --prod.** Deprecated en Angular 16+, usar `--configuration production`.

## D2 — Output directory

**Decisión:** `dist/app/browser/` (output estándar Angular 17+).

**Alternativa descartada — dist/app.** Path ha cambiado en Angular 17, especificar explícito.

## D3 — SPA routing

**Decisión:** Usar `vercel.json` (T-IN-10) para reescribir 404 → index.html.

**Alternativa descartada — _redirects file.** Vercel prefiere vercel.json.

## D4 — Environment variables

**Decisión:** SUPABASE_URL, SUPABASE_ANON_KEY en Vercel Settings (no secrets, son públicos).

**Alternativa descartada — commitear .env.** Nunca.
