# Guía de Configuración de Caché — T-CACHE-STRAT-01

> Cómo se configura la caché en cada entorno y qué decisiones siguen abiertas.

## Resumen

| Entorno | Backend de caché | Qué hace falta |
|----------|-------------------|----------------|
| Local / dev | Caffeine (en memoria) | Nada: no hay servicio que instalar |
| Tests | Caffeine (en memoria) | Nada |
| Producción (Railway) | Caffeine (en memoria), **1 réplica** | Nada: ver el guardrail de réplicas de la sección 2 |

La caché actual **no necesita Redis ni ningún servicio externo**: Caffeine vive
dentro de la JVM. Lo que sí necesita es el registro de métricas (Micrometer),
que ya está en el `pom.xml` y se publica en `/q/metrics`.

---

## 1. Configuración en `application.properties`

```properties
# Caché de datos estáticos (T-CACHE-STRAT-01 D)
# TTL corto a propósito: acota el desfase si el backend llegara a correr en más
# de una réplica. CACHE_TTL lo ajusta por entorno sin tocar el código.
quarkus.cache.caffeine.cargos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.caffeine.partidos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.caffeine.candidatos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.caffeine.geografia.expire-after-write=${CACHE_TTL:2m}

# Métricas estándar de Caffeine por caché (entradas, evictions, acierto/fallo)
quarkus.cache.caffeine.cargos.metrics-enabled=true
quarkus.cache.caffeine.partidos.metrics-enabled=true
quarkus.cache.caffeine.candidatos.metrics-enabled=true
quarkus.cache.caffeine.geografia.metrics-enabled=true

# Identificador de la réplica (Railway inyecta RAILWAY_REPLICA_ID por réplica)
replica.id=${RAILWAY_REPLICA_ID:local}

# Métricas (T-CACHE-STRAT-01 D.5)
quarkus.micrometer.enabled=${QUARKUS_MICROMETER_ENABLED:true}
quarkus.micrometer.export.prometheus.enabled=${QUARKUS_MICROMETER_PROMETHEUS_ENABLED:true}
quarkus.micrometer.binder.http-server.enabled=true
```

Todas las claves admiten variable de entorno, así que en Railway se pueden
ajustar sin tocar la imagen: `CACHE_TTL` para el TTL y `RAILWAY_REPLICA_ID` la
pone la propia plataforma.

### Desactivar la caché (para medir el "antes")

Hay dos formas, y la diferencia importa:

```bash
# A) Apagar la extensión de caché completa.
#    OJO: `quarkus.cache.enabled` se fija en tiempo de BUILD en Quarkus:
#    poner QUARKUS_CACHE_ENABLED=false en un despliegue ya construido NO la
#    apaga; hay que reconstruir la imagen.
QUARKUS_CACHE_ENABLED=false ./mvnw quarkus:dev

# B) Bajar el TTL a 1s en las cuatro cachés (no reconstruye nada).
CACHE_TTL=1s ./mvnw quarkus:dev
```

Para una línea base honesta lo más simple es arrancar el backend con la caché
fuera (opción A) y correr el mismo k6; el contraste de hit ratio y de latencia
es la evidencia de que la caché sirve. La opción B sirve para una comparación
rápida, pero no es una línea base limpia: la caché sigue activa y solo caduca
antes.

> Con la caché apagada la aplicación **sigue funcionando**: se sirve siempre
> desde la base. Lo sostiene `CacheApagadaIT`, que además afirma que los
> endpoints de configuración responden 200 en ese modo.

### Verificar que la caché está viva

```bash
curl -s http://localhost:8080/q/metrics | grep '^cache_'
```

Salida esperada tras un par de lecturas:

```
cache_hits_total{cache="cargos",replica="local"} 12
cache_misses_total{cache="cargos",replica="local"} 1
```

La etiqueta `replica` no es adorno: Railway **agrega** las métricas de todas sus
réplicas en un solo tablero, así que sin ella no se puede distinguir "el hit
ratio bajó" de "una réplica quedó desfasada respecto de las otras".

Si `cache_hits_total` no aparece, la caché no está sirviendo: mira los logs
de arranque (`Cache 'cargos' initialized`) y que el TTL esté configurado.

---

## 2. Despliegue en Railway: el guardrail de las réplicas

El backend corre en **Railway con 1 réplica**. Con Caffeine en memoria la
caché vive en el proceso, así que el número de réplicas decide si el dato
puede quedar desfasado:

- **Con 1 réplica** (lo actual): no hay split. La invalidación es local e
  instantánea, y el TTL de 2 min es solo la red de seguridad para lo que se
  escribiera por un camino que no pasa por los casos de uso.
- **Con 2 o más réplicas**: Railway reparte el tráfico **al azar** y **no hay
  sticky sessions**. Una escritura en la réplica A no invalida la copia de la
  réplica B, así que B puede servir datos viejos hasta que venza su TTL (2 min).
  El tráfico tampoco se queda en la réplica que invalidó: el siguiente request
  puede caer en la otra.

**Guardrail: antes de escalar el backend a más de una réplica, hay que volver
a mirar D2.** Con el TTL de 2 min el desfase está acotado y es aceptable para
datos de configuración electoral; si además hace falta invalidación
instantánea entre réplicas, la respuesta es una caché compartida (Redis), que
es un cambio de producto con su propio coste, no un ajuste de configuración.

Cómo saber si hay más de una réplica sin mirar el panel de Railway: las
métricas van etiquetadas por réplica. Si `/q/metrics` muestra más de un valor
de `replica=`, hay más de una réplica en servicio.

### El vaciado manual de caché es best-effort por réplica

`GET /api/v1/admin/cache/clear` (rol MASTER) vacía la caché **del proceso que
atiende la petición**. Con una sola réplica, vacía todo. Con varias, vacía
una: la otra sigue con su copia hasta que venza el TTL. No hay forma de vaciar
todas sin una caché compartida.

---

## 3. Decisión D2: Caffeine en memoria (tomada)

**Estado: decidida.** El diseño original (`proposal.md`, `design.md`) elegía
Redis; la decisión se cerró con el despliegue real en Railway y Neon.

**Decisión: Opción A — Caffeine en memoria, TTL de 2 minutos.**

Por qué, en el stack real:

- **El problema que Redis resolvía no existe.** Redis se eligió para que varias
  réplicas compartieran caché. Railway corre **1 réplica**, y sin sticky
  sessions el reparto aleatorio es lo que hace aparecer el split.
- **En Neon, una caché en proceso es mejor que una remota.** Neon es
  PostgreSQL serverless: cada acierto de caché es un round-trip a la red
  (y un posible arranque en frío del compute). Caffeine responde desde el heap;
  Redis solo cambiaría un salto de red (Neon) por otro (Redis). La caché en
  proceso además **amortigua el arranque en frío de Neon**, cosa que Redis no
  hace.
- **Redis sería un servicio nuevo**: coste, un modo de falla más y un
  despliegue más, para un problema que no está presente.
- **Lo que Redis sí daría** —invalidación instantánea entre réplicas— solo
  importa si se escala a más de una réplica, y el TTL de 2 min acota el daño
  mientras tanto.

Qué quedaría para migrar a Redis, si algún día hace falta:

- `CacheDeLectura` usa `cache.as(CaffeineCache.class)` porque la API pública
  de Quarkus solo expone `get(key, loader)` e `invalidate*` (no hay `put` ni
  `getIfPresent`). `Cache.as(Class)` lanza `IllegalStateException` si el caché
  no es del tipo pedido; el helper captura ese tipo y **degrada a "sin caché"**
  (se sirve desde la base, hit ratio 0 %), de modo que Redis no tiraría la
  aplicación — pero tampoco cachearía. Para cachear de verdad hay que
  reescribir `CacheDeLectura` para hablar la API de Redis.
- Nota: `@CacheResult` (la opción que sí es agnóstica del backend) no sirve
  aquí, porque no permite medir el hit ratio.

---

## 4. La caché del frontend cubre el desfase

El frontend ya cachea los partidos por sesión (`shareReplay(1)`), así que el
efecto de la caché es visible para el usuario aunque el backend sirva datos
viejos: el navegador no vuelve a pedirlos hasta que recargue la página.

Ojo: esto **no** protege al observador que acaba de recargar la página en el
momento exacto en que el backend sirve una copia desfasada. Acota el problema,
no lo elimina.

---

## 5. Verificación local rápida

```bash
# Backend
cd backend/electoral-api-quarkus && ./mvnw -o quarkus:dev

# En otra terminal: calentar la caché y mirar los contadores
# Las lecturas de /api/v1/configuracion exigen token (401 sin el).
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"<master@...>","password":"<password>"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

curl -s http://localhost:8080/api/v1/configuracion/cargos \
  -H "Authorization: Bearer $TOKEN" > /dev/null
curl -s http://localhost:8080/api/v1/configuracion/partidos \
  -H "Authorization: Bearer $TOKEN" > /dev/null
curl -s http://localhost:8080/q/metrics | grep '^cache_'
```

Carga (100 VUs, reporte de hit ratio):

```bash
cd frontend/tests/load
MASTER_EMAIL=mastersolon@example.local MASTER_PASSWORD=admin12345 \
  k6 run scenarios/cache-hit-ratio.js
```

Resultado medido (backend real, Postgres 16 con el rol `electoral_app` sin
BYPASSRLS, 100 VUs):

| | Sin caché | Con caché |
|---|---|---|
| Hit ratio | 0 % | **100 %** |
| p95 | 3.70 ms | **0.97 ms** |
| Consultas de configuración a la base | 374.240 | **4** (el llenado inicial) |

La métrica estable es la de **consultas de configuración**: el total absoluto
de consultas al servidor depende del ruido de arranque (login, auditoría,
metadatos) y no se puede comparar entre corridas.

Reportes: `documents/load-test/cache-hit-ratio-summary-{con,sin}-cache.md`.

---

## Referencia

- `documents/CACHING_STRATEGY.md` — cómo funciona la caché
- `documents/CACHE_INVALIDATION.md` — las tres vías de invalidación
- `frontend/tests/load/scenarios/cache-hit-ratio.js`
