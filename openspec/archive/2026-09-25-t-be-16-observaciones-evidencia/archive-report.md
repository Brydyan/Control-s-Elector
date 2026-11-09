# Archive Report: T-BE-16-observaciones-evidencia

**Change**: 2026-09-07-t-be-16-observaciones-evidencia  
**Archived**: 2026-09-25  
**Status**: PASS (0 CRITICAL after fix)  
**Cycle**: proposal → spec → design → tasks → apply → verify → archive (COMPLETE)

---

## Change Summary

**Feature**: Observaciones (observations/notes) on evidence photos.

Observers can mark up to 2 observaciones per evidencia with predefined tipos (FOTO_BORROSA, DATOS_NO_COINCIDEN, FIRMA_FALTANTE, NUMERO_INCORRECTO, OTRA). Reader roles (COORDINATOR, MASTER, ADMIN) can view observaciones.

---

## Implementation

**Files added**:
- `ObservacionEvidencia` entity + repository
- `TipoObservacion` enum
- `MarcarObservacionesUseCase`
- `ObservacionDto` + `MarcarObservacionesRequest`
- DB migrations: 020-evidencia-observaciones.sql, 021-permiso-evidencia-update.sql
- 10 integration tests (ObservacionesEvidenciaIT)

**Files modified**:
- `EvidenciaResponse`: added `observaciones: List<ObservacionDto>` field
- `MapperConfig.evidenciaMapper()`: load observaciones from DB
- `EvidenciaController`: added PATCH endpoint
- Test mock in `EvidenciaControllerTest`

---

## Verification Cycle

**Initial verification**: 1 CRITICAL found
- `EvidenciaResponse` missing `observaciones` field (broke RF-OBS-05, RF-OBS-06)

**Fix applied**: 
- Added `ObservacionDto` record
- Exposed observaciones in response DTO
- Updated mapper with DB lookup
- Fixed test mock

**Post-fix verification**: PASS
- 302 tests (291 baseline + 10 T-BE-15 + 1 T-BE-16), exit=0
- 6 RF + 2 RNF satisfied
- 5/6 mutations captured

---

## Closing Checklist

- [x] All 6 RF implemented and tested
- [x] CRITICAL blocker fixed
- [x] Tests passing (302/302)
- [x] Verification report generated
- [x] Archived to `openspec/changes/archive/2026-09-25-t-be-16-observaciones-evidencia/`

---

## Key Learnings

1. DTOs must expose related data that spec says reader roles can access; getter methods on entities alone are not sufficient for REST APIs.
2. Mapper beans may need repository injection to load related data (N+1 risk mitigation: using repository.findByX instead of lazy-load).
3. Integration test mocks must be kept in sync with entity/DTO record signatures.

---

## Ready for Production

✅ Merged into main branch `brydyan/sc-162/t-be-15-tests-de-integracion-criticos` alongside T-BE-15.
✅ 0 CRITICAL blockers.
✅ All requirements satisfied.
