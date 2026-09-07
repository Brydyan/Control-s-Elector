# Proposal — T-IN-05: Configurar Proyecto Supabase

## Qué se construye
Crear proyecto Supabase en la nube. BD PostgreSQL + Auth + Object Storage + Realtime alojado.

## Por qué
- BD administrada (backups, monitoreo, escalado automático)
- Object Storage para fotos electorales
- Realtime WebSocket nativo (dashboard en vivo)
- RLS integrado, SSL/TLS por defecto
- Producción ready

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Proyecto Supabase | crear cuenta, proyecto, BD PostgreSQL | custom SSL cert |
| Object Storage | crear bucket "evidencias" | CDN caching |
| API keys | generar anon + service role keys | rotation policy |
| RLS | habilitar en BD | policies específicas (T-IN-03) |
| Realtime | habilitar WebSocket | custom topics |

## Criterio de aceptación

- [ ] Cuenta Supabase creada
- [ ] Proyecto "Control-Electoral-Prod" creado en región us-east-1
- [ ] BD PostgreSQL 15+ accesible
- [ ] Object Storage bucket "evidencias" creado
- [ ] API keys (anon + service_role) generadas y documentadas
- [ ] .env.production.local contiene SUPABASE_URL y SUPABASE_ANON_KEY
- [ ] Test: conexión desde local a Supabase (psql o SDK)

## Fuera de alcance

- Configurar Realtime (T-IN-06)
- Configurar RLS policies (ya en T-IN-03, solo habilitar aquí)
- Replicación geográfica
- Rate limiting

## Dependencias

- Ninguna (paralelo a T-IN-01…T-IN-04)

## Decisiones a tomar

**D1 — Región Supabase:** us-east-1 (cercano a AWS backend en ec2).

**D2 — Backup:** activar automático (por defecto está activado).
