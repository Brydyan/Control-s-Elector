# Spec — T-BE-20: Generación de Expediente de Impugnación CNE en PDF

## Escenarios

### S1 — Generación de PDF con imagen y tabla de votos

```gherkin
GIVEN    acta existente con candidatos, votos e imagen de evidencia cargada
AND      usuario con rol COORDINATOR autenticado
WHEN     solicita GET /api/v1/coordinator/actas/{id}/expediente-pdf
THEN     backend responde HTTP 200 OK con Content-Type application/pdf
AND      el payload comienza con los bytes mágicos %PDF-
AND      contiene los textos de cantón, número de JRV y totales de votos
AND      registra evento en audit_log
```

### S2 — Generación de PDF para acta sin evidencia adjunta

```gherkin
GIVEN    acta en disputa que no posee archivo de foto adjunto
WHEN     se solicita el expediente PDF
THEN     backend genera el documento PDF exitosamente
AND      sustituye la imagen por un recuadro de advertencia: "[SIN RESPALDO FOTOGRÁFICO EN EL SISTEMA]"
```

### S3 — Rechazo de acceso a usuarios sin rol de coordinación

```gherkin
GIVEN    usuario con rol OBSERVER o GUEST
WHEN     intenta descargar el expediente PDF
THEN     Spring Security rechaza la petición con HTTP 403 Forbidden
```

## Garantías

- **G1:** Generación en memoria sin dejar archivos basura en `/tmp`.
- **G2:** Documento PDF compatible con lectores estándar (Adobe Acrobat, Chrome PDF Viewer).
- **G3:** Trazabilidad de descarga en `audit_log`.
