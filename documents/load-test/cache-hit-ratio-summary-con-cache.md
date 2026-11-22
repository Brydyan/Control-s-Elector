# Cache Hit Ratio — T-CACHE-STRAT-01 F.5 — CON CACHÉ

Backend real (Postgres 16 en Docker, rol `electoral_app` **sin BYPASSRLS**),
100 VUs, ramp up de 10 s, 10 lecturas por VU.

## Resultado

| Métrica | Valor | Objetivo | Estado |
|---|---|---|---|
| Hit ratio | **100.00 %** | > 90 % | ✅ |
| p95 | **0.97 ms** | < 2 s | ✅ |
| Peticiones fallidas | **0** | 0 | ✅ |
| Iteraciones | 11.497 | — | — |
| Lecturas de configuración | ~39.100 | — | — |
| **Consultas de configuración a la base** | **4** | — | — |

## La métrica que importa

**Consultas de configuración a la base: 4** (una por endpoint cacheado:
partidos, cargos, geografía y candidatos) para ~39.100 lecturas. Son el
llenado inicial de la caché; a partir de ahí, la base no vuelve a tocarse.

> El **total absoluto** de consultas al servidor (15 en esta corrida, 23 en
> otra) no es una métrica estable: depende del ruido de arranque (login,
> refresco de sesión, metadatos de JDBC, auditoría). Por eso se mide el
> desglose de consultas **de configuración**, que sí es reproducible.

Cómo reproducir el desglose:

```bash
docker exec ctpg psql -U electoral_user -d electoral_db \
  -c "select pg_stat_statements_reset();" >/dev/null

cd frontend/tests/load
MASTER_EMAIL=mastersolon@example.local MASTER_PASSWORD=admin12345 \
  k6 run scenarios/cache-hit-ratio.js

docker exec ctpg psql -U electoral_user -d electoral_db -c "
  select calls, left(query, 50) as consulta
  from pg_stat_statements
  where (query like '%from cargo%' or query like '%from partido%'
      or query like '%from candidato%' or query like '%from provincia%'
      or query like '%from canton%' or query like '%from parroquia%'
      or query like '%from jrv%')
    and query not like '%pg_stat%'
  order by calls desc;"
```

## Comparativa

| | Sin caché | Con caché | Ahorro |
|---|---|---|---|
| Hit ratio | 0 % | 100 % | — |
| p95 | 3.70 ms | 0.97 ms | **−74 %** |
| Consultas de configuración | 374.240 | **4** | **−99.999 %** |

El objetivo del SDD era −90 % de consultas: **cumplido por tres órdenes de
magnitud**. Detalle de la línea base en
`cache-hit-ratio-summary-sin-cache.md`.
