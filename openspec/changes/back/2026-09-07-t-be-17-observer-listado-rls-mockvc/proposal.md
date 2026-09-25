# Proposal: T-BE-17 — Prueba MockMvc RLS listado OBSERVER

**Origen**: WARNING de verificación T-BE-15: RF-TEST-04 Scenario 1 faltante

**Duración estimada**: 1–2 días  
**Rama**: `brydyan/sc-161/t-be-17-observer-listado-rls-mockvc`, desde `develop`

---

## Problema

En T-BE-15-tests-integracion, la verificación encontró:

> **RF-TEST-04 Scenario 1** — `OBSERVER B` consulta `GET /api/v1/observer/mis-actas` → acta de `OBSERVER A` no aparece (RLS filtrada en HTTP)

El test faltante es un MockMvc integration test que prueba:
1. Dos OBSERVERs con asignaciones a JRVs **diferentes**
2. OBSERVER A registra acta en JRV-1
3. OBSERVER B (solo asignado a JRV-2) llama `GET /api/v1/observer/mis-actas`
4. Verifica: la acta de A NO aparece en la respuesta de B

**Mitigante**: RLS ya fue probado a nivel transaccional (RF-TEST-05) y JPQL (RF-TEST-06). Este test cierra la cobertura HTTP end-to-end.

---

## Alcance

### Qué sí
- Test MockMvc `ObserverControllerRlsIT.observerBNoVeActasDelObserverA()`
- Setup de datos: 2 OBSERVERs, 2 JRVs, 2 actas, asignaciones distintas
- Spring Boot full test context + `@WithMockUser` + `SecurityContextHolder`
- Verificación: acta de A no aparece en respuesta de B

### Qué no
- No tocar el endpoint `/mis-actas` (ya existe y funciona)
- No refactorizar RLS o repository
- No cambiar spec de T-BE-15

---

## Criterios de aceptación

1. **Test existe** en `backend/electoral-api/src/test/java/.../acta/web/ObserverControllerRlsIT.java`
2. **Test pasa** contra código actual (291 → 292 tests)
3. **Mutación**: quitar `registradaPorId` del filtro → test cae
4. **Mutación**: usar COORDINATOR (no OBSERVER) → test pasa (no debería)
5. **Documentado**: en `apply-progress.md`, números medidos

---

## Decisiones de Arquitectura

- **Por qué MockMvc y no MockBean**: RFC-TEST-04 Scenario 1 es HTTP-level; SecurityContext solo se propaga en request real
- **Por qué 2 OBSERVERs distintos**: prueba que RLS isola rows por usuario, no por rol
- **Por qué `observer_jrv_asignacion`**: los OBSERVERs son totalmente aislados (solo ven sus JRVs asignadas)

---

## Referencias

- **T-BE-15**: `openspec/changes/back/2026-09-07-t-be-15-tests-integracion/`
  - Verify report: WARNING 1 (listado scenario)
  - RF-TEST-04 spec: dos scenarios (2 es cubierto, 1 falta)
- **ObserverController**: `backend/electoral-api/src/main/java/.../acta/web/ObserverController.java:98`
  - GET `/api/v1/observer/mis-actas` + `observerId()` + `findWithFilters(..., observerId)`
- **RLS Implementation**: RlsInterceptor, `current_setting('app.current_user_id')` en transacción

---

## Salida esperada

```
backend/electoral-api/src/test/java/ec/twintailcs/electoral/acta/web/ObserverControllerRlsIT.java
  - observerBNoVeActasDelObserverA() — 1 test
  - 1 mutación (C.1: remove registradaPorId filter)

apply-progress.md
  - Gate 0: 291 tests
  - Gate 1: 292 tests (+1)
  - Mutation: [nombre del test que cayó]
```

**Status**: Ready for SDD apply.
