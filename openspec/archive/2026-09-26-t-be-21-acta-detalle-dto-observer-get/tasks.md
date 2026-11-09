# Tasks — T-BE-21: Acta Detalle Completo DTO (Votos, Evidencia, Motivo) & GET Observer

## Fase A: DTOs y Caso de Uso de Detalle

- [x] **A.1** — Crear `ActaDetalleCompletoDto` en `backend/electoral-api/src/main/java/ec/twintailcs/electoral/acta/web/dto/`:
  - Incluir campos de `ActaResponseDto`.
  - Añadir `List<ActaDetalleDto> detalles`.
  - Añadir `String urlEvidencia` y `UUID evidenciaPublicId`.
  - Añadir `String motivoDisputa`.
- [x] **A.2** — Crear o extender caso de uso `ConsultarActaDetalleUseCase`:
  - Cargar entidad `Acta` con lock compartido / read-only.
  - Cargar `actaDetalleRepository.findByActaId(actaId)` y mapear a `ActaDetalleDto`.
  - Buscar evidencia activa en `EvidenciaRepository` y llamar `EvidenciaService.presignDownload(publicId)`.
  - Si `estado == EN_DISPUTA`, consultar último motivo en `audit_log`.
  - Ensamblar `ActaDetalleCompletoDto`.

## Fase B: Endpoints en Controllers

- [x] **B.1** — En `CoordinatorController.java`:
  - Modificar `verActa(@PathVariable Long actaId)` para retornar `ResponseEntity<ActaDetalleCompletoDto>`.
- [x] **B.2** — En `ObserverController.java`:
  - Añadir endpoint `GET /api/v1/observer/actas/{actaId}` con `@PreAuthorize("hasAuthority('acta:upload')")`.
  - Validar autoría: `acta.getRegistradaPor()` debe coincidir con `CurrentUser.staticId()` (o lanzar `AccessDeniedException`).

## Fase C: Tests de Integración y Unitarios

- [x] **C.1** — En `CoordinatorControllerIT`:
  - Test `GET /api/v1/coordinator/actas/{id}` retorna HTTP 200 con `detalles` poblados y `urlEvidencia`.
  - Test `motivoDisputa` presente cuando el acta está en `EN_DISPUTA`.
- [x] **C.2** — En `ObserverControllerIT`:
  - Test `GET /api/v1/observer/actas/{id}` retorna HTTP 200 para el veedor dueño.
  - Test `GET /api/v1/observer/actas/{id}` retorna HTTP 403 cuando otro veedor intenta consultar.
- [x] **C.3** — Verificación por mutación, ejecutada por quien implementa:
  - Quitar la asignación de `detalles` en el builder del DTO y comprobar que **cae** el test de votos individuales. Anotá en `apply-progress.md` **el nombre del test que cayó**.
  - Quitar la comprobación de autoría en el endpoint de Observer y comprobar que **cae** el test de 403 Forbidden. Anotá en `apply-progress.md` **el nombre del test que cayó**.

---

## Qué NO hacer en esta fase

- NO alterar la query ni el DTO de `GET /api/v1/coordinator/actas` (el listado debe mantenerse plano sin colecciones para evitar N+1).
- NO agregar migraciones Liquibase (usar las tablas existentes `acta_detalle`, `evidencia` y `audit_log`).
- NO devolver URLs de S3 permanentes; siempre usar URLs prefirmadas con TTL.

---

## Compuertas antes de dar por terminada la fase

```bash
cd backend/electoral-api
./mvnw -B clean test
```
- `63 suites / 281 tests base + nuevos tests de C.1 y C.2`
- `0 ERROR / 0 failure`
