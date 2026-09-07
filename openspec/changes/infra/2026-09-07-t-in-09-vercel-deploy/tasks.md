# Tasks — T-IN-09: Deploy Angular en Vercel

## Fase A: Crear cuenta Vercel + proyecto

- [ ] **A.1** — Ir a https://vercel.com, login con GitHub
- [ ] **A.2** — Crear nuevo proyecto (importar repo `Control-s-Elector`)
- [ ] **A.3** — Vercel auto-detect Angular project
- [ ] **A.4** — Configurar root directory: `frontend/app`

**Compuerta A:** Vercel muestra "Connected to GitHub repo".

## Fase B: Build settings

- [ ] **B.1** — En Vercel project → Settings → Build & Development
  - Build command: `ng build --configuration production`
  - Output directory: `dist/app/browser`

- [ ] **B.2** — En Environment variables, agregar (public):
  - `NG_APP_SUPABASE_URL` = URL Supabase (T-IN-05)
  - `NG_APP_SUPABASE_ANON_KEY` = anon key Supabase
  - `NG_APP_API_URL` = `https://api.twintailcs.xyz` (o Railway domain)

**Compuerta B:** Build settings configurados, environment vars visibles.

## Fase C: Deploy manual

- [ ] **C.1** — En Vercel, ir a Deployments → Deploy
- [ ] **C.2** — Esperar a que build complete (~3 min)
- [ ] **C.3** — Ver logs: debe pasar `ng build` sin errores
- [ ] **C.4** — Vercel asigna URL temporal: `https://control-[random].vercel.app`

**Compuerta C:** Build exitoso, deploy completo.

## Fase D: Verificar SPA routing

- [ ] **D.1** — Abrir app en Vercel URL
- [ ] **D.2** — Navegar a /observer/home (si existe)
- [ ] **D.3** — Refrescar navegador (F5)
- [ ] **D.4** — Verificar que no da 404 (debe cargar componente)

**Compuerta D:** Refresh en rutas funciona.

## Fase E: Configurar domain twintailcs.xyz

- [ ] **E.1** — En Vercel Settings → Domains
- [ ] **E.2** — Agregar `control.twintailcs.xyz`
- [ ] **E.3** — Vercel da instrucciones DNS (CNAME)
- [ ] **E.4** — En registrar DNS (ej: Namecheap, GoDaddy), apuntar domain a Vercel
- [ ] **E.5** — Esperar DNS propagación (~30 min)
- [ ] **E.6** — Probar https://control.twintailcs.xyz

**Compuerta E:** Domain funciona, SSL automático (Vercel maneja).

## Fase F: Automático on push

- [ ] **F.1** — Hacer pequeño commit, push a main/develop
- [ ] **F.2** — Vercel auto-detecta, inicia nuevo build
- [ ] **F.3** — Ver Deployments: nueva entrada en progreso

**Compuerta F:** Push dispara deploy automático.

## Qué NO hacer

- **No usar Vercel free tier para prod** — usar plan Pro
- **No commitear .env** — usar Vercel environment vars
- **No compilar localmente antes** — Vercel compila en pipeline
- **No configurar cache muy agresivo** — users pueden ver versión vieja

## Compuertas

| Fase | Verifica | Expected |
|------|----------|----------|
| A | Vercel dashboard | Project connected to GitHub |
| B | Build settings | ng build + dist/app/browser configured |
| C | Build logs | "Build successful" sin errores |
| D | Refresh /observer/home | No 404, componente carga |
| E | Navegar https://control.twintailcs.xyz | App visible, SSL válido |
| F | Push + Deployments | Nueva entrada 1-2 min después |

## Verificación por Mutación

- **V.1** — Cambiar output dir a dist/ → build falla (no encuentra archivos)
- **V.2** — Remover vercel.json → refresh en ruta da 404 (T-IN-10 necesario)
