# T-BE-17 — Prueba MockMvc RLS listado OBSERVER

**Cierra WARNING de T-BE-15-tests-integracion**: RF-TEST-04 Scenario 1 faltante

## Qué es

Test MockMvc que valida que OBSERVER B no ve actas de OBSERVER A en `GET /api/v1/observer/mis-actas` (RLS filtrada en HTTP).

## Por qué

T-BE-15 probó RLS a nivel:
- ✅ Transacción (RF-TEST-05): `current_setting('app.current_user_id')` retorna ID correcto
- ✅ JPQL (RF-TEST-06): filtro `registradaPorId` funciona

Faltaba:
- ❌ HTTP (Scenario 1): endpoint `/mis-actas` filtra respuesta correctamente

## Scopeactualmente

- 1 test MockMvc: `ObserverControllerRlsIT.observerBNoVeActasDelObserverA()`
- 1 mutación: remover `registradaPorId` guard
- ~80 líneas de código

## Archivos

- `proposal.md` — problema, alcance, criterios
- `design.md` — decisiones arquitectónicas (MockMvc vs Testcontainers, setup de datos)
- `specs/tests/spec.md` — requisitos funcionales y no funcionales
- `tasks.md` — tareas para implementación
- Salida: `backend/electoral-api/src/test/java/.../acta/web/ObserverControllerRlsIT.java`

## Siguientes pasos

1. Leer `proposal.md`, `design.md`, `specs/tests/spec.md`
2. Implementar según `tasks.md` (sdd-apply)
3. Verificar con `sdd-verify`
4. Archivar con `sdd-archive`
