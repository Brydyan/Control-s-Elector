# Tasks — T-BE-18: Subida de Evidencia Fotográfica por Coordinador

## Fase A: Controlador y Delegación de Servicio

- [ ] **A.1** — En `CoordinatorController.java`:
  - Inyectar `EvidenciaService`.
  - Crear endpoint `POST /actas/{id}/imagen` consumiendo `multipart/form-data`.
  - Anotación `@PreAuthorize("hasAnyRole('COORDINATOR', 'MASTER')")`.
  - Anotación `@RequestPart("imagen") MultipartFile imagen`.
- [ ] **A.2** — En `EvidenciaService.java`:
  - Método `subirEvidenciaCoordinador(Long actaId, MultipartFile imagen, Long coordinadorId, HttpServletRequest request)`.
  - Auditoría forense explícita `EVIDENCIA_CONTINGENCIA_COORDINADOR`.

## Fase B: Tests de Integración y Seguridad

- [ ] **B.1** — Crear `CoordinatorControllerImagenIT.java`:
  - Test subida exitosa con rol COORDINATOR (201 Created).
  - Test rechazo 403 con rol OBSERVER.
  - Test rechazo 400 por clave incorrecta (`'file'`).
  - Test compensación transaccional S3 ante fallo en base de datos.
- [ ] **B.2** — Medir ejecución con `./mvnw test -Dtest=CoordinatorControllerImagenIT`.

## Verificación por Mutación

- **V.1** — Cambiar `@RequestPart("imagen")` a `@RequestPart("archivo")` -> test de clave `'imagen'` debe fallar.
- **V.2** — Quitar `@PreAuthorize` -> test de rechazo a OBSERVER debe fallar.
