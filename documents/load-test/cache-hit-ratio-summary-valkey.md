# Cache Hit Ratio — T-CACHE-STRAT-01 F.5 — CON CACHÉ **VALKEY** (Phase 2)

Backend real (Postgres 16 en Docker, rol `electoral_app` **sin BYPASSRLS**)
con la cache distribuida de Phase 2 sobre **Valkey** (`valkey/valkey:8-alpine`),
100 VUs, ramp up de 10 s, 10 lecturas por VU, **TTL de 2 min**
(`CACHE_TTL`, decisión D2).

Es la corrida que cierra F.1 del ODD de Phase 2: el mismo script, el mismo
backend, pero ahora la cache vive en Valkey y no en el heap de la JVM.

## Resultado

| Métrica | Valor | Objetivo | Estado |
|---|---|---|---|
| Hit ratio | **100.00 %** | > 90 % | ✅ |
| p95 | **1.07 ms** | < 2 s | ✅ |
| Peticiones fallidas | **0** | 0 | ✅ |
| Iteraciones | 11.429 | — | — |
| Lecturas de configuración | 457.160 | — | — |
| **Consultas de configuración a la base** | **4** | — | — |

## La métrica que importa

**Consultas de configuración a la base: 4** (una por endpoint cacheado:
partidos, cargos, geografía y candidatos) para 457.160 lecturas. Son el
llenado inicial; a partir de ahí, la base no vuelve a tocarse.

Los contadores del propio backend lo confirman:

```
cache_hits_total{cache="geografia",replica="local"} 114289
cache_hits_total{cache="partidos",replica="local"}  114289
cache_hits_total{cache="cargos",replica="local"}    114289
cache_hits_total{cache="candidatos",replica="local"} 114289
cache_misses_total{cache="..."}                            1   (por cache)
```

Los 4 misses son los 4 llenados iniciales. Ni uno más en 457.160 lecturas.

## Comparativa: Caffeine (Phase 1) vs Valkey (Phase 2)

Mismo script, mismo backend, misma carga:

| | Caffeine (Phase 1) | Valkey (Phase 2) | Diferencia |
|---|---|---|---|
| Hit ratio | 100 % | **100 %** | — |
| p95 | 0.92 ms | **1.07 ms** | **+0.15 ms** |
| Consultas de configuración a la base | 4 / 45.856 lecturas | **4 / 457.160 lecturas** | — |
| Invalidación entre réplicas | **no se propagaba** | **se propaga** | qualitative |

El p95 sube **0.15 ms**: es el salto a la red que la cache distribuida implica.
A cambio, la invalidación de una réplica se propaga a las otras, que es
justo el defecto que Phase 2 viene a cerrar. Ese intercambio (0.15 ms por
lectura contra stale cache entre réplicas) es el que justifica el cambio.

Detalle de la línea base sin caché en
`cache-hit-ratio-summary-sin-cache.md`; detalle de la corrida Phase 1 en
`cache-hit-ratio-summary-con-cache.md`.

## Reproducir

```bash
# Stack: Postgres real (rol de app, sin BYPASSRLS) + Valkey + backend
cd frontend/tests/load

# OJO: hay que relajar el rate limit de config-read. Con el limite de
# produccion (60/min) el propio limitador responde 429 y la corrida mide
# el limitador, no la cache. Los limites de produccion NO se tocan: esto
# es solo el entorno de la corrida.
APP_RATELIMIT_CONFIG_READ_LIMIT=100000000 \
APP_RATELIMIT_USER_GLOBAL_LIMIT=100000000

MASTER_EMAIL=admin@control-elector.local MASTER_PASSWORD=admin12345 \
  API_HOST=localhost:8080 k6 run scenarios/cache-hit-ratio.js
```

## Advertencia sobre lo que esta corrida NO demuestra

- **Una sola réplica.** Con una réplica, el hit ratio de Valkey y el de Caffeine
  son el mismo numero. Lo que Phase 2 cambia es el comportamiento con 2+
  réplicas, y eso lo afirma `CacheDeLecturaValkeyIT` (otra réplica ve lo que
  escribió esta, y su invalidación se propaga), no esta corrida.
- **No es una prueba de degradación.** Con Valkey caído la cache se pierde y
  el sistema sigue sirviendo desde la base: eso lo cubre
  `CacheValkeyCaidaIT`, no este script.
