# Guía de Configuración de Caché — T-CACHE-STRAT-01

> Phase 2 (2026-11-22): backend de caché distribuido con Valkey (protocolo Redis).
> Phase 1 usaba Caffeine en memoria (1 réplica).

## Resumen

| Entorno | Backend de caché | Qué hace falta |
|----------|-------------------|----------------|
| Local / dev | Valkey en `compose` (servicio `valkey`) | `docker compose --profile dev up -d valkey` |
| Tests | Valkey vía Testcontainers (`ValkeyTestResource`) | Docker disponible |
| Producción (Railway) | Valkey compartido entre réplicas | Servicio Valkey + `REDIS_HOSTS` |

Phase 2 usa `quarkus-redis-cache` sobre Valkey para caché compartida entre
réplicas. La app degrada a "sin caché" si Valkey no está disponible (no cae),
pero en producción debe estar provisionado.

**Por qué Valkey y no Redis.** Valkey es un fork de Redis 7.2.4 con el mismo
protocolo (RESP) y licencia **BSD-3-Clause** (Linux Foundation). Redis 7.4+ usa
RSALv2/SSPL, que no es una licencia OSI; Redis 8+ es tri-license (con AGPLv3).
Las propiedades de Quarkus se llaman `quarkus.redis.*` porque ese es el nombre
de la extensión (`quarkus-redis-client` / `quarkus-redis-cache`), no porque el
servidor sea Redis.

---

## 1. Configuración en `application.properties`

```properties
# Caché de datos estáticos (Phase 2: Valkey distribuido)
quarkus.cache.enabled=${QUARKUS_CACHE_ENABLED:true}
quarkus.cache.type=redis
quarkus.cache.redis.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.redis.cargos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.redis.partidos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.redis.candidatos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.redis.geografia.expire-after-write=${CACHE_TTL:2m}

# Tipos por cache (deserialización en Valkey)
quarkus.cache.redis.cargos.value-type=java.util.List
quarkus.cache.redis.partidos.value-type=java.util.List
quarkus.cache.redis.candidatos.value-type=java.util.List
quarkus.cache.redis.geografia.value-type=ec.twintailcs.electoral.geografia.web.dto.GeografiaTreeDto

# Valkey compartido (rate limiting + cache)
quarkus.redis.hosts=${REDIS_HOSTS:redis://localhost:6379}

# Identificador de la réplica (Railway inyecta RAILWAY_REPLICA_ID por réplica)
replica.id=${RAILWAY_REPLICA_ID:local}
```

La config Caffeine de Phase 1 queda comentada en `application.properties` como
referencia histórica; no se usa con `quarkus.cache.type=redis`.

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
de arranque, que `REDIS_HOSTS` apunte a Valkey, y que el TTL esté
configurado.

---

## 2. Despliegue en Railway: Valkey compartido

Phase 2 usa Valkey como backend de caché compartido. Con múltiples
réplicas, todas leen y escriben el mismo caché:

- **Invalidación:** `@CacheInvalidateAll` invalida la entrada en Valkey; las
  demás réplicas ven el cambio en la siguiente lectura.
- **TTL:** 2 min (`CACHE_TTL`) como red de seguridad.
- **Vaciado manual:** `GET /api/v1/admin/cache/clear` (rol MASTER) limpia el
  caché compartido vía `CacheManager`; queda auditado.
- **Fallback:** si Valkey no responde, `CacheDeLectura` degrada a "sin caché"
  (sirve desde la base) y registra el error en debug; la app no devuelve 500
  por la caché.

**Guardrail operativo:** provisionar Valkey antes de escalar réplicas.
Sin Valkey, el sistema funciona pero pierde hit ratio y aumenta carga a Neon.

---

## 3. Decisión de backend: Phase 1 Caffeine, Phase 2 Valkey

**Phase 1 (2026-11-21):** Caffeine en memoria, 1 réplica, TTL 2 min. Correcto
para el despliegue de entonces.

**Phase 2 (2026-11-22, este ODD):** Valkey distribuido. Motivo: al escalar
a 2+ réplicas, Caffeine por réplica genera stale cache tras invalidaciones. La
licencia es BSD-3-Clause (Linux Foundation), a diferencia de Redis 7.4+
(RSALv2/SSPL, que no es OSI).

**Comportamiento esperado:**

- Con Valkey disponible: hit ratio alto, invalidación compartida entre réplicas.
- Con Valkey caído: `CacheDeLectura` captura errores y sirve desde base; la app
  no cae, pero aumenta latencia/carga en DB.
- Con `quarkus.cache.enabled=false` (build-time): la extensión entrega NoOp;
  `CacheDeLectura` degrada a "sin caché".

### 3.1 La lectura con tipo genérico y la imagen nativa

Valkey devuelve bytes. Para que la lectura devuelva `List<Cargo>` y no
`List<Object>`, la deserialización necesita el tipo concreto, y la API de
`RedisCache` solo ofrece `getOrNull(K, Class<V>)` y
`getOrNull(K, TypeLiteral<V>)` — no hay sobrecarga con `java.lang.reflect.Type`.
`TypeLiteral` es el único vehículo que transporta un tipo genérico, así que
`CacheDeLectura.typeLiteral()` construye uno y le fija el tipo del caller
reflejeando sobre el campo privado `TypeLiteral.actualType`.

Eso **sí funciona en la imagen nativa**, porque Quarkus registra ese campo
para reflexión. Pero es una dependencia implícita: si una actualización de
Quarkus deja de registrarlo, la lectura falla, el helper degrada a "sin caché"
y el sistema sigue respondiendo 200 — con el 100 % del tráfico contra la base.
El síntoma es invisible salvo que se mire el contador de aciertos.

Por eso el job `backend-native` de CI tiene el paso **"Assert the distributed
cache serves the second read"**: lee dos veces un endpoint cacheado y exige que
`cache_hits_total` haya subido. Si ese día el registro de reflexión desaparece,
el CI se pone rojo en vez de producción.

**Consecuencia práctica:** al actualizar Quarkus, revisar ese paso. No es un
detalle: es lo que separa "la caché sirve" de "la caché finge que sirve".

### 3.2 Estado de la extensión

`quarkus-redis-cache` está en **preview** en Quarkus 3.40.1 (la versión que
usa este proyecto). Es la extensión oficial de Quarkus, pero en preview: la API
puede cambiar entre versiones. Riesgo de mantenimiento, no de runtime —
siempre que se conserve el paso de aserción de 3.1.

---

## 4. Caché frontend

El frontend sigue cacheando catálogos por sesión (`shareReplay(1)`). Eso reduce
requests, pero no reemplaza la consistencia del backend. Con Valkey, el backend
comparte invalidaciones entre réplicas; el frontend sigue refrescando al
recargar sesión/página según su política actual.

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
| p95 | 3.70 ms | **0.92 ms** |
| Consultas de configuración a la base | 374.240 | **4** (el llenado inicial) |

La métrica estable es la de **consultas de configuración**: el total absoluto
de consultas al servidor depende del ruido de arranque (login, auditoría,
metadatos) y no se puede comparar entre corridas.

Con backend Valkey, el tamaño no vive en el heap de la JVM sino en
Valkey. Vigilar memoria/evictions de Valkey y `cache_hits_total` /
`cache_misses_total` por caché/replica en `/q/metrics`.

Reportes: `documents/load-test/cache-hit-ratio-summary-{con,sin}-cache.md`.

---

## Referencia

- `documents/CACHING_STRATEGY.md` — cómo funciona la caché
- `documents/CACHE_INVALIDATION.md` — las tres vías de invalidación
- `frontend/tests/load/scenarios/cache-hit-ratio.js`
