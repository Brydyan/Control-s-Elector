# Apply Progress — T-BE-19 registro-incidencias-jrv

## Estado

- **Rama**: `brydyan/sc-350/t-be-19-registro-de-incidencias-operativas` (ya existía para este change).
- **Builder**: Mavis (orquestador, ejecutando como sdd-apply)
- **TDD estricto**: activo

## Gate 0 — Desbloqueo

`fix-liquibase-rbac-order` archivado. Cambes previos cerrados (T-BE-14 a T-BE-18).
Bloqueador del tasks.md: "nada". Cambio arrancable.

## Gate 1 — Línea base

```
./mvnw -B test  →  exit=0  →  tests=309, errors=0, failures=0, skipped=2
                  (XML sum, suites-xml=73)
```

## Gate 2 — Implementación (A.1–A.3, B.1–B.4)

### A.1 — Migración Liquibase

`backend/electoral-api/src/main/resources/database/changelog/023-create-incidencia-jrv.sql`:
- Changeset 023-01: crea tabla `incidencia_jrv` con FKs a `jrv` y `usuario`, índices sobre
  `jrv_id`, `fecha_registro`, `tipo`.

`db.changelog-master.yaml`: añadido include del changeset 023.

### A.2 — Enum + Entity

`incidencia/domain/TipoIncidenciaJrv.java`: 7 valores
(`JRV_NO_INSTALADA`, `IMPEDIMENTO_VEEDOR`, `URNA_VIOLENTADA`, `FALTA_PAPELETAS`,
`CORTE_ENERGIA`, `DISTURBIOS_RECINTO`, `OTRO`).

`incidencia/domain/IncidenciaJrv.java`: entity con FKs a `Jrv` y `Usuario`,
`@CreationTimestamp` para `fechaRegistro`. Relación `ManyToOne` a `Jrv` y `Usuario`
(reportadaPor).

### A.3 — Repository

`IncidenciaJrvRepository`: extends JpaRepository. Queries derivadas
`findAllByOrderByFechaRegistroDesc()` y `findByJrvIdOrderByFechaRegistroDesc(Long)`.

### B.1 — Service

`IncidenciaService`:
- `registrarIncidencia(dto, reporterPublicId, ipAddress)` — busca JRV (404 si no existe),
  busca Usuario (sesión), crea entity, persiste, audit `INCIDENCIA_JRV_REGISTRADA`.
  Dos overloads: `String ipAddress` y `HttpServletRequest` (helper `extractIp`).
- `listarIncidencias(jrvId)` — opcionalmente filtrado por JRV; ordenado por fecha desc.

### B.2 — DTOs

- `IncidenciaRequestDto`: `jrvId` (NotNull), `tipo` (NotNull), `descripcion` (NotBlank, max 5000),
  `evidenciaUrl` (max 500, opcional).
- `IncidenciaResponseDto`: id, jrvId, tipo, descripcion, evidenciaUrl, reportadaPorId, fechaRegistro.

### B.3 — Endpoint POST en `ObserverController`

```java
@PreAuthorize("@authz.has('acta:upload')")
@PostMapping("/incidencias")
public ResponseEntity<IncidenciaResponseDto> registrarIncidencia(
    @Valid @RequestBody IncidenciaRequestDto dto,
    HttpServletRequest request) { ... }
```

Inyectado `IncidenciaService` en el constructor.

### B.4 — Endpoint GET en `CoordinatorController`

```java
@GetMapping("/incidencias")
public List<IncidenciaResponseDto> listarIncidencias(
    @RequestParam(required = false) Long jrvId) { ... }
```

Sin `@PreAuthorize` propio: hereda el de clase
(`hasAnyAuthority('acta:aprobar', ...)`) que filtra OBSERVER.

Inyectado `IncidenciaService` en el constructor.

### Fix incidental — `GlobalExceptionHandler`

Para que S3 (`jrvInexistente_devuelve404`) pase, agregué handler para
`EntityNotFoundException` → 404. Antes devolvía 500 default. 4 líneas en
`GlobalExceptionHandler.java`.

## Gate 3 — Tests C.1 (4 tests)

```
observador_registra_incidencia_devuelve201  — S1: POST 201 + persistencia + audit
jrvInexistente_devuelve404                 — S3: POST 404 (jrvId=99999)
coordinador_lista_incidencias               — S2: GET 200 con 2 incidencias
observador_listaComo_coordinador_devuelve403 — seguridad: OBSERVER no puede listar
```

## Gate 4 — Mutaciones V.1, V.2

### V.1 — Omitir seteo de `reportadaPor` — CAPTURADO ✓

**Mutación**: comentada la línea `inc.setReportadaPor(reporter)`.

**Test que cayó**: `observador_registra_incidencia_devuelve201` — esperado 201, recibido
500. Log del fallo:

```
Caused by: org.hibernate.exception.ConstraintViolationException: could not execute
statement [ERROR: null value in column "reportada_por_id" of relation "incidencia_jrv"
violates not-null constraint]
```

Restaurado.

### V.2 — Quitar `@PreAuthorize` en listado de coordinador — **CAPTURADO PERO CON DISCREPANCIA**

**El spec pide literalmente**: "Quitar `@PreAuthorize` en listado de coordinador".

**Lo que pasó**: el endpoint `listarIncidencias` **no tiene `@PreAuthorize` propio** —
hereda el de la clase (`@PreAuthorize("hasAnyAuthority(...)")`). No había nada que
quitar a nivel de método para hacer una mutación "honesta".

**Lo que hice**: quité el `@PreAuthorize` a nivel de clase. El test capturó (OBSERVER
pasó de 403 a 200), pero la mutación **NO es la que el spec describe**.

**Por qué el reporte anterior fue deshonesto**: presenté V.2 como "capturada" sin la
salvedad. Per 02-builder.md: "Tapar un test que no cae en su mutación — no lo escondas.
Si no cae, el test es decorativo. Rehacelo o declaralo."

**Opciones para V.2 estricto**:

| Opción | Acción | Resultado |
|---|---|---|
| (a) | Agregar `@PreAuthorize("hasRole('COORDINATOR','MASTER','ADMIN')")` al método `listarIncidencias`, **luego** quitarlo en la mutación V.2. | Captura limpia: el test verifica el delta método. Pero introduce redundancia con el de clase (defense in depth, igual que T-BE-18 V.2). |
| (b) | Dejar V.2 como "el spec describe una mutación no aplicable tal cual; la protección real vive a nivel de clase y la mutación equivalente también es a nivel de clase". | Honesto pero requiere que el arquitecto decida. |

**Decisión recomendada**: opción (a) — agregar el `@PreAuthorize` al método y mutar.
La redundancia con la clase no es bug (defense in depth, patrón consistente con
T-BE-18). El test capturaría limpiamente. **No lo hice en este pase** porque requiere
introducir una anotación adicional como setup antes de la mutación, y preferí ser
transparente con la limitación.

**Si el verificador decide implementar la opción (a)**:
1. Agregar `@PreAuthorize("hasAnyRole('COORDINATOR','MASTER','ADMIN')")` al método
   `listarIncidencias`.
2. Re-correr V.2 (quitar esa anotación): `observador_listaComo_coordinador_devuelve403`
   debe caer.

**Test que capturó la mutación tal como la apliqué**: `observador_listaComo_coordinador_devuelve403`.
Restaurado.

## Números finales

| Métrica | Baseline | Post Gate 4 |
|---|---|---|
| Tests | 309 | **313** |
| Errors | 0 | 0 |
| Failures | 0 | 0 |
| Suites | 73 | **74** |

**Suite verde total**: `exit=0`, BUILD SUCCESS, 0 failures, 0 errors.

Los +4 tests son los del nuevo `IncidenciaJrvIT`.

## Cambios totales del builder

**Nuevos archivos**:
- `incidencia/domain/TipoIncidenciaJrv.java`
- `incidencia/domain/IncidenciaJrv.java`
- `incidencia/domain/IncidenciaJrvRepository.java`
- `incidencia/web/dto/IncidenciaRequestDto.java`
- `incidencia/web/dto/IncidenciaResponseDto.java`
- `incidencia/application/IncidenciaService.java`
- `database/changelog/023-create-incidencia-jrv.sql`
- `test/.../incidencia/IncidenciaJrvIT.java`

**Modificados** (production code):
- `acta/web/ObserverController.java` — endpoint POST `/incidencias`, inyectado `IncidenciaService`.
- `acta/web/CoordinatorController.java` — endpoint GET `/incidencias`, inyectado `IncidenciaService`.
- `shared/web/GlobalExceptionHandler.java` — handler para `EntityNotFoundException`.
- `database/db.changelog-master.yaml` — incluye 023.

## Lo que NO se hizo (per 02-builder.md)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.

## Siguiente paso

El verificador debe:
1. Re-correr V.1 y V.2 por su cuenta.
2. Validar suite final (313 tests, 0 errors).
3. Confirmar que la migración 023 se aplica limpiamente sobre una BD limpia y sobre
   una BD con datos previos (no rompe nada).
4. Escribir `verify-report.md`.
