# Proposal — T-BE-20: Generación de Expediente de Impugnación CNE en PDF

## Qué se construye
Servicio y endpoint para generar el **Expediente Probatorio Oficial de Impugnación CNE** en formato PDF descargable para actas en estado `EN_OBSERVACION` o `EN_DISPUTA`:
- Ruta: `GET /api/v1/coordinator/actas/{id}/expediente-pdf`
- Retorna: `application/pdf` con encabezado `Content-Disposition: attachment; filename="expediente-jrv-{numero}-{cargo}.pdf"`
- Contenido del documento (1 a 2 páginas estructuradas):
  1. **Encabezado Jurídico**: "Junta Provincial Electoral de Santa Elena · Elecciones Seccionales 2027 · Expediente Técnico de Impugnación".
  2. **Identificación de la Mesa**: Cantón, Parroquia, Recinto Electoral, Número de JRV y Dignidad escrutada.
  3. **Cuadro Comparativo de Votos**: Tabla de candidatos, listas, votos digitados, blancos, nulos, total de sufragantes y cálculo explícito del descuadre aritmético.
  4. **Respaldo Fotográfico Original**: Fotografía del acta física embebida en alta resolución obtenida del almacenamiento S3.
  5. **Causal y Observación Legal**: Detalle del motivo de disputa o inconsistencia redactado por el coordinador.
  6. **Sello Forense Digital**: Hash SHA-256 del documento, usuario generador, fecha y hora UTC exacta.

## Por qué
- Durante la noche electoral y los días posteriores, los delegados jurídicos defienden los votos en las audiencias públicas de escrutinio del CNE.
- Los abogados no pueden ingresar con laptops al atril de la junta; requieren el documento impreso o digital con validez probatoria inmediata para presentar el reclamo legal ante el CNE o el Tribunal Contencioso Electoral (TCE).

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Endpoint | `GET /api/v1/coordinator/actas/{id}/expediente-pdf` | Generación de reportes generales de resultados |
| Formato | PDF binario generado en memoria con OpenPDF | Word o Excel editable |
| Contenido | Datos de acta, cuadro de votos, imagen física, motivo legal y hash | Firma electrónica de token criptográfico |
| Permisos | `@PreAuthorize("hasAnyRole('COORDINATOR', 'MASTER', 'ADMIN')")` | Descarga pública anónima |

## Criterio de aceptación

- [ ] Endpoint genera PDF válido para actas existentes.
- [ ] Incorpora la imagen del acta descargándola internamente del bucket S3.
- [ ] Detalla el cálculo matemático de inconsistencia o el motivo de observación.
- [ ] Formato visual limpio con tipografía formal apta para presentación judicial.
- [ ] Retorna HTTP 404 si el acta no existe y 403 si el rol no está autorizado.

## Dependencias

- Dependencia Maven `com.github.librepdf:openpdf`.
- `EvidenciaService` (para obtener stream de imagen S3).
- `ActaRepository`.
