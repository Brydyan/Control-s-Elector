# Tasks — T-BE-20: Generación de Expediente de Impugnación CNE en PDF

## Fase A: Dependencia y Servicio Generador

- [ ] **A.1** — Agregar dependencia OpenPDF a `backend/electoral-api/pom.xml`:
  ```xml
  <dependency>
      <groupId>com.github.librepdf</groupId>
      <artifactId>openpdf</artifactId>
      <version>2.0.3</version>
  </dependency>
  ```
- [ ] **A.2** — Implementar `ExpedientePdfService`:
  - Método `generarExpedientePdf(Long actaId, Long usuarioId): byte[]`.
  - Construcción de documento: metadatos, tabla comparativa, foto del S3 y pie forense.

## Fase B: Controlador y Headers HTTP

- [ ] **B.1** — En `CoordinatorController.java`:
  - Endpoint `GET /actas/{id}/expediente-pdf`.
  - Headers `Content-Type: application/pdf` y `Content-Disposition: inline; filename=...`.
  - Anotación `@PreAuthorize("hasAnyRole('COORDINATOR', 'MASTER', 'ADMIN')")`.

## Fase C: Tests de Integración

- [ ] **C.1** — Crear `ExpedientePdfIT.java`:
  - Test descarga de PDF verificando header `%PDF-`.
  - Test inclusión de datos de JRV.
  - Test rechazo 403 a OBSERVER.
- [ ] **C.2** — Medir ejecución con `./mvnw test -Dtest=ExpedientePdfIT`.

## Verificación por Mutación

- **V.1** — Cambiar el Content-Type a `text/plain` -> test de tipo MIME debe fallar.
- **V.2** — Quitar `@PreAuthorize` -> test de seguridad debe fallar.
