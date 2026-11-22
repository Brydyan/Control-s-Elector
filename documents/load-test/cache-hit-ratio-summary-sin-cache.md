# Cache Hit Ratio — T-CACHE-STRAT-01 F.5 — LÍNEA BASE (sin caché)

Backend con `QUARKUS_CACHE_ENABLED=false` (propiedad build-time: hay que
reconstruir la imagen). 100 VUs, ramp up de 10 s, 10 lecturas por VU.

- p95: **2.77 ms**
- hit ratio: **0.00 %** (objetivo > 90 %) — fallido **por diseño**: es la línea base
- peticiones fallidas: **0**
- iteraciones: 9.779
- lecturas de configuración: ~39.100
- **consultas a la base: 391.206** (97.790 por cada endpoint cacheado:
  partidos, cargos, geografía y candidatos)

La comparación con la corrida con caché está en
`cache-hit-ratio-summary-con-cache.md`: 23 consultas frente a 391.206, y un
p95 de 0.77 ms frente a 2.77 ms.
