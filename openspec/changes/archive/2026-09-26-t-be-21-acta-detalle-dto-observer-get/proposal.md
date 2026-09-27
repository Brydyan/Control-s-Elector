# Proposal — T-BE-21: Acta Detalle Completo DTO (Votos, Evidencia, Motivo) & GET Observer

## Qué se construye
Ampliación de los contratos REST de escrutinio para resolver el bloqueo de datos en frontend, proveyendo un DTO enriquecido para la consulta detallada de un acta individual:

1. **DTO Enriquecido para Consulta Unitaria (`ActaDetalleCompletoDto`)**:
   - Campos del acta base: `id`, `jrvId`, `cargoId`, `registradaPorId`, `estado`, `totalSufragantes`, `votosBlancos`, `votosNulos`, `fechaRegistro`, `motivoAnulacion`, `nombreCargo`, `numeroJrv`, `totalVotosCandidatos`.
   - **Votos individuales por candidato**: `List<ActaDetalleDto> detalles` (`candidatoId`, `votos`) cargados desde `actaDetalleRepository.findByActaId(actaId)`.
   - **Evidencia fotográfica enlazada**: `String urlEvidencia` (URL prefirmada generada por `EvidenciaService`) y `UUID evidenciaPublicId` si existe evidencia física registrada.
   - **Motivo de inconsistencia**: `String motivoDisputa` recuperado cuando el acta se encuentra en estado `EN_DISPUTA`.
2. **Nuevo Endpoint en `ObserverController`**:
   - `GET /api/v1/observer/actas/{actaId}`: permite al veedor (`OBSERVER`) autenticado consultar el detalle completo de un acta registrada por él mismo para cotejo y subsanación en campo (`T-FE-14`).
3. **Enriquecimiento del Endpoint de Detalle en `CoordinatorController`**:
   - `GET /api/v1/coordinator/actas/{actaId}`: retorna `ActaDetalleCompletoDto` en lugar del plano `ActaResponseDto`.
   - Permite al coordinador (`COORDINATOR`, `MASTER`, `ADMIN`) visualizar los votos reales por candidato en el visor Split-View 50/50 y cargar la foto física real sin depender de la columna eliminada `storage_url`.

## Por qué
- En PR $\gamma$ se aplanó `ActaResponseDto` para prevenir problemas N+1 en listados masivos, pero se eliminó el desglose de votos de candidatos, dejando únicamente la sumatoria `totalVotosCandidatos`. Esto obligó al frontend a hardcodear `votos: 0` en el detalle.
- La columna `acta.storage_url` fue eliminada en la migración `019-drop-acta-storage-url.sql` al migrar a la tabla `evidencia`. `ActaResponseDto` quedó huérfana de referencia visual, impidiendo que el visor del coordinador y del veedor muestre la foto.
- El veedor no tenía ningún endpoint `GET /actas/{id}` bajo `/api/v1/observer/`, bloqueando la implementación de la pantalla móvil de corrección de actas disputadas ([`T-FE-14`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/openspec/changes/front/2026-09-26-t-fe-14-observer-correccion-acta/)).

## Alcance

| Elemento | Incluye | No incluye |
|---|---|---|
| **DTOs** | `ActaDetalleCompletoDto` con lista de votos, URL prefirmada y motivo | Alterar el DTO plano de listado `GET /coordinator/actas` (mantiene rendimiento) |
| **Observer API** | `GET /api/v1/observer/actas/{actaId}` con filtro de autoría | Permitir al veedor ver actas de otros recintos |
| **Coordinator API** | `GET /api/v1/coordinator/actas/{actaId}` retornando DTO completo | Modificación de endpoints de aprobación / disputa |
| **Evidencias** | Generación de URL prefirmada (15 min) integrada al DTO | Subida de archivos (ya cubierto por T-BE-14 y T-BE-18) |

## Criterios de Aceptación

- [ ] `GET /api/v1/coordinator/actas/{id}` retorna HTTP 200 con `ActaDetalleCompletoDto` conteniendo la lista de candidatos y sus votos exactos.
- [ ] Si el acta tiene evidencia física asociada, `urlEvidencia` contiene la URL prefirmada válida; si no tiene foto, devuelve `null` sin error.
- [ ] Si el acta está `EN_DISPUTA`, `motivoDisputa` expone la nota registrada por el coordinador.
- [ ] `GET /api/v1/observer/actas/{id}` permite al veedor que registró el acta obtener su detalle completo.
- [ ] Si un veedor intenta acceder al ID de un acta registrada por otro veedor, recibe HTTP 403 Forbidden o 404 Not Found.
- [ ] El endpoint de listado paginado `GET /api/v1/coordinator/actas` conserva su DTO ligero `ActaResponseDto` sin regresión N+1.
- [ ] Suite completa de backend pasa en VERDE con tests de integración dedicados.

## Fuera de Alcance
- Modificaciones en la base de datos o migraciones Liquibase (se reutilizan las tablas existentes `acta_detalle`, `evidencia`, `audit_log`).
