# Infra Changes — Spec-Driven Development (SDD)

Cambios de infraestructura, divididos en retrospectivos (ya implementados) y pendientes (nuevos).

## Cambios Pendientes (TODO)

| Cambio | Título | Dependencias | Estado |
|--------|--------|--------------|--------|
| T-IN-05 | Configurar Proyecto Supabase | ninguna | Pending |
| T-IN-06 | Supabase Realtime | T-IN-05 | Pending |
| T-IN-07 | Deploy Spring Boot en Railway | T-IN-05 | Pending |
| T-IN-08 | application-prod.properties | T-IN-07 | Pending |
| T-IN-09 | Deploy Angular en Vercel | T-IN-07 | Pending |
| T-IN-10 | vercel.json SPA routing | T-IN-09 | Pending |
| T-IN-11 | GitHub Actions CI/CD | T-IN-07, T-IN-09 | Pending |
| T-IN-12 | Tailscale Staging | T-IN-01 (local Docker) | Pending |

**Línea base 2026-09-07:** Docker Compose staging setup, SQL schema, RLS, seed data (all completed and archived).

## Cambios Retrospectivos (COMPLETADO)

| Cambio | Título | Status |
|--------|--------|--------|
| T-IN-01 | Docker Compose staging completo | ✅ COMPLETADO |
| T-IN-02 | Schema SQL inicial | ✅ COMPLETADO |
| T-IN-03 | RLS PostgreSQL | ✅ COMPLETADO |
| T-IN-04 | Datos semilla (seed) | ✅ COMPLETADO |

Archivados en: `openspec/changes/archive/infra/`

## Dependencia Graph

```
T-IN-01 (Docker Compose local)
  └─→ T-IN-12 (Tailscale access)

T-IN-05 (Supabase proyecto)
  ├─→ T-IN-06 (Realtime WebSocket)
  ├─→ T-IN-07 (Railway backend deploy)
  │   ├─→ T-IN-08 (application-prod.properties)
  │   ├─→ T-IN-09 (Vercel frontend deploy)
  │   │   ├─→ T-IN-10 (vercel.json SPA routing)
  │   │   └─→ T-IN-11 (GitHub Actions CI/CD)
  │   └─→ T-IN-11 (GitHub Actions CI/CD)

Resumen: T-IN-05 es base → Railway → Vercel → GitHub Actions
```

## Estructura de cada cambio

```
2026-09-07-t-in-NN-descripcion-slug/
  ├── proposal.md         ← Intent, scope, criterios
  ├── design.md           ← Decisiones D1/D2/... + alts descartadas
  ├── specs/
  │   └── infra/
  │       └── spec.md     ← GIVEN/WHEN/THEN + mutaciones
  └── tasks.md            ← Fases A/B/C, checklist, gates
```

## Próximos Pasos

1. **Para cada cambio pending:**
   - Leer proposal.md (qué/por qué/scope)
   - Revisar design.md (decisiones)
   - Ejecutar tasks.md (fases A/B/C)
   - Validar spec.md (escenarios + mutaciones)

2. **Orden recomendado:**
   - T-IN-05: Supabase proyecto (base en cloud)
   - T-IN-06: Realtime (optional, pero recomendado para T-FE-11)
   - T-IN-07: Railway backend (API en prod)
   - T-IN-08: application-prod.properties (config prod)
   - T-IN-09: Vercel frontend (app en prod)
   - T-IN-10: vercel.json (SPA routing fix)
   - T-IN-11: GitHub Actions (CI/CD automation)
   - T-IN-12: Tailscale (staging access team)

3. **Al completar cada cambio:**
   - Crear apply-progress.md (qué se hizo)
   - Crear verify-report.md (qué se validó)
   - Mover a openspec/changes/archive/infra/ con archive-report.md

## Decisiones Arquitectónicas

| Cambio | D1 | D2 | D3 |
|--------|----|----|-----|
| T-IN-05 | Región us-east-1 | Backup auto | Bucket único "evidencias" |
| T-IN-06 | Events INSERT+UPDATE | Tablas acta+notificacion | Reconnect automático SDK |
| T-IN-07 | Dockerfile en repo | Railway auto-trigger main | Port 8081 |
| T-IN-08 | ddl-auto=validate | CORS twintailcs.xyz | Logging WARN |
| T-IN-09 | `ng build --configuration production` | dist/app/browser | vercel.json SPA |
| T-IN-10 | Rewrite 404 → index.html | Status 200 | Pattern `/((?!api).*)`|
| T-IN-11 | CI en PR, CD solo main | Tests paralelos | GitHub Secrets |
| T-IN-12 | Tailscale VPN | GitHub OAuth | Allow all devices |

## Trampas Conocidas

Ninguna aún. Se agregarán conforme aparezcan.
