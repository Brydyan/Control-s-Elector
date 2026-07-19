# Paginación y Filtros — Estándar del proyecto

## Stack elegido

- **Spring Data `Pageable`** para paginación + ordenamiento
- **`JpaSpecificationExecutor`** (Specifications) para filtros dinámicos componibles
- **Sin dependencias extra** — ya viene en `spring-boot-starter-data-jpa`

## Por qué Specifications

| Opción | Veredicto |
|--------|-----------|
| `Pageable` directo en query methods | Sirve para consultas fijas, explota si hay combinaciones de filtros |
| QueryDSL | Poderoso pero abandonado (fork OpenFeign), dependency weight innecesario |
| **Specifications** ✅ | **Sin deps extra, componible, type-safe vía Metamodel, estándar Spring** |

## Patrón

### 1. Repository extiende `JpaSpecificationExecutor`

```java
public interface EvidenciaRepository extends JpaRepository<Evidencia, Long>,
                                             JpaSpecificationExecutor<Evidencia> {
}
```

### 2. Clase de Specifications por entidad

```java
public class EvidenciaSpecs {

    public static Specification<Evidencia> jrvIdEq(Long jrvId) {
        return (root, query, cb) -> 
            jrvId == null ? null : cb.equal(root.get("jrvId"), jrvId);
    }

    public static Specification<Evidencia> cargoIdEq(Long cargoId) {
        return (root, query, cb) -> 
            cargoId == null ? null : cb.equal(root.get("cargoId"), cargoId);
    }

    public static Specification<Evidencia> estadoEq(String estado) {
        return (root, query, cb) -> 
            estado == null || estado.isBlank() ? null : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Evidencia> creadoEntre(Instant desde, Instant hasta) {
        return (root, query, cb) -> {
            if (desde == null && hasta == null) return null;
            if (desde != null && hasta != null) return cb.between(root.get("creadoEn"), desde, hasta);
            if (desde != null) return cb.greaterThanOrEqualTo(root.get("creadoEn"), desde);
            return cb.lessThanOrEqualTo(root.get("creadoEn"), hasta);
        };
    }
}
```

### 3. Controller recibe `Pageable` y filtros como `@RequestParam`

```java
@GetMapping
public Page<EvidenciaResponse> listar(
        @RequestParam(required = false) Long jrvId,
        @RequestParam(required = false) String estado,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "creadoEn") String sort,
        @RequestParam(defaultValue = "desc") String dir) {

    Pageable pageable = PageRequest.of(page, size, 
        Sort.by(Sort.Direction.fromString(dir), sort));

    Specification<Evidencia> spec = Specification
        .where(EvidenciaSpecs.jrvIdEq(jrvId))
        .and(EvidenciaSpecs.estadoEq(estado));

    return repo.findAll(spec, pageable)
        .map(evidenciaMapper::toDto);
}
```

### 4. Service layer (opcional, para lógica extra)

Si hay lógica de negocio antes de paginar, el service recibe `Pageable` y delega al repo:

```java
public Page<EvidenciaResponse> listar(Long jrvId, String estado, Pageable pageable) {
    Specification<Evidencia> spec = SpecsBuilder.forEvidencia(jrvId, estado);
    return repo.findAll(spec, pageable).map(mapper::toDto);
}
```

## Response format

Spring `Page` serializa automáticamente a JSON:

```json
{
  "content": [...],
  "totalElements": 142,
  "totalPages": 8,
  "number": 0,
  "size": 20,
  "sort": { "sorted": true, "unsorted": false, "empty": false },
  "first": true,
  "last": false,
  "empty": false
}
```

## Conventions del proyecto

- `page` = 0-based (default: 0)
- `size` = items por página (default: 20, max: 100)
- `sort` = nombre del campo (default: `creadoEn`)
- `dir` = `asc` | `desc` (default: `desc`)
- Filtros nulos se ignoran (null-safe en cada Spec)
- Para joins/relaciones: `root.join("usuario").get("nombre")`

## JPA Metamodel (opcional)

Para type-safety sin strings mágicos, agregar dependency:

```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-jpamodelgen</artifactId>
    <scope>provided</scope>
</dependency>
```

Genera `Evidencia_.java` con constantes tipadas para los field names.
