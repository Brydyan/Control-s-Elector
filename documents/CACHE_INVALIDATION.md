# Invalidación de Caché — T-CACHE-STRAT-01

> Cubre las cuatro cachés del backend (`cargos`, `partidos`, `candidatos`,
> `geografia`) y la caché de sesión del frontend.

## Las tres vías de invalidación

| Vía | Cuándo se dispara | Alcance | Latencia para el usuario |
|-----|-------------------|---------|--------------------------|
| Por escritura | Crear / actualizar / eliminar un cargo, partido, candidato, provincia, cantón, parroquia o JRV | Toda la caché del backend, en esa instancia | Inmediata |
| Por TTL | 10 minutos desde la escritura | La caché que no se volvió a escribir | Hasta 10 min |
| Manual | Un MASTER lo pide | Todos los caches o uno | Inmediata |

La invalidación por escritura es la que resuelve el caso normal. El TTL es la
red de seguridad para lo que se escribió por fuera del backend (SQL manual,
script de seed). El vaciado manual es para incidentes: "los datos están mal y
no podemos esperar 10 minutos".

---

## 1. Invalidación por escritura

Los 21 casos de uso de escritura llevan `@CacheInvalidateAll`:

| Dominio | Casos de uso |
|---------|--------------|
| Electoral | Crear / Actualizar / Eliminar de Cargo, Partido y Candidato (9) |
| Geografía | Crear / Actualizar / Eliminar de Provincia, Cantón, Parroquia y JRV (12) |

```java
@CacheInvalidateAll(cacheNames = "cargos")
@Transactional
public Cargo crearCargo(CargoRequest dto, Long usuarioId, String ip) { ... }
```

`@CacheInvalidateAll` borra **todas** las entradas de la caché nombrada. Como
las listas de cargos/partidos/candidatos/geografía se cachean con una sola
clave (`todos`), no hay entradas parciales que se queden viejas.

Cobertura: `CacheInvalidacionIT` (integración) verifica que, tras una
escritura, la siguiente lectura vuelve a la base.

> **Alcance por instancia**: con Caffeine en memoria, cada réplica del backend
> tiene su propia caché. Un POST en la réplica A no invalida la caché de la
> réplica B; ahí la lectura de B seguirá viendo el dato viejo hasta que pase
> el TTL (10 min) o se vacíe la caché en B. Es el riesgo de la decisión
> pendiente D2 (ver `CACHE_SETUP_GUIDE.md`).

---

## 2. TTL automático

```properties
quarkus.cache.caffeine.cargos.expire-after-write=10m
quarkus.cache.caffeine.partidos.expire-after-write=10m
quarkus.cache.caffeine.candidatos.expire-after-write=10m
quarkus.cache.caffeine.geografia.expire-after-write=10m
```

`expire-after-write`: la entrada se va 10 minutos después de escribirse, no
después de leerse. Un caché que se lee mucho y se escribe poco se queda
viejo 10 minutos como máximo.

---

## 3. Vaciado manual

### Matriz de roles

| Rol | ¿Puede? |
|-----|---------|
| MASTER | Sí |
| ADMIN | No |
| COORDINATOR / OBSERVER | No |

El endpoint exige el permiso `cache:clear` o el rol `MASTER`
(`Autorizador.exigir("cache:clear", "MASTER")`, el patrón real del repo; en
este backend no se usa `@RolesAllowed`). Vaciar la caché es una escritura
sobre el estado operativo de la aplicación, y ADMIN no escribe en este
dominio.

### Endpoint

```
GET /api/v1/admin/cache/clear              → todos los cachés
GET /api/v1/admin/cache/clear?cacheName=cargos → solo ese
```

Con token:

```bash
# 1) Autenticarse como MASTER
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"<master@...>","password":"<password>"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 2) Vaciar todo
curl -s -X GET http://localhost:8080/api/v1/admin/cache/clear \
  -H "Authorization: Bearer $TOKEN"

# 3) Vaciar uno solo
curl -s -X GET "http://localhost:8080/api/v1/admin/cache/clear?cacheName=partidos" \
  -H "Authorization: Bearer $TOKEN"
```

Respuesta 200:

```json
{
  "success": true,
  "message": "All caches cleared successfully",
  "clearedCaches": ["candidatos", "cargos", "geografia", "partidos"]
}
```

Si el caché pedido no existe, responde **404**:

```json
{ "success": false, "message": "Cache 'foo' not found", "clearedCaches": [] }
```

### Auditoría

Cada vaciado queda en `audit_log` (`AuditLogService.record`, el camino genérico
único de escritura sobre esa tabla):

| Columna | Valor |
|---------|-------|
| `recurso` | `CACHE` |
| `entidad_id` | `0` |
| `usuario_id` | el MASTER que ejecutó la orden |
| `accion` | `LIMPIAR_CACHE` |
| `datos` | `{"cacheName":"*","cachesCleared":[...]}` (o el nombre puntual) |
| `ip` | la IP del cliente, tomada de `X-Forwarded-For` |

Verificación:

```sql
select usuario_id, accion, datos, created_at
from audit_log
where recurso = 'CACHE'
order by created_at desc
limit 10;
```

> Detalle de implementación que ya costó un bug: en Quarkus 3.40,
> `Cache.invalidateAll()` devuelve un `Uni` perezoso. Sin
> `.await().indefinitely()` el endpoint respondía **200 sin invalidar nada**.
> El test `AdminCacheResourceTest` verifica el estado real de la caché después
> de la llamada, no solo el código de respuesta.

---

## 4. Caché del frontend (sesión)

La caché del frontend vive en memoria, en servicios `@Injectable({
providedIn: 'root' })`, y dura lo que la pestaña. Se limpia sola al recargar.

```typescript
// ObserverApi
this.observerApi.invalidateCache();

// DashboardApi
this.dashboardApi.invalidateCache();
```

Cuando un dato cambia y el usuario está en la misma sesión, la invalidación
del frontend la dispara la acción que rompió el dato (por ejemplo, el MASTER
que crea un partido y vuelve al dashboard).

---

## Cómo verificar que la invalidación funciona

1. **Con los tests** (rápido, en CI):

```bash
cd backend/electoral-api-quarkus && ./mvnw -o test -Dtest='CacheInvalidacionIT,CacheServicioIT,AdminCacheResourceTest'
```

2. **A mano, contra el backend corriendo**:

```bash
# Calentar la caché
curl -s http://localhost:8080/api/v1/configuracion/cargos > /dev/null

# Ver los contadores
curl -s http://localhost:8080/q/metrics | grep '^cache_'

# Escribir (con token de MASTER)
curl -s -X POST http://localhost:8080/api/v1/configuracion/cargos \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nombre":"Cargo nuevo","nivel":"MUNICIPAL"}'

# Volver a leer: los contadores muestran un acierto nuevo y la lista ya trae
# el cargo nuevo (la escritura invalidó la caché).
curl -s http://localhost:8080/api/v1/configuracion/cargos
```

---

## Referencia

- `documents/CACHING_STRATEGY.md` — cómo funciona la caché
- `documents/CACHE_SETUP_GUIDE.md` — configuración y despliegue
- `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/auth/web/AdminCacheResource.java`
