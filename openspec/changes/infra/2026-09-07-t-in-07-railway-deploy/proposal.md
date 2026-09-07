# Proposal — T-IN-07: Deploy Spring Boot en Railway

## Qué se construye
Desplegar backend Spring Boot en Railway: plataforma que ejecuta app automáticamente desde GitHub con CI/CD incluido.

## Por qué
- Automatización: push a main → deploy automático
- No gestionar servidores (Platform as a Service)
- Integración GitHub native
- SSL/TLS automático

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Railway | crear proyecto, conectar GitHub | custom domain |
| Dockerfile | usar Dockerfile existente o generar | build optimización |
| Environment | configurar vars (SUPABASE_URL, etc.) | secrets rotation |
| CI/CD | push main → build → deploy | PR staging env |
| Healthcheck | endpoint /actuator/health | auto-rollback |

## Criterio de aceptación

- [ ] Cuenta Railway creada (GitHub login)
- [ ] Proyecto conectado a repo GitHub
- [ ] Deploy en main automático
- [ ] API accesible en https://api.twintailcs.xyz (o subdomain Railway)
- [ ] Logs visibles en Railway dashboard
- [ ] Test: POST /api/auth/login → respuesta 401 (not authed) o success

## Fuera de alcance

- Database en Railway (usamos Supabase)
- Redis cache
- Autoscaling
- CDN caching

## Dependencias

- T-IN-05 (Supabase BD ready con creds)

## Decisiones a tomar

**D1 — Dockerfile existente o generar:** Si existe en repo, usar. Si no, generar.

**D2 — Domain:** api.twintailcs.xyz (ya apunta a Railway).
