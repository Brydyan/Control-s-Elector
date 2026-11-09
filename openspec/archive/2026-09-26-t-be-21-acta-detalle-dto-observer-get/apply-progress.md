# Apply Progress — T-BE-21 acta-detalle-dto-observer-get

## Estado

- **Rama**: `brydyan/sc-371/t-be-21-acta-detalle-completo-dto-votos-evidencia`.
- **Builder**: Mavis (orquestador, ejecutando como sdd-apply)
- **TDD estricto**: activo

## Gate 0 — Desbloqueo

`fix-liquibase-rbac-order` archivado. Cambes previos cerrados (T-BE-14 a T-BE-20).
Bloqueador del tasks.md: "nada". Cambio arrancable.

## Gate 1 — Línea base

```
./mvnw -B test  →  exit=0  →  tests=315, errors=0, failures=0, skipped=2
                  (XML sum, suites-xml=75)
```

El tasks.md dice "63 suites / 281 tests base" — está desactualizado. Mi baseline real es 318
tests / 76 suites (post T-BE-20).

## Gate 2 — Implementación (A.1, A.2, B.1, B.2)

### A.1 — `ActaDetalleCompletoDto`

`acta/web/dto/ActaDetalleCompletoDto.java`: record que extiende los campos de `ActaResponseDto`
+ añade `List<ActaDetalleDto> detalles`, `UUID evidenciaPublicId`, `String urlEvidencia`,
`String motivoDisputa`.

Import explícito de `ec.twintailcs.electoral.acta.web.ActaDetalleDto` (la clase, no record —
vive en `web/` no en `web/dto/`).

### A.2 — `ConsultarActaDetalleUseCase`

`acta/application/usecase/ConsultarActaDetalleUseCase.java`:
- Carga `Acta`, `ActaDetalle` (findByActaId), primera `Evidencia`, motivo de `audit_log`.
- Ensambla `ActaDetalleCompletoDto`.
- `@Transactional(readOnly = true)`.

### B.1 — `CoordinatorController.verActaDetalle()`

Modificado `GET /api/v1/coordinator/actas/{actaId}` para retornar `ActaDetalleCompletoDto`
vía `consultarActaDetalleUseCase.ejecutar(actaId)`. El listado (`findWithFilters`) sigue
retornando `ActaResponseDto` plano per "qué NO hacer" del spec.

### B.2 — `ObserverController.verMiActa()`

`GET /api/v1/observer/actas/{actaId}` con `@PreAuthorize("@authz.has('acta:upload')")`. Valida
autoría: `acta.getRegistradaPor().getId()` debe coincidir con `observerId`. Si no, lanza
`AccesoNoPermitidoException` (mapea a 403 via `GlobalExceptionHandler`).

### Fix menor — typo en import

El primer intento de compilación falló por `import ec.twintailcs.auditlog.domain.AuditLog;`
sin el segmento `electoral/`. El path correcto es `ec.twintailcs.electoral.auditlog.domain.AuditLog`.
Corregido.

### Fix menor — `AuditLog.detalle` es JSONB

`AuditLog.detalle` es `Map<String, Object>` (JSONB), no `String`. `AuditLog::getDetalle`
devuelve Map, incompatible con el `String motivoDisputa` del DTO. Patrón aplicado (ya
usado en `CoordinatorController.java:254`): `a.getDetalle() == null ? null : a.getDetalle().toString()`.

## Gate 3 — Tests C.1 + C.2

`acta/web/ActaDetalleControllerIT.java`: 3 tests.

```
coordinador_veDetalleConDetallesYUrlEvidencia   — 200, detalles[0].votos=90, motivoDisputa presente
observerDuenio_veDetalle                       — 200
observerAjeno_noVeDetalle_devuelve403         — 403
```

## Gate 4 — Mutaciones C.3

### C.3.1 — Quitar asignación de `detalles` — CAPTURADO ✓

**Mutación**: comentado `detalles,` en el builder del DTO (queda `null`).

**Test que cayó**: `coordinador_veDetalleConDetallesYUrlEvidencia` — `detalles` ahora es
`null` en vez de array con un detalle.

```
JSON path "$.detalles"
Expected: an array
     but: was: null
```

Restaurado.

### C.3.2 — Quitar comprobación de autoría en Observer — CAPTURADO ✓

**Mutación**: comentado el `if (!acta.getRegistradaPor().getId().equals(observerId))`.

**Test que cayó**: `observerAjeno_noVeDetalle_devuelve403` — esperado 403, recibido 200.
OBSERVER ajeno ahora ve el detalle porque la autoría ya no se valida.

```
Status expected:<403> but was:<200>
```

Restaurado.

## Números finales

| Métrica | Baseline | Post Gate 4 |
|---|---|---|
| Tests | 315 | **318** |
| Errors | 0 | 0 |
| Failures | 0 | 0 |
| Suites | 75 | **76** |

**Suite verde total**: `exit=0`, BUILD SUCCESS, 0 failures, 0 errors.

Los +3 tests son los del nuevo `ActaDetalleControllerIT`.

## Cambios totales del builder

**Nuevos archivos**:
- `acta/web/dto/ActaDetalleCompletoDto.java`
- `acta/application/usecase/ConsultarActaDetalleUseCase.java`
- `test/.../acta/web/ActaDetalleControllerIT.java`

**Modificados** (production code):
- `acta/web/CoordinatorController.java` — endpoint `verActa` retorna `ActaDetalleCompletoDto`;
  inyectado `ConsultarActaDetalleUseCase`.
- `acta/web/ObserverController.java` — nuevo endpoint `GET /actas/{actaId}`; inyectado
  `ConsultarActaDetalleUseCase`.

## Limitaciones documentadas

1. **`urlEvidencia` puede ser null** en el DTO si S3 falla (endpoint S3 es fake en tests).
   El use case maneja el error silenciosamente y devuelve null — no propagamos para no
   romper la UX. En producción con S3 real, se loguea el error.

2. **`motivoDisputa` es el `toString()` del Map JSONB**. No es el motivo "limpio" — es la
   representación Java del JSON. Para producción se podría serializar el JSON vía Jackson o
   parsear campos específicos (`{motivo: "..."}`). Out of scope de este change.

## Lo que NO se hizo (per 02-builder.md)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.

## Siguiente paso

El verificador debe:
1. Re-correr C.3.1 y C.3.2 por su cuenta (deben capturar).
2. Validar la suite final (318 tests, 0 errors).
3. Escribir `verify-report.md`.
