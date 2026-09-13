# Paginación y Filtros — Contrato vigente

Estándar aprobado tras el merge de #85 (coordinator flow) y #87 (DTO enriquecido).
Reemplaza la versión anterior que proponía `Page<T>` nativo de Spring; el contrato
vigente usa un envelope estricto `{data, meta}` con orden estable y búsqueda por estado.

## Stack

| Capa | Tecnología | Ubicación |
|---|---|---|
| Backend — repositorio | Spring Data `JpaRepository` + `JpaSpecificationExecutor` | `backend/electoral-api/.../domain/<Entity>Repository.java` |
| Backend — paginación | `PageRequestFactory` + `PagedResponse<T>` + `PageMeta` | `backend/electoral-api/.../shared/web/pagination/` |
| Backend — specs | `JpaSpecificationExecutor` con `Specification.where(...).and(...)` | por módulo (`acta`, `evidencia`) |
| Frontend — modelo | `Page<T>` en `shared/models` con `data: T[]` y `meta: PageMeta` | `frontend/app/src/app/shared/models/page.model.ts` |
| Frontend — UI | `<app-paginator>` reutilizable | `frontend/app/src/app/shared/components/paginator/` |
| Frontend — enforcement | servicio lanza si el backend devuelve un array pelado | `coordinator.service.ts` (mismo patrón en otros módulos) |

## Contrato HTTP

### Request (query params)

| Param | Tipo | Default | Notas |
|---|---|---|---|
| `page` | int (0-based) | `0` | `-D` |
| `size` | int | `20` | máximo configurable por endpoint |
| `sort` | string | depende del endpoint | debe pertenecer a `allowedSorts`; el resto devuelve `400` |
| `q` | string (opcional) | — | búsqueda libre específica del endpoint (ej. estado) |

### Response (envelope estricto)

```json
{
  "data": [ /* T[] */ ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8
  }
}
```

**No se devuelve `Page<T>` de Spring** (con `content`, `sort`, `numberOfElements`, etc.).
El envelope `{data, meta}` es deliberadamente minimalista para no acoplar el frontend al modelo interno de Spring.

### Errores

- `400 Bad Request` si `page`, `size` o `sort` son inválidos (mensaje específico por campo).
- `401/403` según RBAC del endpoint.
- `500` solo para fallos no controlados.

## Backend — piezas compartidas

### `PageRequestFactory`

Centraliza la creación de `Pageable` con **tiebreaker obligatorio** sobre `id ASC`
para garantizar orden estable cuando hay timestamps iguales.

```java
Pageable pageable = PageRequestFactory.of(
    page, size, sort, dir, allowedSorts, defaultSort, defaultDir);
```

- `allowedSorts`: `Set<String>` de campos ordenables (rechaza cualquier otro con `400`).
- `defaultSort`/`defaultDir`: aplicados cuando los params vienen vacíos o null.
- El tiebreaker `id ASC` se agrega automáticamente.

### `PagedResponse<T>` y `PageMeta`

```java
public record PagedResponse<T>(List<T> data, PageMeta meta) {}

public record PageMeta(
    int page, int size, long totalElements, int totalPages) {}
```

El controller hace:

```java
Specification<Acta> spec = Specification
    .where(ActaSpecs.estadoEq(estado))
    .and(ActaSpecs.search(q));

Page<Acta> page = actaRepo.findAll(spec, pageable);
return PagedResponse.of(page.map(actaMapper::toDto));
```

### Specifications

Una clase por agregado, con métodos null-safe componibles. Ejemplo vigente:

```java
public final class ActaSpecs {
    private ActaSpecs() {}

    public static Specification<Acta> estadoEq(EstadoActa estado) {
        return (root, query, cb) ->
            estado == null ? null : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Acta> search(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("estado")), pattern);
    }
}
```

**Importante**: el patrón LIKE se precomputa en Java. **Nunca** envolver `:search` con
`LOWER(CONCAT(...))` en JPQL — Postgres infiere `bytea` para el parámetro NULL y rompe
las queries sin filtro.

## Frontend — piezas compartidas

### `Page<T>` model

```ts
export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Page<T> {
  data: T[];
  meta: PageMeta;
}
```

### Servicio enforcement

Cada servicio de listado valida explícitamente el shape:

```ts
if (!body || !Array.isArray(body.data) || !body.meta) {
  console.error('[<Feature>Service] Unexpected response shape', body);
  throw new Error('Expected { data, meta } envelope');
}
return body;
```

Si el backend devuelve un array pelado, el servicio lanza — el caller recibe el
error y muestra el estado de error correspondiente. **Nunca** envolver arrays
// pelados en `{data: array, meta: empty}` automáticamente: oculta bugs de contrato.

### `<app-paginator>`

Componente standalone que recibe `meta`, `loading` y emits `pageChange`.
Recibe inputs tipados (`meta = input.required<PageMeta>()`) y emite `pageChange`
con el nuevo índice. Lo usan las páginas que paginan (`actas-lista` y futuras).

## Convenciones del proyecto

- `page` 0-based. `size` default 20. `sort` default por endpoint.
- Filtros nulos o vacíos se ignoran (cada Spec devuelve `null`).
- El orden es estable SIEMPRE (tiebreaker `id ASC` agregado en `PageRequestFactory`).
- Búsqueda libre (`q`) precomputada en Java como patrón LIKE; sin `LOWER()` ni
  `CONCAT()` alrededor de los bind params JPQL.
- `PagedResponse<T>` se serializa con Jackson estándar (no requiere config extra).
- Frontend **nunca** asume `Page<T>` de Spring — siempre el envelope `{data, meta}`.
- Service frontend **valida** el envelope antes de devolver; lanza si no calza.

## Lista de endpoints que aplican el contrato

| Endpoint | Controller | Notas |
|---|---|---|
| `GET /api/v1/coordinator/actas` | `CoordinatorController` | contrato vigente (paginación + filtros + estado search) |

Endpoints pendientes de migrar al contrato: ver `openspec/ROADMAP.md` y Fase 1
del plan vertical COORDINATOR (T-BE-13).

## Anti-patrones prohibidos

- Devolver `Page<T>` nativo de Spring desde un endpoint público (acopla el contrato).
- Usar `LOWER(CONCAT('%', :q, '%'))` en JPQL (Postgres infiere `bytea` para NULL).
- Aplicar `ORDER BY` sin tiebreaker estable.
- Ordenar por defecto por un campo no presente en `allowedSorts`.
- Permitir `sort` arbitrario sin validar.
- En el frontend, aceptar arrays pelados y envolverlos en `{data, meta}` "por las dudas".
- En el frontend, leer `body.content` (Spring) en lugar de `body.data` (envelope).

## Referencias

- Implementación vigente en `backend/electoral-api/.../shared/web/pagination/`.
- Frontend mirror en `frontend/app/src/app/shared/{models,components/paginator}`.
- Tests de contrato: `PageRequestFactoryTest`, `PagedResponseTest`,
  `paginator.component.spec.ts`, `coordinator.service.spec.ts`.