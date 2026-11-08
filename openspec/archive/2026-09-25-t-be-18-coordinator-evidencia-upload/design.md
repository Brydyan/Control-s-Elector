# Design — T-BE-18: Subida de Evidencia Fotográfica por Coordinador

---

## D1 — Ubicación del Endpoint y Verbo HTTP

**Decisión**: Exponer `POST /api/v1/coordinator/actas/{id}/imagen` dentro de `CoordinatorController`:
```java
@PreAuthorize("hasAnyRole('COORDINATOR', 'MASTER')")
@PostMapping(value = "/actas/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<EvidenciaResponseDto> subirEvidenciaCoordinador(
    @PathVariable Long id,
    @RequestPart("imagen") MultipartFile imagen,
    HttpServletRequest request) { ... }
```

**Alternativa descartada — Usar el endpoint de observer abriendo permisos**: Mezclar el contexto de observador con el de coordinador rompe la semántica de auditoría y auditaría al coordinador como si fuera el veedor de campo.

---

## D2 — Reutilización del Flujo de Almacenamiento Seguro

**Decisión**: Invocar directamente `evidenciaService.subirEvidencia(actaId, imagen, currentUser.getId(), request)` reutilizando toda la infraestructura probada de `T-BE-14`:
- Verificación de Content-Type permitido (`image/webp`, `image/jpeg`, `image/png`).
- Almacenamiento en bucket S3/RustFS.
- `TransactionSynchronization` para rollback automático en caso de excepción en base de datos.
- Generación de registro en tabla `evidencia`.

---

## D3 — Registro de Auditoría Específico

**Decisión**: Registrar acción diferenciada en `AuditLogService`:
- Tipo: `EVIDENCIA_CONTINGENCIA_COORDINADOR`
- Metadatos: `actaId`, `coordinadorId`, `nombreArchivoOriginal`, `tamanoBytes`, `sha256Hash`.

**Alternativa descartada — No auditar**: En litigios electorales, el CNE exige constancia de quién incorporó cada documento al expediente.
