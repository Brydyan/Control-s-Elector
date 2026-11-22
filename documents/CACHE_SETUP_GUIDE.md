# Guía de Configuración de Caché — T-CACHE-STRAT-01

> Cómo se configura la caché en cada entorno y qué decisiones siguen abiertas.

## Resumen

| Entorno | Backend de caché | Qué hace falta |
|----------|-------------------|----------------|
| Local / dev | Caffeine (en memoria) | Nada: no hay servicio que instalar |
| Tests | Caffeine (en memoria) | Nada |
| Producción (k8s) | Caffeine (en memoria), **2 réplicas** | Nada, pero ver el riesgo en "Decisión pendiente D2" |

La caché actual **no necesita Redis ni ningún servicio externo**: Caffeine vive
dentro de la JVM. Lo que sí necesita es el registro de métricas (Micrometer),
que ya está en el `pom.xml` y se publica en `/q/metrics`.

---

## 1. Configuración en `application.properties`

```properties
# Caché de datos estáticos (T-CACHE-STRAT-01 D)
quarkus.cache.caffeine.cargos.expire-after-write=10m
quarkus.cache.caffeine.partidos.expire-after-write=10m
quarkus.cache.caffeine.candidatos.expire-after-write=10m
quarkus.cache.caffeine.geografia.expire-after-write=10m

# Métricas (T-CACHE-STRAT-01 D.5)
quarkus.micrometer.enabled=${QUARKUS_MICROMETER_ENABLED:true}
quarkus.micrometer.export.prometheus.enabled=${QUARKUS_MICROMETER_PROMETHEUS_ENABLED:true}
quarkus.micrometer.binder.http-server.enabled=true
```

Todas las claves admiten variable de entorno con prefijo `QUARKUS_`, así que en
k8s se pueden apagar sin tocar la imagen.

### Desactivar la caché (para medir el "antes")

Hay dos formas, y la diferencia importa:

```bash
# A) Apagar la extensión de caché completa.
#    OJO: `quarkus.cache.enabled` se fija en tiempo de BUILD en Quarkus:
#    poner QUARKUS_CACHE_ENABLED=false en un pod ya construido NO la apaga;
#    hay que reconstruir la imagen.
QUARKUS_CACHE_ENABLED=false ./mvnw quarkus:dev

# B) Bajar el TTL a 1s en las cuatro cachés (no reconstruye nada).
QUARKUS_CACHE_CAFFEINE_CARGOS_EXPIRE_AFTER_WRITE=1s \
QUARKUS_CACHE_CAFFEINE_PARTIDOS_EXPIRE_AFTER_WRITE=1s \
QUARKUS_CACHE_CAFFEINE_CANDIDATOS_EXPIRE_AFTER_WRITE=1s \
QUARKUS_CACHE_CAFFEINE_GEOGRAFIA_EXPIRE_AFTER_WRITE=1s \
  ./mvnw quarkus:dev
```

Para una línea base honesta lo más simple es arrancar el backend con la caché
fuera (opción A) y correr el mismo k6; el contraste de hit ratio y de latencia
es la evidencia de que la caché sirve. La opción B sirve para una comparación
rápida, pero no es una línea base limpia: la caché sigue activa y solo caduca
antes.

### Verificar que la caché está viva

```bash
curl -s http://localhost:8080/q/metrics | grep '^cache_'
```

Salida esperada tras un par de lecturas:

```
cache_hits_total{cache="cargos"} 12
cache_misses_total{cache="cargos"} 1
```

Si `cache_hits_total` no aparece, la caché no está sirviendo: mira los logs
de arranque (`Cache 'cargos' initialized`) y que el TTL esté configurado.

---

## 2. Despliegue en Kubernetes

`k8s/backend.yaml` corre **2 réplicas** del backend. Con Caffeine en memoria
cada réplica tiene su propia caché:

- Una lectura que llega a la réplica A no llena la caché de la réplica B.
- Una escritura en A invalida la caché de A, no la de B: la réplica B puede
  servir datos viejos hasta 10 minutos (el TTL).

Por eso, si se decide ir a Redis (D2), hay que añadir el servicio y las
variables de entorno correspondientes al manifiesto, y ArgoCD se encarga del
rollout. Ningún secreto se versiona: las credenciales van en `k8s/secret.yaml`
(Secret de Kubernetes), nunca en el manifiesto.

---

## 3. Decisión pendiente D2: Caffeine compartido o Redis

**Estado: sin decidir. Requiere decisión humana explícita** (no es un detalle
de implementación: cambia el modelo de despliegue y el coste).

### Opción A — Redis compartido

- Una sola caché para todas las réplicas: sin divergencia de datos, invalidación
  efectiva al instante en todas las réplicas.
- Coste: un servicio más en el cluster, secret, backup y monitoreo.
- **Trabajo de código obligatorio**: `CacheDeLectura` usa
  `cache.as(CaffeineCache.class)` porque la API pública de Quarkus solo
  expone `get(key, loader)` e `invalidate*` (no hay `put` ni `getIfPresent`).
  Con Redis, ese `as(CaffeineCache.class)` falla, el helper cae al camino "sin
  caché" y **la caché se desactiva en silencio**, con el hit ratio en 0 %.
  Hay que reescribir `CacheDeLectura` para hablar la API de Redis.
- Nota: `@CacheResult` (la opción que sí es agnóstica del backend) no sirve
  aquí, porque no permite medir el hit ratio.

### Opción B — Caffeine en memoria con el riesgo documentado

- Cero infraestructura nueva, cero coste.
- Cada réplica cachea por su cuenta: hasta 10 minutos de datos viejos tras una
  escritura, y hasta un 100 % de acierto en la réplica que más recibe tráfico.
- Mitigación posible: bajar el TTL (por ejemplo, 60 s) a cambio de más
  consultas a la base.

### Qué hace falta para decidir

- ¿Cuántas réplicas y qué carga espera en el pico?
- ¿El proceso electoral admite datos de configuración viejos por hasta 10
  minutos tras un cambio (por ejemplo, un partido nuevo que no aparece en el
  formulario del observador)?- ¿Hay presupuesto para un Redis gestionado?

**Recomendación del equipo técnico**: con 2 réplicas y una jornada electoral
donde los cambios de configuración son raros, Redis compartido es lo más
simple de razonar (una sola verdad), pero no se aplica sin la decisión
explícita de la persona responsable.

---

## 4. Alternativa temporal: caché compartida en el frontend

Mientras D2 no esté decidida, el frontend ya cachea los partidos por sesión
(`shareReplay(1)`), así que el efecto de la caché es visible para el usuario
aunque la réplica del backend sirva datos viejos: el navegador no vuelve a
pedirlos hasta que recargue la página.

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
k6 run scenarios/cache-hit-ratio.js
```

---

## Referencia

- `documents/CACHING_STRATEGY.md` — cómo funciona la caché
- `documents/CACHE_INVALIDATION.md` — las tres vías de invalidación
- `frontend/tests/load/scenarios/cache-hit-ratio.js`
