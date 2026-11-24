# Cache Hit Ratio — T-CACHE-STRAT-01 F.5 — LÍNEA BASE (sin caché)

Backend con `QUARKUS_CACHE_ENABLED=false` (propiedad **build-time**: hay que
reconstruir la imagen para apagarla). 100 VUs, ramp up de 10 s, 10 lecturas por
VU.

## Resultado

| Métrica | Valor | Nota |
|---|---|---|
| Hit ratio | **0.00 %** | fallido **por diseño**: es la línea base |
| p95 | **3.70 ms** | |
| Peticiones fallidas | **0** | |
| Iteraciones | 9.356 | |
| Lecturas de configuración | ~37.400 | |
| **Consultas de configuración a la base** | **374.240** | una por cada lectura |

## Conclusión

Sin caché, **cada lectura de configuración es una consulta a la base**: 374.240
consultas para ~37.400 lecturas (4 endpoints × 10 lecturas × 9.356 iteraciones,
más las consultas de arranque).

Normalizado por lectura (para que la comparación valga aunque las corridas
tengan distinto número de iteraciones): **10,0 consultas a la base por cada
lectura de configuración**.

Con la caché activada, la misma carga genera **4 consultas** — el llenado
inicial, y ni una más. El ahorro es del **99.999 %**, tres órdenes de magnitud
por encima del objetivo del SDD (−90 %).

> El TTL no aparece en esta línea base: con la caché apagada no hay entradas
> que caduquen. El TTL de 2 min sí tiene coste en la corrida **con** caché
> (29 consultas extra en 4,5 minutos), y está medido en
> `cache-hit-ratio-summary-con-cache.md`.

Comparativa completa en `cache-hit-ratio-summary-con-cache.md`.
