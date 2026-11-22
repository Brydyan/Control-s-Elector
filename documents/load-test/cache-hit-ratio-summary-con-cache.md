# Cache Hit Ratio — T-CACHE-STRAT-01 F.5 — CON CACHÉ

Backend real (Postgres 16 en Docker, rol `electoral_app` **sin BYPASSRLS**),
100 VUs, ramp up de 10 s, 10 lecturas por VU, **TTL de 2 min**
(`CACHE_TTL`, decisión D2).

## Resultado

| Métrica | Valor | Objetivo | Estado |
|---|---|---|---|
| Hit ratio | **100.00 %** | > 90 % | ✅ |
| p95 | **0.92 ms** | < 2 s | ✅ |
| Peticiones fallidas | **0** | 0 | ✅ |
| Iteraciones | 11.464 | — | — |
| Lecturas de configuración | 45.856 | — | — |
| **Consultas de configuración a la base** | **4** | — | — |

## La métrica que importa

**Consultas de configuración a la base: 4** (una por endpoint cacheado:
partidos, cargos, geografía y candidatos) para 45.856 lecturas. Son el
llenado inicial de la caché; a partir de ahí, la base no vuelve a tocarse.

> El **total absoluto** de consultas al servidor (15 en esta corrida, 23 en
> otra) no es una métrica estable: depende del ruido de arranque (login,
> refresco de sesión, metadatos de JDBC, auditoría). Por eso se mide el
> desglose de consultas **de configuración**, que sí es reproducible.

## El coste real del TTL de 2 minutos

Un TTL corto no es gratis, así que se midió su coste con una corrida larga
(**4 min 30 s**, meseta de 4 min), en la que el TTL expira dos veces:

| Corrida | Lecturas | Consultas a la base | Consultas por lectura |
|---|---|---|---|
| 30 s (1 llenado) | 45.856 | 4 | 0,0000872 |
| 4 min 30 s (varios rellenos) | 1.103.520 | 33 | 0,0000299 |

Es decir: **el TTL de 2 min añade 29 consultas en 4,5 minutos** para
1,1 millones de lecturas. Un TTL más largo (10 min) las habría reducido a
~4, pero habría dejado hasta 10 minutos de dato viejo si el backend llegara a
correr en más de una réplica. El TTL de 2 min es el punto de equilibrio.

## Comparativa

Para que la comparación valga aunque las corridas tengan distinto número de
iteraciones, se normaliza por **lectura de configuración** (4 endpoints ×
iteraciones):

| | Sin caché | Con caché (TTL 2 min) | Ahorro |
|---|---|---|---|
| Hit ratio | 0 % | **100 %** | — |
| p95 | 3.70 ms | **0.92 ms** | **−75 %** |
| Consultas de configuración | 374.240 | **4** | **−99.999 %** |
| Consultas por lectura | 10,0 | **0,0000872** | **−99.99913 %** |

El objetivo del SDD era −90 % de consultas: **cumplido por tres órdenes de
magnitud**. Detalle de la línea base en
`cache-hit-ratio-summary-sin-cache.md`.

## Reproducir

```bash
docker exec ctpg psql -U electoral_user -d electoral_db \
  -c "select pg_stat_statements_reset();" >/dev/null

# La caché debe estar fría: si no, el llenado inicial ya no se cuenta.
curl -s "http://localhost:8081/api/v1/admin/cache/clear" \
  -H "Authorization: Bearer $TOKEN"

cd frontend/tests/load
MASTER_EMAIL=mastersolon@example.local MASTER_PASSWORD=admin12345 \
  API_HOST=localhost:8081 k6 run scenarios/cache-hit-ratio.js

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

## Nota sobre las etiquetas de las métricas

Los contadores van etiquetados con la caché **y con la réplica**
(`cache_hits_total{cache="cargos",replica="local"}`). Railway **agrega** las
métricas de todas sus réplicas, así que sin la etiqueta `replica` no se
podría distinguir "el hit ratio bajó" de "una réplica quedó desfasada
respecto de las otras". El script de k6 tiene que tolerar etiquetas extra: un
parser que exija `{cache="..."}` exacto leería cero contadores en silencio.
