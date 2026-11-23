# ODD — Rate Limiting ajuste Post-Caching Strategy

**Referencia:** T-RATELIMIT-ADJ-01  
**Depende de:** ~~T-CACHE-STRAT-01 (SDD Caching Strategy)~~ — sin dependencia real (ver abajo)  
**Epic:** Infra › Escalabilidad & Performance  

> **Estado (2026-11-20, sc-521): reemplazada por
> `openspec/changes/back/2026-11-20-rate-limiting-adjustment/design.md`.**
>
> Al revisar el código se encontró que el rate limiting no funcionaba: el
> interceptor nunca se aplicaba (faltaba `@Nonbinding`), USER_LIMIT no se leía y
> no había Redis en producción. El ajuste quedó así:
>
> - **C (USER_LIMIT 1000 → 2000): descartado.** Se queda en 1000/min, ahora
>   aplicado de verdad y configurable (`APP_RATELIMIT_USER_GLOBAL_LIMIT`). Un
>   observador real usa 10-50 req/min; el 45% no tenía medición detrás.
> - **B (proyección con caché): reemplazado** por límites configurables que se
>   ajustan con medición real.
> - **D (6 endpoints): hecho**, con clave por usuario (no por IP: los recintos
>   comparten IP) y matriz de permisos declarada.
> - **E (coordinación Redis): hecho**, prefijos `rl:v1:` vs. `cache:` y TTL en
>   toda clave (`RedisRateLimitBackendIT`).
> - **F (docs): el design.md citado arriba.**
> - El prerequisito de caché no aplica: el rate limiting no depende de él.
>
> El contenido original queda abajo como referencia histórica.

---

## Propósito

Ajustar parámetros de rate limiting después de implementar caching strategy. Sin caching, los límites bloquean flujos legítimos. Con caching (45% reducción de requests), necesitamos:

1. Aumentar límite user de 1000 → 2000 req/min
2. Extender `@RateLimited` a endpoints faltantes
3. Verificar coordinación Redis (caching + rate limiting sin conflicto)

---

## Scope

### A. Revisar Límites Actuales

- [ ] Leer `RateLimitingService.java` (límites globales + por usuario)
- [ ] Leer `@RateLimited` en 3 recursos:
  - `AuthResource.java` (login: 5/5min)
  - `DpoResource.java` (contact-dpo: 1/30s)
  - `UserLegalResource.java` (export-data: 1/24h)
- [ ] Documentar límites actuales en tabla

### B. Proyectar Requests con Caching

- [ ] Calcular req/min SIN caching: 1410 usuarios × 2-3 req = 3,300+/min
- [ ] Calcular req/min CON caching: mismo flujo × 45% reduction = 1,815/min
- [ ] Comparar vs límite user actual: 1,000/min → **INSUFICIENTE**

### C. Ajustar Parámetro: User Limit

- [ ] Cambiar en `RateLimitingService.java` línea 28:
  ```java
  private static final long USER_LIMIT = 2000;  // was 1000
  ```
- [ ] Justificación: Caching reduce requests 45%, necesitamos 2000 para 1410 usuarios
- [ ] Compilar sin errores

### D. Extender @RateLimited a Más Endpoints

**Endpoints actuales CON @RateLimited:**
- POST /auth/login → 5/5min
- GET /legal/export-data → 1/24h
- POST /legal/contact-dpo → 1/30s

**Endpoints faltantes SIN @RateLimited (revisar + agregar):**
- [ ] GET /observer/jrvs → Agregar: `@RateLimited(limit = 100, windowSeconds = 60, identifier = "user")`
- [ ] POST /observer/actas → Agregar: `@RateLimited(limit = 50, windowSeconds = 60, identifier = "user")`
- [ ] GET /configuracion/cargos → Agregar: `@RateLimited(limit = 200, windowSeconds = 60, identifier = "ip")`
- [ ] GET /configuracion/partidos → Agregar: `@RateLimited(limit = 200, windowSeconds = 60, identifier = "ip")`
- [ ] GET /configuracion/candidatos → Agregar: `@RateLimited(limit = 200, windowSeconds = 60, identifier = "ip")`
- [ ] GET /admin/usuarios → Agregar: `@RateLimited(limit = 50, windowSeconds = 60, identifier = "user")`

**Compilar sin errores**

### E. Testing: Coordinación Redis

- [ ] Test: Cache + rate limit coexisten sin conflicto
  ```java
  @Test
  void testRateLimitAndCacheCoexist() {
    // 1. Call endpoint multiple times (hits cache)
    service.getCandidatos(); // 1st: Redis miss (cache), rate limit counter+1
    service.getCandidatos(); // 2nd: Redis hit (cache), rate limit counter+1
    
    // 2. Verify: cache keys ≠ rate limit keys
    // cache: "candidatos:*"
    // rate limit: "rate_limit:user:123:getCandidatos"
    
    // 3. Redis memory: both present, no overflow
  }
  ```
- [ ] Load test 50 usuarios: verificar hit ratio > 90% Y rate limit permits > 95%
- [ ] Regresión: endpoints aún funcionales bajo carga

### F. Documentación

- [ ] Crear `RATE_LIMITING_ADJUSTMENT.md`:
  - Por qué cambió (caching reduce requests 45%)
  - Nuevos límites por endpoint
  - Coordinación Redis: prefijos separados
  - Monitoreo: alertas si rate limit blocks > 5%

---

## Implementation Checklist

### Pre-Builder

- [ ] Caching SDD implementado y mergeado (prerequisito)
- [ ] Load test Caching SDD para medir req/min real
- [ ] Comparar con proyección (esperado: 1,815/min)

### Builder Phase

```
1. A → Revisar límites (5 min)
2. B → Proyectar requests (10 min)
3. C → Ajustar USER_LIMIT 1000→2000 (1 min + compile)
4. D → Extender @RateLimited a 6 endpoints (15 min + compile)
5. E → Tests (30 min)
6. F → Documentación (15 min)

TOTAL: ~1-1.5 horas
```

### Pre-Deployment

- [ ] Todos tests pasan (unit + integration + load)
- [ ] Rate limit + cache coexisten en Redis sin conflicto
- [ ] Monitoreo Redis: memory < 200MB (100MB cache + 50MB rate limit + buffer)
- [ ] Endpoints nuevos con @RateLimited funcionan correctamente

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Rate limit AÚN insuficiente (2000 no es bastante) | Load test post-caching real data, ajustar si necesario |
| Redis memory exhaustion (caching + rate limit) | Monitoreo separado, alertas en 500MB |
| Extender @RateLimited causa regresión | Tests de integración para cada endpoint nuevo |
| Coordinación Redis keys: collision | Prefijos diferentes (cargos: vs rate_limit:), no hay riesgo |

---

## Hoja de Ruta (Fases)

**Fase 1: Caching SDD** (Semana 1)
- Implementar frontend cache (Observer partidos + Dashboard)
- Implementar backend Redis + @Cacheable

**Fase 2: ODD Rate Limiting Ajuste** (Semana 2)
- Ajustar límites
- Extender @RateLimited
- Verificar coordinación

**Fase 2.5 (Optional): Sliding Window**
- Cambiar RateLimitingService de fixed → sliding window
- Más justo pero requiere refactor

**Fase 3 (Future): Event-driven**
- Auto-scale límites si detecta picos
- Integración con Railway para backend replicas

---

## Notas

- Esta ODD está bloqueada por Caching SDD (prerequisito)
- El límite 2000 es proyección; ajustar post-load-test real
- Coordinación Redis: prefijos separados = sin conflicto
- Monitorear post-deploy por 1 semana

---

## Sign-Off

**Builder:** _______________  
**Date:** _______________  

**QA:** _______________  
**Date:** _______________  

**Ready for Merge:** [ ] Yes [ ] No

**Merge Commit:** TBD
