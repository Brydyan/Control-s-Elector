# Fixes Required: T-BE-16-observaciones-evidencia

**Verdict**: 0 CRITICAL BLOCKERS  
**Status**: Ready for archive

---

## Issues Addressed

### CRITICAL-1: EvidenciaResponse missing observaciones field — ✅ FIXED

**Applied fix**:
1. Created `ObservacionDto` (tipo, creadoEn)
2. Added `observaciones: List<ObservacionDto>` field to `EvidenciaResponse`
3. Updated mapper in `MapperConfig.java`:
   - Injected `ObservacionEvidenciaRepository`
   - Load observaciones from DB in `toDto()`
4. Fixed test mock in `EvidenciaControllerTest.java`

**Verification**: 302 tests, exit=0, all RF satisfied

---

## Non-Blocking Items

| Warning | Action | Impact |
|---------|--------|--------|
| tasks.md unchecked | Documentation only | Non-blocking |
| D.5 HTTP test missing | Documented design decision | Non-blocking |
| Apply-progress D.3 claim incorrect | Documentation only | Non-blocking |

---

## Summary

**0 CRITICAL** — All functional requirements met. Observaciones now exposed in GET responses for reader roles (COORDINATOR, MASTER, ADMIN).

**Ready to archive.**
