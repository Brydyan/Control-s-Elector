# Estrategia de Caché — T-CACHE-STRAT-01

> Estado: Phase 2 implementada en backend (Valkey distribuido) y frontend. Última actualización: 2026-11-22.
> Spec Phase 1: `openspec/changes/back/2026-11-19-caching-strategy/`.
> ODD Phase 2: `odd/tasks/phase2-caching-valkey-distributed.md`.

## Qué cachea y por qué

Solo **datos estáticos de la configuración preelectoral**: cargos, partidos,
candidatos y geografía. Son los datos que el frontend pide en cada pantalla y
que casi no cambian durante la jornada. Ni un solo acta, ni votos, ni estados
de registro pasan por la caché.

| Capa | Dato | Mecanismo | TTL |
|------|------|-----------|-----|
| Frontend | partidos (formulario del observador) | RxJS `shareReplay(1)` | sesión |
| Frontend | resumen, resultados, progreso, novedades (dashboard) | RxJS `shareReplay(1)` | sesión |
| Backend | `cargos`, `partidos`, `candidatos`, `geografia` | Quarkus Cache + Valkey (Redis protocol, OSS) | 2 min |

Efecto medido en los tests:

- Formulario del observador con 5 cargos: **6 peticiones HTTP** (1 de partidos +
  5 de candidatos) en vez de 11.
- Dashboard: **4 peticiones** en vez de 5 al saltar entre secciones, porque la
  caché vive en el servicio, no en el componente.
- Backend: la 2ª lectura de cargos/partidos/candidatos/geografía **no toca la
  base** (`CacheServicioIT` cuenta consultas con `SessionFactory.getStatistics()`).
- Multi-replica: todas las réplicas comparten el mismo caché Valkey; una
  invalidación por escritura se ve en las demás réplicas.

---

## Frontend: RxJS `shareReplay(1)`

### Patrón

```typescript
private partidosCache$: Observable<PartidoDto[]> | null = null;

getCandidatosPorCargo(cargoId: number): Observable<CandidatoInfo[]> {
  if (!this.partidosCache$) {
    this.partidosCache$ = this.http
      .get<PartidoDto[]>(`${this.configUrl}/partidos`)
      .pipe(shareReplay(1));
  }
  return forkJoin({
    candidatos: this.http.get<CandidatoDto[]>(`${this.configUrl}/candidatos?cargoId=${cargoId}`),
    partidos: this.partidosCache$,
  }).pipe(map(...));
}
```

### Comportamiento

- **Primera suscripción**: sale la petición HTTP.
- **Suscripciones siguientes**: reciben la misma respuesta (multicast).
- **Error**: no queda cacheado; el siguiente suscriptor reintenta.
- **Limpieza**: manual con `invalidateCache()`.

### Dónde vive la caché

En un servicio `@Injectable({ providedIn: 'root' })`, no en el componente. Por
eso sobrevive a la navegación dentro de la SPA: el servicio raíz no se
destruye al cambiar de ruta. Por eso el E2E navega con `routerLink` y **no**
con `page.goto()`: un `goto` recarga la aplicación entera y borra la caché.

### Cuándo usar `shareReplay(1)`

✅ **Sí**
- GET a datos que no cambian durante la sesión (configuración preelectoral).
- Varios suscriptores al mismo dato.

❌ **No**
- Datos que cambian (actas, votos, estados, progreso en vivo).
- Mutaciones (POST/PUT/DELETE).
- Cuando se necesita TTL: eso es la caché del backend.

---

## Backend: Quarkus Cache + Valkey (Phase 2)

### Por qué no `@CacheResult`

La anotación `@CacheResult` de Quarkus no deja ver si el acierto vino de la
caché o de la base: el hit ratio —la métrica que dice si la caché está
sirviendo o si cada pedido sigue yendo a la base— queda a ciegas. Por eso la
lectura es explícita, con un helper que cuenta:

- `shared/cache/CacheDeLectura.leer(cacheName, clave)` → acierto (`cache.hits`)
- `shared/cache/CacheDeLectura.guardar(cacheName, clave, valor)` → fallo (`cache.misses`)

El patrón en un caso de uso:

```java
Optional<List<Cargo>> enCache = cache.leer("cargos", "todos", TIPO_CARGOS);
if (enCache.isPresent()) {
  return enCache.get();
}
List<Cargo> cargos = Cargo.listAll();   // la consulta se queda en el caso de uso
cache.guardar("cargos", "todos", cargos);
return cargos;
```

La consulta a la base **no** va dentro del helper: una lambda que llama a un
método estático de Panache se ejecuta en otra clase sintética, y ahí Panache ya
no tiene inyectada su implementación
(`IllegalStateException: implementationInjectionMissing`).

Phase 2: el backend es Valkey (protocolo Redis, BSD-3-Clause, Linux
Foundation). `CacheDeLectura` usa `quarkus-redis-cache`/`RedisCache` para
leer/escribir y mantiene contadores Micrometer (`cache.hits`, `cache.misses`)
porque Valkey no expone hit/miss automático. Si Valkey no responde, el helper
degrada a "sin caché" (consulta a base) y registra el fallo; la app sigue
operativa.

**Por qué la lectura necesita un tipo.** El backend Redis devuelve bytes: para
devolver `List<Cargo>` y no `List<Object>`, la deserialización necesita el tipo
concreto. La API de `RedisCache` solo ofrece `getOrNull(K, Class<V>)` y
`getOrNull(K, TypeLiteral<V>)`: no hay sobrecarga con `java.lang.reflect.Type`.
`TypeLiteral` es el único vehículo que transporta un tipo genérico, y para
construir uno con el tipo del caller el helper reflectea sobre el campo privado
`TypeLiteral.actualType`.

Eso funciona en la imagen nativa **porque Quarkus registra el campo para
reflexión**, y no por casualidad: si una actualización de Quarkus dejara de
registrarlo, la lectura fallaría y la caché degradaría a "siempre miss" en
silencio (el backend seguiría respondiendo 200, pero cada request iría a la
base). Por eso el job `backend-native` de CI tiene el paso *"Assert the
distributed cache serves the second read"*, que mira el contador de aciertos y
no el código de respuesta.

### Implementación

| Caso de uso | Caché | Clave | TTL |
|-------------|-------|-------|-----|
| `ListarCargosUseCase` | `cargos` | `todos` | 2 min |
| `ListarPartidosUseCase` | `partidos` | `todos` | 2 min |
| `ListarCandidatosUseCase` | `candidatos` | `cargo:<cargoId>\|partido:<partidoId>` | 2 min |
| `ListarGeografiaUseCase` | `geografia` | `todos` | 2 min |

La clave de `candidatos` lleva los filtros: el listado sin filtros y el de un
cargo son datos distintos y no pueden compartir entrada.

### Configuración (`application.properties`)

```properties
quarkus.cache.enabled=${QUARKUS_CACHE_ENABLED:true}
quarkus.cache.type=redis
quarkus.cache.redis.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.redis.cargos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.redis.partidos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.redis.candidatos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.redis.geografia.expire-after-write=${CACHE_TTL:2m}

# Tipos por cache. OJO: `java.util.List` es un tipo CRUDO. Sirve para la
# escritura (el backend serializa la lista sin saber de qué elementos se trata);
# la forma genérica de la LECTURA la resuelve el helper reflejando el tipo del
# caller sobre `TypeLiteral` (ver "Por qué la lectura necesita un tipo").
quarkus.cache.redis.cargos.value-type=java.util.List
quarkus.cache.redis.partidos.value-type=java.util.List
quarkus.cache.redis.candidatos.value-type=java.util.List
quarkus.cache.redis.geografia.value-type=ec.twintailcs.electoral.geografia.web.dto.GeografiaTreeDto

# Redis/Valkey compartido (rate limiting + cache)
quarkus.redis.hosts=${REDIS_HOSTS:redis://localhost:6379}

# Réplica (Railway inyecta RAILWAY_REPLICA_ID): etiqueta en las métricas de caché
replica.id=${RAILWAY_REPLICA_ID:local}
```

### Phase 2: Valkey distribuido (reemplaza Caffeine)

Phase 1 usaba Caffeine en memoria por réplica. Eso es correcto con 1 réplica,
pero con 2+ réplicas produce stale cache: una escritura invalida solo la réplica
que la atendió. Phase 2 mueve el backend a **Valkey**, un fork de Redis 7.2.4
que habla el mismo protocolo (RESP) y que licencia **BSD-3-Clause** (Linux
Foundation).

**Por qué Valkey y no Redis.** No es una cuestión de "Redis es closed source"
(Redis 7.2 y anteriores son BSD-3-Clause): Redis 7.4+ usa RSALv2/SSPL, que no
es una licencia OSI, y Redis 8+ es tri-license (con AGPLv3, que sí es OSI).
Valkey resuelve el problema sin Persistent.io: BSD-3-Clause, sin arguables
empresas de por medio, mantenido por la Linux Foundation. Por eso el compose,
los tests y los servicios de CI usan la imagen `valkey/valkey:8-alpine`.

- **Backend:** `quarkus-redis-cache` + `quarkus-redis-client`. Las propiedades
  se llaman `quarkus.redis.*` porque ese es el nombre de la extensión de
  Quarkus, no porque el servidor sea Redis.
- **Estado de la extensión:** `quarkus-redis-cache` está en **preview** en
  Quarkus 3.40.1 (la versión que usa este proyecto). La API puede cambiar en
  una actualización de Quarkus: es el riesgo a vigilar.
- **API de app:** `CacheDeLectura.leer/guardar` sin cambio de firma pública.
- **Invalidación:** `@CacheInvalidateAll` sigue funcionando vía `CacheManager`.
- **Métricas:** `cache.hits`/`cache.misses` manuales en Micrometer, etiquetadas
  con `cache` y `replica`.
- **Fallback:** si Valkey no está disponible, el helper captura la excepción y
  sirve desde base (hit ratio baja, la app no cae). `quarkus.cache.enabled=false`
  sigue siendo el kill switch de build-time.
- **Config Caffeine:** deprecada y comentada en `application.properties` como
  referencia histórica.

---

## Invalidación

- **Por escritura**: los 21 casos de uso de escritura (cargos, partidos,
  candidatos, provincia, cantón, parroquia, JRV: crear, actualizar, eliminar)
  llevan `@CacheInvalidateAll`, así que cualquier cambio entra en caché
  deja la caché inválida en esa misma instancia.
- **Por TTL**: 2 minutos, como red de seguridad.
- **Manual**: `GET /api/v1/admin/cache/clear` (solo MASTER, queda auditado).
  Detalle en `CACHE_INVALIDATION.md`.

---

## Monitoreo

### Métricas del backend

Con Micrometer en el classpath, `/q/metrics` publica:

```
cache_hits_total{cache="cargos",replica="local"} 42
cache_misses_total{cache="cargos",replica="local"} 3
```

Los contadores son por nombre de caché. El hit ratio es
`hits / (hits + misses)`.

```bash
curl -s http://localhost:8080/q/metrics | grep '^cache_'
```

### Carga

`frontend/tests/load/scenarios/cache-hit-ratio.js`: 100 VUs, ramp up de 10 s,
10 lecturas por VU sobre los 4 endpoints cacheados. Lee el hit ratio real de
`/q/metrics` (no lo estima) y escribe el reporte en
`results/cache-hit-ratio-summary.{md,json}` — esa carpeta está en `.gitignore`;
las versiones que sí se versionan están en `documents/load-test/`.

```bash
k6 run scenarios/cache-hit-ratio.js
```

Objetivos del SDD: p95 < 2 s, hit ratio > 90 %.

**Medido** (100 VUs contra el backend real, TTL de 2 min): hit ratio **100 %**,
p95 **0.92 ms**, y **4 consultas de configuración a la base** (el llenado
inicial) frente a **374.240** sin caché — un ahorro del **99.999 %**, tres
órdenes de magnitud por encima del objetivo del SDD (−90 %).

El TTL de 2 min tiene un coste medido: en una corrida de 4 min 30 s (con dos
vencimientos del TTL) fueron **33 consultas para 1.103.520 lecturas**, es decir
29 consultas extra frente al llenado inicial. Reportes en
`documents/load-test/`.

### Frontend

DevTools → Network → filtrar `/partidos`, `/dashboard/resultados`. Varias
peticiones al mismo recurso significan caché rota; una sola para N
suscriptores significa que funciona.

---

## Problemas frecuentes

| Síntoma | Causa | Solución |
|---------|-------|----------|
| Datos viejos en el frontend | caché de sesión sin invalidar | `invalidateCache()` o recargar |
| Un observador ve datos viejos y otro no | TTL sin invalidación por escritura | comprobar `@CacheInvalidateAll` en el caso de uso |
| Una réplica quedó desfasada respecto de las otras | backend con más de una réplica | ver la etiqueta `replica` en `/q/metrics` y el guardrail de réplicas |
| La base sigue recibiendo consultas | clave distinta entre llamadas | revisar la clave usada en `CacheDeLectura.leer` |
| Hit ratio 0 % en las métricas | caché no configurada o backend que no es Caffeine | ver `CACHE_SETUP_GUIDE.md` |
| El endpoint de vaciado responde 200 pero no vacía | `invalidateAll()` sin `await` (bug ya corregido) | `cache.invalidateAll().await().indefinitely()` |

---

## Referencias

- [RxJS shareReplay](https://rxjs.dev/api/operators/shareReplay)
- [Quarkus Cache](https://quarkus.io/guides/cache)
- [Caffeine](https://github.com/ben-manes/caffeine)
- `documents/CACHE_INVALIDATION.md`
- `documents/CACHE_SETUP_GUIDE.md`
