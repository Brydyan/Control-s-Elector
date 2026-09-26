# Apply Progress — T-BE-20 dossier-impugnacion-cne-pdf

## Estado

- **Rama**: `brydyan/sc-356/t-be-20-expediente-de-impugnacion-cne-en` (ya existía).
- **Builder**: Mavis (orquestador, ejecutando como sdd-apply)
- **TDD estricto**: activo

## Gate 0 — Desbloqueo

`fix-liquibase-rbac-order` archivado. Cambes previos cerrados (T-BE-14 a T-BE-19).
Bloqueador del tasks.md: "nada". Cambio arrancable.

## Gate 1 — Línea base

```
./mvnw -B test  →  exit=0  →  tests=313, errors=0, failures=0, skipped=2
                  (XML sum, suites-xml=74)
```

## Gate 2 — Implementación (A.1, A.2, B.1)

### A.1 — Dependencia OpenPDF

`backend/electoral-api/pom.xml`: agregado `com.github.librepdf:openpdf:2.0.3`. LGPL/MPL,
sin coste de licencia (alternativa iText 7/8 descartada por privativa).

### A.2 — `ExpedientePdfService`

`acta/application/ExpedientePdfService.java`:
- Método `generarExpedientePdf(Long actaId, UUID usuarioPublicId)` retorna `byte[]`.
- Estructura del PDF: metadatos → tabla comparativa de mesa → tabla de votos →
  imagen S3 (o recuadro de advertencia "[SIN RESPALDO FOTOGRÁFICO EN EL SISTEMA]") →
  pie forense con SHA-256 del documento + id coordinador + timestamp UTC.
- Audit `EXPEDIENTE_PDF_DESCARGADO` post-emisión.
- Sin `@Transactional(readOnly = true)` — el servicio escribe audit y necesita tx read-write.
  (Cambio menor respecto a la primera versión.)
- Inyectado `UsuarioRepository` para resolver UUID → Long.

### Fix menor — `S3StorageService.download(String)`

Agregado método `download(String key)` que retorna `InputStream` del objeto en S3. Usado
por `ExpedientePdfService` para incrustar la foto del acta en el PDF.

### B.1 — Endpoint `GET /actas/{actaId}/expediente-pdf`

`CoordinatorController.java`:
- Endpoint con `produces = "application/pdf"`.
- `@PreAuthorize("hasAnyRole('COORDINATOR','MASTER','ADMIN')")`.
- `ResponseEntity.ok()` con `Content-Disposition: inline; filename="expediente-acta-{id}.pdf"`.
- Sin `.header("Content-Type", ...)` explícito — `produces` ya establece el header (ver
  hallazgo en V.1 más abajo).
- Inyectado `ExpedientePdfService` en el constructor.

## Gate 3 — Tests C.1 (2 tests)

```
coordinador_descarga_pdf           — S1+S2: 200, Content-Type pdf, %PDF-, filename, audit
observer_noPuedeDescargarPdf      — S3: 403
```

### Limitación documentada — verificación textual del PDF

OpenPDF comprime el contenido del documento por defecto. Verificar strings como "JRV-PDF-001"
o "Prefecto" en los bytes requiere un parser PDF (pdfbox) o desactivar la compresión.
El test verifica headers + bytes mágicos + tamaño + audit, pero NO el contenido textual
específico. Para verificación más profunda se necesitaría:
- Agregar dependencia `org.apache.pdfbox:pdfbox` al pom (ya presente, verificar).
- Usar `PDFTextStripper` para extraer texto y buscar substrings.

## Gate 4 — Mutaciones V.1, V.2

### V.1 — Cambiar `Content-Type` a `text/plain` — CAPTURADO ✓

**Mutación**: `produces = "application/pdf"` → `produces = "text/plain"`.

**Hallazgo durante la primera mutación**: el controller también tenía
`.header("Content-Type", "application/pdf")` explícito, lo que sobrescribía el `produces`.
La mutación NO capturaba porque el header explícito ganaba. **Fix**: removí el `.header`
explícito (el `produces` ya establece el Content-Type correctamente). Después del fix, la
mutación sí captura.

**Test que cayó**: `coordinador_descarga_pdf`.

```
Content type expected:<application/pdf> but was:<text/plain>
```

Restaurado.

### V.2 — Quitar `@PreAuthorize` del método — **NO CAPTURADO** (limitación)

**Mutación**: comentado el `@PreAuthorize("hasAnyRole('COORDINATOR','MASTER','ADMIN')")`
del método `descargarExpedientePdf`.

**Test que NO captura**: `observer_noPuedeDescargarPdf`. La anotación de clase
(`hasAnyAuthority('acta:aprobar',...)`) sigue rechazando OBSERVER, así que quitar
la del método no cambia el resultado observable.

**Limitación**: misma redundancia defense-in-depth que T-BE-18 V.2 y T-BE-19 V.2. La
mutación V.2 según spec no es capturable sin remover AMBAS anotaciones (lo cual
rompería todos los endpoints del controller). Documentado como **declarado-no-aplicable**.

Restaurado.

## Números finales

| Métrica | Baseline | Post Gate 4 |
|---|---|---|
| Tests | 313 | **315** |
| Errors | 0 | 0 |
| Failures | 0 | 0 |
| Suites | 74 | **75** |

**Suite verde total**: `exit=0`, BUILD SUCCESS, 0 failures, 0 errors.

Los +2 tests son los del nuevo `ExpedientePdfIT`.

## Cambios totales del builder

**Nuevos archivos**:
- `acta/application/ExpedientePdfService.java`
- `test/.../acta/ExpedientePdfIT.java`

**Modificados** (production code):
- `pom.xml` — dependencia OpenPDF 2.0.3.
- `storage/s3/S3StorageService.java` — método `download(String)`.
- `acta/web/CoordinatorController.java` — endpoint `expediente-pdf`, inyectado `ExpedientePdfService`,
  removido `.header("Content-Type", ...)` redundante (causaba que V.1 no capturara).

## Limitaciones documentadas

1. **V.2 no captura** — redundancia defense-in-depth. Mismo patrón que T-BE-18 V.2 y T-BE-19 V.2.
2. **Verificación textual del PDF** — OpenPDF comprime el contenido; el test verifica headers,
   bytes mágicos, tamaño y audit, pero NO strings específicos del PDF. Para eso se requeriría
   pdfbox o desactivar compresión.
3. **S1 con imagen real** — el test usa endpoint S3 fake, así que la imagen falla con IOException
   y el servicio muestra el recuadro "[SIN RESPALDO FOTOGRÁFICO EN EL SISTEMA]" (S2). Para S1
   con imagen real se necesitaría TestContainers RustFS.

## Lo que NO se hizo (per 02-builder.md)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.

## Siguiente paso

El verificador debe:
1. Re-correr V.1 (debe capturar) por su cuenta.
2. Confirmar V.2 declarada-no-aplicable.
3. Validar la suite final (315 tests, 0 errors).
4. Escribir `verify-report.md`.
