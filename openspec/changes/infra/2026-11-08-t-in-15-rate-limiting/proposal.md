# T-IN-15: Rate Limiting Dashboard Endpoints

## El problema

MVP endpoints de dashboard (RF-DASH-01–07, T-BE-13) aún no tienen rate limiting.
Si veedores/coordinadores pollea dashboard cada 1 segundo durante escrutinio,
pueden saturar la API sin intención maliciosa.

## Alcance

✅ @RateLimited annotations en endpoints de dashboard
  - resumen: 60 req/min por usuario
  - resultados: 30 req/min por usuario
  - progreso: 30 req/min por usuario
  - novedades: 20 req/min por usuario

✅ Tests para 429 TOO_MANY_REQUESTS
✅ Mutation testing (disable rate limit → test fails)
✅ Redis integration (almacenar contadores de rate limit)
✅ Graceful backoff messages

❌ Rate limiting en otros endpoints (post-MVP, T-IN-16)
❌ Rate limiting por IP (post-MVP, diferente de por usuario)
❌ Admin override (desactivar rate limit) (post-MVP, requiere log audit)

## Razón de criticidad

**ALTO**: No es bloqueador como 1–4, pero protege el MVP de uso excesivo.
Viene post-deployment de Railway, cuando el MVP está live.
