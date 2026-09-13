# Archive report — T-BE-12 cierre de coordinación

## Resultado

T-BE-12 cerrado. Change archivado en plano en
`openspec/changes/archive/2026-09-07-t-be-12-cierre-coordinacion/`.

## Cifras finales (medidas en `4e1d89d`)

- `CoordinatorControllerTest`: 28 tests, 0 errors, 0 failures, 0 skipped.
- Suite backend completa: 53 suites, 215 tests, 0 errors, 0 failures, 1 skipped.
- `mvn -B spotless:check`: exit 0.

## Garantías verificadas por mutación (ver `verify-report.md`)

| ID | Garantía | Test que cayó |
|---|---|---|
| RF-COORD-01 | Sólo COORDINATOR/MASTER acceden al módulo | `endpoints_asAdmin_returns403` |
| RF-COORD-02 | Sólo MASTER anula (mediante permiso `acta:anular`) | `anular_asCoordinator_returns403` |
| RF-COORD-03 | Sólo se anulan actas APROBADAS | `anular_asMaster_actaEnviada_retorna409` |
| RF-COORD-04 | Máquina de estados respetada | `aprobar_actaEnDisputa_retorna409` |
| RF-COORD-05 | Motivo obligatorio al disputar/anular | `disputar_sinMotivo_returns400` |
| RF-COORD-07 | Filtro por cantón funciona | `listarActas_conFiltroCantonId` (con acta control) |

RF-COORD-06 (auditoría) verificada por existencia — el test ya contaba filas de
`audit_log` antes y después de las acciones.

## Iteración del contrato de mutación

La verificación requirió tres corridas independientes del subagente `sdd-verify`:

1. **Corrida 1** abortó con CRITICAL: la task B.1 proponía mutar
   `@PreAuthorize("hasRole('MASTER')")` pero el stack de hardening #68-#71
   había migrado el módulo a permisos por string. Arquitecto arregló el
   contrato en commit `513812a`.
2. **Corrida 2** abortó con CRITICAL: el test
   `listarActas_conFiltroCantonId` no distinguía filtro aplicado de no
   aplicado porque sólo había un acta. Arquitecto añadió una segunda acta
   control en otro cantón en commit `4e1d89d`.
3. **Corrida 3** verdict: **PASS**. Las 6 mutaciones cayeron sus tests
   esperados.

## Decisiones del arquitecto

- **D1**: el código es la autoridad; `T-BE-12.md` se corrigió cuando
  contradecía al controller. Renombrado a `COMPLETADO-T-BE-12.md`.
- **D2**: renombrar a `COMPLETADO-` solo después de las mutaciones, no
  antes. La casilla sin medición es exactamente el defecto T-IN-11.
- **D3**: el filtro por cantón se implementó (no se retiró la promesa
  de `backend.md`). Ver `apply-progress.md` §C.2.

## Desviaciones de la propuesta original

La propuesta original (2026-09-07) asumía la suite backend en rojo
(78 ERROR). Esa medición quedó obsoleta por los PRs #68-#71 y
subsecuentes. La Fase 1 arrancó el 2026-09-13 con la suite ya verde
(53/211/0/0/1), lo que cambió A.2 (línea base PASS, no ERROR) y
desbloqueó las mutaciones.

## Próximo paso (no parte de este change)

- T-BE-13 (Dashboard) puede arrancar ahora: la suite backend está
  verde y verificada.
- T-BE-14, T-BE-15, T-BE-16 siguen pendientes (ver
  `documents/task-control/backendtask/`).
- Plan vertical COORDINATOR sigue con Fase 2 (bandeja), Fase 3
  (detalle), Fase 4 (decisiones), Fase 5 (resiliencia), Fase 6
  (validación final).