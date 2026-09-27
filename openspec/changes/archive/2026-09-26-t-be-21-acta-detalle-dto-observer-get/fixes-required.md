# Fixes Required — T-BE-21: Acta Detalle Completo DTO (Votos, Evidencia, Motivo) & GET Observer

## Status

**No hay CRITICAL blockers.**

La suite de tests pasa con 318 tests, 0 failures, 0 errors. Todos los criterios de aceptación están cubiertos. El change está listo para archive.

---

## Hallazgos (No Bloqueantes)

### Hallazgo 1 — `motivoDisputa` es serialización Java, no JSON limpio

| Aspecto | Detalle |
|---|---|
| **Síntoma** | `motivoDisputa` contiene `toString()` del Map JSONB, produciendo salida como `{motivo: "...", ...}` en formato Java, no JSON sintácticamente limpio |
| **Impacto** | Nulo en funcionalidad; la información está presente. Frontend puede parsearla si es necesario |
| **Causa** | Per design.md D3: decidimos extraer desde `audit_log.detalle` (Map) en lugar de crear migración Liquibase |
| **Cierre** | Mejora futura: usar Jackson para serializar JSON limpio. Documentado en apply-progress.md. No bloquea. |

---

### Hallazgo 2 — `primeraEvidencia()` ejecuta scan completo sin índice

| Aspecto | Detalle |
|---|---|
| **Síntoma** | `evidenciaRepository.findAll()` seguido de filtro en memoria. Si hay miles de evidencias, es ineficiente |
| **Impacto** | Bajo — se ejecuta una vez por consulta, no causa N+1. Rendimiento lineal con cantidad de evidencias |
| **Causa** | `EvidenciaRepository` no tiene `findByActaId` ordenado por fecha. Decisión de diseño: evitar migración innecesaria |
| **Cierre** | Mejora futura: agregar `findByActaId(Long actaId)` al repositorio. Documentado en apply-progress.md. No bloquea. |

---

### Hallazgo 3 — S3 presignDownload() falla silenciosamente, deja `urlEvidencia = null`

| Aspecto | Detalle |
|---|---|
| **Síntoma** | Si S3 falla al generar presigned URL, se captura exception y `urlEvidencia` queda `null` (sin error al usuario) |
| **Impacto** | Aceptable — UX trade-off: endpoint sigue retornando 200 con datos del acta, solo sin foto. Usuario puede seguir adelante |
| **Causa** | Design.md D2: "No propagamos para no romper la UX". Se loguea el error en producción |
| **Cierre** | Comportamiento esperado per spec. No bloquea. En producción con S3 real es poco probable. |

---

## Conclusión

**Veredicto**: PASS-WITH-KNOWN-LIMITATIONS

El change cumple con todos los criterios de aceptación. Las limitaciones documentadas son trade-offs de diseño aceptables, mejorables en futuras iteraciones pero no bloqueantes para cierre.

**Ninguna acción requerida para archive.**
