# Tasks: T-IN-15

Rama: `infra/t-in-15-rate-limiting`, de `develop`.
Bloqueador: T-IN-07r (backend en Railway debe estar deployado antes).

---

## A · Setup Upstash Redis

- [ ] **A.1** — Crea proyecto en Upstash (serverless Redis):
  - Database name: `control-electoral-rate-limit`
  - Region: misma que Railway (us-east)
  - Type: Redis
  - Anotar en `apply-progress.md`:
    ```
    REDIS_URL = redis://[user]:[pass]@[host]:6379
    ```

- [ ] **A.2** — Añade REDIS_URL a Railway env vars (ODD #503, update).

---

## B · Implement @RateLimited annotation

- [ ] **B.1** — Crea annotation:
  `src/main/java/.../shared/ratelimit/RateLimited.java`
  ```java
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface RateLimited {
    int limit() default 60;
    String window() default "1m";  // "1m", "1h", etc
  }
  ```

- [ ] **B.2** — Crea aspect (interceptor):
  `src/main/java/.../shared/ratelimit/RateLimitingAspect.java`
  ```java
  @Aspect
  @Component
  public class RateLimitingAspect {
    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint jp, RateLimited rateLimited) {
      String userId = getCurrentUserId();  // from SecurityContext
      String key = "ratelimit:" + userId + ":" + jp.getSignature().getName();
      
      long currentCount = redis.increment(key);
      if (currentCount == 1) {
        redis.expire(key, parseDuration(rateLimited.window()));
      }
      
      if (currentCount > rateLimited.limit()) {
        long ttl = redis.ttl(key);
        return ResponseEntity.status(429)
          .header("Retry-After", String.valueOf(ttl))
          .build();
      }
      
      return jp.proceed();
    }
  }
  ```

---

## C · Annotate dashboard endpoints

- [ ] **C.1** — En `DashboardController`:
  ```java
  @GetMapping("/resumen")
  @RateLimited(limit = 60, window = "1m")
  public ResumenActasDto resumen() { ... }
  
  @GetMapping("/resultados")
  @RateLimited(limit = 30, window = "1m")
  public ResultadoCargoDto resultados() { ... }
  
  @GetMapping("/progreso")
  @RateLimited(limit = 30, window = "1m")
  public ProgresoCantonDto progreso() { ... }
  
  @GetMapping("/novedades")
  @RateLimited(limit = 20, window = "1m")
  public List<NovedadDto> novedades() { ... }
  ```

---

## D · Tests: normal flow + mutations

- [ ] **D.1** — `RateLimitingAspectTest.java`:
  ```java
  @Test void resumenEndpointAllows60ReqPerMin() {
    for (int i = 0; i < 60; i++) {
      mvc.perform(get("/api/v1/dashboard/resumen"))
        .andExpect(status().isOk());
    }
  }
  
  @Test void resumenEndpoint61stReqFails() {
    for (int i = 0; i < 60; i++) {
      mvc.perform(get("/api/v1/dashboard/resumen"))
        .andExpect(status().isOk());
    }
    mvc.perform(get("/api/v1/dashboard/resumen"))
      .andExpect(status().is(429))
      .andExpect(header().exists("Retry-After"));
  }
  
  @Test void differentUsersHaveIndependentLimits() {
    // Request as User A: 30 times → all OK
    // Request as User B: 30 times → all OK (independent counter)
  }
  
  @Test void limitResetAfterWindow() {
    // Make 60 requests
    // Wait 61 seconds
    // Make 1 more request → OK (new window)
  }
  ```

- [ ] **D.2** — Mutation tests (each endpoint):
  ```java
  @Test @DisableRateLimitingForTest
  void resumenWithoutRateLimitingAllows100Req() {
    for (int i = 0; i < 100; i++) {
      mvc.perform(get("/api/v1/dashboard/resumen"))
        .andExpect(status().isOk());
    }
  }
  ```
  (Annotation `@DisableRateLimitingForTest` desactiva el aspect solo en tests.)

---

## E · Integration with Upstash

- [ ] **E.1** — `RedisRateLimitStore.java`:
  ```java
  @Component
  public class RedisRateLimitStore {
    private StringRedisTemplate redis;
    
    public long incrementAndGet(String key, long windowSeconds) {
      // INCR + EXPIRE si es nuevo
      long value = redis.opsForValue().increment(key);
      if (value == 1) {
        redis.expire(key, Duration.ofSeconds(windowSeconds));
      }
      return value;
    }
    
    public long getTTL(String key) {
      return redis.getExpire(key, TimeUnit.SECONDS);
    }
  }
  ```

- [ ] **E.2** — Test conexión a Upstash (integration test):
  ```java
  @SpringBootTest
  class UpstashRedisIntegrationTest {
    @Test void canConnectToUpstash() {
      redis.ping();  // Espera "PONG"
    }
  }
  ```
  (Este test corre solo si REDIS_URL está seteada.)

---

## F · Graceful error handling

- [ ] **F.1** — En `RateLimitingAspect`, fallback si Redis cae:
  ```java
  @Around("@annotation(rateLimited)")
  public Object enforceRateLimit(ProceedingJoinPoint jp, RateLimited rateLimited) {
    try {
      // ... rate limit logic
    } catch (RedisConnectionException e) {
      log.warn("Redis unavailable, returning 503");
      return ResponseEntity.status(503)
        .body(Map.of("error", "Rate limit service unavailable"));
    }
  }
  ```

---

## G · Manual test contra Railway

- [ ] **G.1** — Deploy a Railway (T-IN-07r ya debe estar live):
  ```bash
  git push origin infra/t-in-15-rate-limiting
  # Railway redeploya
  ```

- [ ] **G.2** — Test contra prod:
  ```bash
  for i in {1..65}; do
    curl https://api.controls.brydyan.me/api/v1/dashboard/resumen \
      -H "Authorization: Bearer $TOKEN" -w "%{http_code}\n"
  done
  # Esperado: 61–65 son 429, resto 200
  ```

---

## H · Mutaciones

- [ ] **H.1** — RF-RL-01: Limit enforcement.
  En `RateLimitingAspect`, cambia `if (currentCount > rateLimited.limit())` a `if (currentCount > rateLimited.limit() + 10)`.
  Mutación: test de 61 req espera que pasen 71 req. Anotar test que falla.
  Restaura.

- [ ] **H.2** — RF-RL-05: Retry-After header.
  Quita `.header("Retry-After", ...)` de la respuesta.
  Mutación: test de header falla. Restaura.

- [ ] **H.3** — RF-RL-06: Per-user limits.
  En el key del Redis, quita el userId: `key = "ratelimit:" + jp.getSignature().getName()` (sin userId).
  Mutación: test de dos usuarios compartiendo límite falla (User B queda limitado si A ya hizo 60 req).
  Restaura.

---

## Qué NO hacer

- No rate limiting en otros endpoints aún (post-MVP, T-IN-16).
- No rate limiting por IP (está por usuario, diferente enfoque).
- No admin bypass (post-MVP, T-IN-16).
- No cache de resultados de dashboard (rate limiting es solo protección).

---

## Compuertas

```bash
cd backend/electoral-api-quarkus

# ¿Tests pasan?
./mvnw -B test -Dtest=RateLimitingAspectTest

# ¿Deployment en Railway OK?
curl https://api.controls.brydyan.me/api/v1/health/backend
# Esperado: 200

# ¿Rate limit funciona?
for i in {1..65}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    https://api.controls.brydyan.me/api/v1/dashboard/resumen
done | sort | uniq -c
# Esperado: 60× 200, 5× 429
```

Si todas pasan ✓, T-IN-15 cierra.
