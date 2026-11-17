# Spec: Rate Limiting T-IN-15

## RF-RL-01: @RateLimited annotation en resumen

GIVEN endpoint `GET /api/v1/dashboard/resumen`
WHEN usuario hace 61 requests en 1 minuto
THEN las primeras 60 responden 200, la 61ª responde 429 Too Many Requests

## RF-RL-02: @RateLimited annotation en resultados

GIVEN endpoint `GET /api/v1/dashboard/resultados`
WHEN usuario hace 31 requests en 1 minuto
THEN las primeras 30 responden 200, la 31ª responde 429

## RF-RL-03: @RateLimited annotation en progreso

GIVEN endpoint `GET /api/v1/dashboard/progreso`
WHEN usuario hace 31 requests en 1 minuto
THEN responde 429 a partir de la 31ª

## RF-RL-04: @RateLimited annotation en novedades

GIVEN endpoint `GET /api/v1/dashboard/novedades`
WHEN usuario hace 21 requests en 1 minuto
THEN responde 429 a partir de la 21ª

## RF-RL-05: Retry-After header presente

GIVEN cliente recibe 429
WHEN lee header `Retry-After`
THEN contiene número en segundos (ej: 45) que indica cuándo reintentar

## RF-RL-06: Rate limit POR USUARIO, no global

GIVEN dos usuarios A y B
WHEN A hace 30 requests a /resumen (bajo límite de 60)
AND B hace 30 requests a /resumen
THEN ambos siguen dentro del límite (no comparten contador)

## RF-RL-07: Window reseteado cada minuto

GIVEN usuario hizo 30 requests en T0–T0+30s
WHEN llega T0+60s (nueva ventana)
THEN contador resetea, usuario puede hacer 60 más en T0+60s–T0+120s

## RF-RL-08: Admin exempt (futuro)

GIVEN usuario con rol MASTER
WHEN hace 100 requests en 1 minuto (arriba del límite)
THEN responde 200 (admin bypass)
[SKIP para MVP, TODO: T-IN-16]

## RNF-RL-01: Redis latency negligible

GIVEN request a /resumen
WHEN comprueba rate limit en Redis
THEN add latency < 5ms (Upstash típico ~2–3ms)

## RNF-RL-02: Graceful degradation si Redis cae

GIVEN Redis desconectado
WHEN cliente hace request
THEN responde 503 (no 200, no cachea sin límite)
O implementa fallback: rate limit in-memory como degradación
