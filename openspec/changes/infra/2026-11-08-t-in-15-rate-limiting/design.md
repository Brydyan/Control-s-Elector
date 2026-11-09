# Design: T-IN-15

## D1 — Rate limiting storage: Redis vs in-memory

**Decisión**: Redis (Upstash, serverless). Multi-instance safe en Railway.

**Alternativa descartada**: In-memory (Spring Cache).
- Si Railway escala a 2+ instancias, cada una tiene su contador → bypass.
- Upstash Redis tiene tier free para MVP (~100k commands/día).

## D2 — Límites por usuario, no por IP

**Decisión**: Rate limit por `CurrentUser.id()` (UUID publicId).
Dashboard es de usuarios autenticados; un IP puede tener varios usuarios.

**Alternativa descartada**: Rate limit por IP.
- Corporativos/escuelas con NAT verían límite compartido.
- RLS ya filtra por usuario; coherente con ello.

## D3 — Custom annotation vs interceptor

**Decisión**: `@RateLimited(limit = 60, window = "1m")` en métodos.
Simple, declarativa, explícita por endpoint.

**Alternativa descartada**: Interceptor global.
- Interpretador global es implícito; fácil olvidar.
- @RateLimited es visible en el código.

## D4 — HTTP 429 vs retry-after

**Decisión**: HTTP 429 Too Many Requests + `Retry-After: 60` header.

**Regla**: cliente debe esperar el `Retry-After` (en segundos) antes de reintentar.

## D5 — Test mutation

**Decisión**: Test normal y test con `@RateLimitingDisabled` (annotation test-only).
Mutación: quita `@RateLimitingDisabled` del test, espera 429.

**Regla**: mutar una vez por endpoint, anotar nombre del test.
