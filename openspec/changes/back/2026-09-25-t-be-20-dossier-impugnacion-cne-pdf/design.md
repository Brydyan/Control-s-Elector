# Design — T-BE-20: Generación de Expediente de Impugnación CNE en PDF

---

## D1 — Biblioteca de Generación PDF: OpenPDF

**Decisión**: Utilizar `com.github.librepdf:openpdf:2.0.3` (licencia LGPL/MPL libre, compatible con Spring Boot y Java 21):
```xml
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.3</version>
</dependency>
```

**Alternativa descartada — iText 7/8 comercial**: Exige licencia comercial privativa (AGPL o de pago), inviable para proyectos libres o de despliegue institucional.

---

## D2 — Arquitectura del Servicio `ExpedientePdfService`

**Decisión**: 
- Clase `ExpedientePdfService` en paquete `ec.twintailcs.electoral.acta.application`:
  - Recibe `actaId`.
  - Carga el acta con sus detalles (`ActaDetalle`), candidatos, partidos y entidad `Evidencia`.
  - Descarga el stream de bytes de la foto desde S3 / RustFS.
  - Compone el documento usando tablas de OpenPDF (`PdfPTable`), estilos limpios en blanco y negro con acentos institucionales, y escala la imagen al 45% del ancho de página con borde y relación de aspecto preservada.
  - Retorna `byte[]` que se transmite por streaming en el controlador sin guardar archivos temporales en disco.

---

## D3 — Sello de Integridad y Trazabilidad

**Decisión**: 
- Se computa un hash SHA-256 de los bytes generados y se estampa en el pie de página del documento junto al identificador del coordinador y la fecha/hora UTC.
- Se registra un evento en `AuditLogService` (`EXPEDIENTE_PDF_DESCARGADO`).
