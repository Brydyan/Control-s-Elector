# Estrategia de Caché — T-CACHE-STRAT-01

> Estado: implementado en backend y frontend. Última actualización: 2026-11-21.
> Spec: `openspec/changes/back/2026-11-19-caching-strategy/`.

## Qué cachea y por qué

Solo **datos estáticos de la configuración preelectoral**: cargos, partidos,
candidatos y geografía. Son los datos que el frontend pide en cada pantalla y
que casi no cambian durante la jornada. Ni un solo acta, ni votos, ni estados
de registries pasan por la caché.

| Capa | Dato | Mecanismo | TTL |
|------|------|-----------|-----|
| Frontend | partidos (formulario del observador) | RxJS `shareReplay(1)` | sesión |
| Frontend | resumen, resultados, progreso, novedades (dashboard) | RxJS `shareReplay(1)` | sesión |
| Backend | `cargos`, `partidos`, `candidatos`, `geografia` | Quarkus Cache (Caffeine) | 10 min |

Efecto medido en los tests:

- Formulario del observador con 5 cargos: **6 peticiones HTTP** (1 de partidos +
  5 de candidatos) en vez de 11.
- Dashboard: **4 peticiones** en vez de 5 al saltar entre secciones, porque la
  caché vive en el servicio, no en el componente.
- Backend: la 2ª lectura de cargos/partidos/candidatos/geografía **no toca la
  base** (`CacheServicioIT` cuenta consultas con `SessionFactory.getStatistics()`).

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

## Backend: Quarkus Cache (Caffeine)

### Por qué no `@CacheResult`

La anotación `@CacheResult` de Quarkus no deja ver si el acierto vino de la
caché o de la base: el hit ratio —la métrica que dice si la caché está
sirviendo o si cada pedido sigue yendo a la base— queda a ciegas. Por eso la
lectura es explícita, con un helper que cuenta:

- `shared/cache/CacheDeLectura.leer(cacheName, clave)` → acierto (`cache.hits`)
- `shared/cache/CacheDeLectura.guardar(cacheName, clave, valor)` → fallo (`cache.misses`)

El patrón en un caso de uso:

```java
Optional<List<Cargo>> enCache = cache.leer("cargos", "todos");
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

### Implementación

| Caso de uso | Caché | Clave | TTL |
|-------------|-------|-------|-----|
| `ListarCargosUseCase` | `cargos` | `todos` | 10 min |
| `ListarPartidosUseCase` | `partidos` | `todos` | 10 min |
| `ListarCandidatosUseCase` | `candidatos` | `todos` | 10 min |
| `ListarGeografiaUseCase` | `geografia` | `todos` | 10 min |

### Configuración (`application.properties`)

```properties
quarkus.cache.caffeine.cargos.expire-after-write=10m
quarkus.cache.caffeine.partidos.expire-after-write=10m
quarkus.cache.caffeine.candidatos.expire-after-write=10m
quarkus.cache.caffeine.geografia.expire-after-write=10m
```

### Advertencia: Caffeine está acoplado en el helper

`CacheDeLectura` usa `cache.as(CaffeineCache.class)` porque la API pública de
Quarkus (`io.quarkus.cache.Cache`) solo expone `get(key, loader)` e
`invalidate*`: no hay `put` ni `getIfPresent`. Consecuencia: **si se cambia a
Redis, la caché se desactiva en silencio** (el `ClassCastException` se
absorbe y toda lectura va a la base) y el hit ratio marca 0 % de acierto.
Cambiar a Redis exige reescribir `CacheDeLectura`; ver la decisión pendiente
D2 en `CACHE_SETUP_GUIDE.md`.

---

## Invalidación

- **Por escritura**: los 21 casos de uso de escritura (cargos, partidos,
  candidatos, provincia, cantón, parroquia, JRV: crear, actualizar, eliminar)
  llevan `@CacheInvalidateAll`, así que cualquier cambio entra en caché
  deja la caché inválida en esa misma instancia.
- **Por TTL**: 10 minutos, como red de seguridad.
- **Manual**: `GET /api/v1/admin/cache/clear` (solo MASTER, queda auditado).
  Detalle en `CACHE_INVALIDATION.md`.

---

## Monitoreo

### Métricas del backend

Con Micrometer en el classpath, `/q/metrics` publica:

```
cache_hits_total{cache="cargos"} 42
cache_misses_total{cache="cargos"} 3
```

Los contadores son por nombre de caché. El hit ratio es
`hits / (hits + misses)`.

```bash
curl -s http://localhost:8080/q/metrics | grep '^cache_'
```

### Carga

`frontend/tests/load/scenarios/cache-hit-ratio.js`: 100 VUs, ramp up de 10 s,
10 lecturas por VU sobre los 4 endpoints cacheados. Lee el hit ratio real de
`/q/metrics` y escribe el reporte en
`results/cache-hit-ratio-summary.{md,json}`.

```bash
k6 run scenarios/cache-hit-ratio.js
```

Objetivos del SDD: p95 < 2 s, hit ratio > 90 %.

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
