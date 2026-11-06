# Proposal — T-SECURITY-01: Rate Limiting Infrastructure

**Date:** 2026-11-06  
**Priority:** CRITICAL  
**Estimación:** 5-6 puntos → 1-2 sprints

---

## Qué se construye

Sistema de rate limiting de múltiples capas que protege contra brute-force, scraping y DDoS:
- **Global IP-based:** máximo 100 peticiones/minuto por dirección IP
- **Per-user authenticated:** máximo 1000 peticiones/minuto por usuario autenticado
- **Per-endpoint custom:** reglas específicas más restrictivas (login: 5 intentos/5min, export: 10 req/min)
- **Backend:** implementado en Quarkus con Bucket4j (rate limiting library estándar Java)
- **Frontend:** validación local de throttling visual para mejor UX
- **Persistencia:** Redis-backed para distribuido en múltiples instancias backend
- **Responses:** HTTP 429 Too Many Requests con headers `Retry-After` y `X-RateLimit-*`

## Por qué es necesario

**Hoy sin rate limiting:**
- ❌ Login vulnerable a brute-force: atacante prueba 1000 passwords/seg sin fricción
- ❌ Export endpoints (`/api/data/export`) vulnerable a scraping masivo
- ❌ DDoS simple: 1 cliente malintencionado satura el backend sin límite
- ❌ Recursos electorales críticos (actas, resultados) expuestos a agotamiento

**Con esto:**
- ✅ Login limitado a 5 intentos/5 minutos por IP, protegido
- ✅ Export limitado a 10 req/minuto por usuario, previene scraping
- ✅ DDoS absorbido a nivel de aplicación: cliente ruidoso aislado sin afectar otros
- ✅ Compliance: PCI-DSS, OWASP exigen rate limiting en endpoints críticos

## Problema específico

1. **Zero implementación actual:** No existe ningún middleware, filtro o decorador de rate limiting en `JwtAuthFilter`, `AuthController`, `ExportController`, ni en ningún endpoint.
2. **Ataques prácticos:** Un usuario malintencionado o bot pueden ejecutar fuerza bruta contra login, o descargar toda la base de datos de actas mediante requests en loop sin interrupción.
3. **Escalabilidad rota:** Con solo 2-3 clientes atacando simultáneamente, el backend colapsa; no hay amortiguación por prioridades ni aislamiento de usuario malicioso.

## Solución alto nivel

```
[Request Ingress]
    ↓
[IP-based Filter: 100 req/min] ← Redis slot counter
    ↓
[Auth Filter: extrae userId si está autenticado]
    ↓
[Per-User Filter: 1000 req/min] ← Redis slot counter
    ↓
[Endpoint-specific Filter: login/export/etc] ← decorador @RateLimit(requests=5, window=5m)
    ↓
[Permitir o devolver 429]
```

**Backend stack:**
- **Quarkus:** Use `@ApplicationScoped` bean con `Bucket4j` library (Java 11+, production-grade)
- **Redis:** Backing store para contadores distribuidos (keys: `rl:ip:{ip}`, `rl:user:{userId}`, `rl:endpoint:{path}:{userId}`)
- **JwtAuthFilter:** Intercepta tras parsing JWT, agrega `userId` al contexto
- **Decorador:** `@RateLimit(requests: Int, windowSec: Int, identifier: "ip" | "user" | "combined")` en métodos de controlador

**Frontend:**
- Angular guard en rutas sensibles (login form) que throttle el botón Submit: disabled durante 2s tras fallo
- Mostrar UI "Too many attempts, retry in Xs" después de 429 response

## Alcance

✅ DENTRO:
- Bucket4j integration en Quarkus backend
- Redis-backed distributed counters
- Global IP-based rate limiting (100 req/min)
- Per-user authenticated rate limiting (1000 req/min)
- Per-endpoint custom rules: login (5 att/5min), export (10 req/min), data-access (50 req/min)
- HTTP 429 responses con headers `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`
- JwtAuthFilter hook para inyectar userId
- Tests: unit tests para lógica Bucket4j, integration tests con Redis test container
- Configuration: `application.properties` con rates configurables por entorno
- Frontend throttling visual (button disabled feedback)
- Documentation: cuáles endpoints tienen límites y por qué

❌ FUERA:
- WAF/CDN rate limiting (Cloudflare) → futuro / infraestructura
- Adaptive rate limiting (aumentar/disminuir dinámicamente) → futuro
- GraphQL rate limiting (por query complexity) → futuro, proyecto es REST
- Custom webhook alertas cuando se alcanza limite → futuro (logs suficientes)

---

## Beneficios

| Aspecto | Valor |
|---|---|
| **Seguridad** | Brute-force attacks -> 50 tries/5min máximo. Scraping -> bloqueado en 10 requests. |
| **Compliance** | PCI-DSS 6.5.10, OWASP A7:2021 (brute force) requerimientos satisfechos. |
| **Ops** | Visibilidad en Redis: 1 comando ver qué IPs/usuarios atacan. |
| **UX** | Frontend feedback claro: "Demasiados intentos, reintentar en 3s". |
| **Costo** | Redis counter: <1ms latencia, <100 bytes por entrada. Insignificante. |
| **Escalabilidad** | 100 req/s global bien absorbido; con rate limiting, 10 bots aislados = cero impacto en usuarios legítimos. |

---

## Criterios de aceptación

- [ ] Bucket4j bean `@ApplicationScoped` configurable vía `application.properties`
- [ ] Endpoints: `POST /api/auth/login` limitado a 5 intentos / 5 minutos por IP
- [ ] Endpoints: `GET /api/data/export` limitado a 10 requests / minuto por usuario autenticado
- [ ] Endpoints: `POST /api/actas/registrar` limitado a 50 requests / minuto por usuario (para sync offline)
- [ ] Global IP-based: 100 requests / minuto, respuesta 429 con header `Retry-After: 60`
- [ ] Per-user authenticated: 1000 requests / minuto, respuesta 429 con header `X-RateLimit-Remaining`
- [ ] Redis integration: contadores distribuidos funcionan correctamente en 2+ instancias backend (test con docker-compose)
- [ ] Unit tests: verifican que Bucket4j devuelve true/false correctamente
- [ ] Integration tests: con Redis test container, verifican 429 responses y counter decrement
- [ ] Frontend: botón Submit en login disabled durante 3s si falla (UX feedback)
- [ ] `application.properties` en dev/staging/prod define rates: dev=lenient (1000/min), prod=strict (100/min)
- [ ] Documentation: README.md en task explica qué endpoints tienen límites, cómo configurar, cómo monitorear en Redis

---

## Riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| **Redis no disponible** | Media (fallo puntual) | Alto (cae todo rate limiting) | Fallback: in-memory Caffeine con TTL 1min; log WARN pero permitir request |
| **False positives:** cliente legítimo bloqueado | Baja (si bien calibrado) | Medio (frustración user) | Monitoring en producción; alertas si >10% 429s; umbral tunable vía `application.properties` |
| **Overhead latencia:** 1-2ms por request | Baja (Redis es rápido) | Bajo | Aceptable; verificar en load test que p99 <50ms total |
| **Distributed cache desync** | Muy baja (Redis ACID) | Alto (rate limit no eficaz) | Redis single master; si fallover, pérdida temp 1-2min counters (aceptable) |
| **Configuración mal calibrada:** rates muy bajos | Media (al inicio) | Medio | Staging env; pruebas manuales; monitorear métricas 429s first week en prod |

---

**Status:** Ready for design  
**Last Updated:** 2026-11-06
