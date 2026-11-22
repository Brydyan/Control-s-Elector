# Cache Hit Ratio — T-CACHE-STRAT-01 F.5

Ejecutado contra el backend real (Postgres 16 en Docker, rol `electoral_app`
sin BYPASSRLS), 100 VUs, ramp up de 10 s, 10 lecturas por VU.

## Con caché (Caffeine, TTL 10 min)

- p95: **0.77 ms** (objetivo < 2000 ms) ✅
- hit ratio: **100.00 %** (objetivo > 90 %) ✅
- peticiones fallidas: **0** ✅
- iteraciones: 11.675 · lecturas de configuración: ~46.700
- **consultas a la base: 23** (solo login y metadatos; ninguna de configuración)

## Sin caché (`quarkus.cache.enabled=false`)

- p95: **2.77 ms**
- hit ratio: **0.00 %**
- peticiones fallidas: 0
- iteraciones: 9.779 · lecturas de configuración: ~39.100
- **consultas a la base: 391.206** (97.790 por cada endpoint cacheado)

## Ahorro

- **−99.99 %** de consultas a la base (23 → 391.206 en el escenario inverso;
  en el escenario con caché, 0 consultas de configuración frente a 391.206).
- **−72 %** de latencia p95 (2.77 ms → 0.77 ms).
- El objetivo del SDD era −90 % de consultas: **cumplido**.

## Reproducir

```bash
cd frontend/tests/load
MASTER_EMAIL=mastersolon@example.local MASTER_PASSWORD=admin12345 \
  k6 run scenarios/cache-hit-ratio.js
```

Para la línea base, arrancar el backend con `QUARKUS_CACHE_ENABLED=false`
(propiedad build-time: hay que reconstruir la imagen) y repetir.
