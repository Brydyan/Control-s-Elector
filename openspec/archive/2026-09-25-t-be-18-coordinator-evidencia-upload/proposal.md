# Proposal — T-BE-18: Subida de Evidencia Fotográfica por Coordinador

## Qué se construye
Endpoint en `CoordinatorController` para que los roles `COORDINATOR` y `MASTER` puedan adjuntar o actualizar la fotografía de respaldo de un acta electoral:
- Ruta: `POST /api/v1/coordinator/actas/{id}/imagen` (o `PUT`)
- Multipart: `@RequestPart("imagen") MultipartFile imagen`
- Persistencia en almacenamiento S3 / RustFS vía `EvidenciaService`.
- Registro de auditoría forense en `AuditLogService` indicando que la evidencia fue incorporada por vía de coordinación/contingencia.

## Por qué
- En el operativo de campo de Santa Elena 2027, es frecuente que el veedor se quede sin batería en su celular, sufra la rotura de su cámara o deba evacuar el recinto antes de completar la subida.
- El veedor transmite los votos por llamada y envía la fotografía física por WhatsApp a través de un tercero o coordinador zonal.
- Actualmente solo el veedor autor tiene permiso en `POST /api/v1/observer/actas/{id}/imagen`. Si el acta queda sin foto, el coordinador no tiene mecanismo para completar el expediente probatorio.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Endpoint | `POST /api/v1/coordinator/actas/{id}/imagen` | Subida de actas completas por coordinador |
| Seguridad | `@PreAuthorize("hasAnyRole('COORDINATOR', 'MASTER')")` | Acceso a observers ajenos |
| Validación | Validación de tipo MIME (WebP/JPEG/PNG) y tamaño (<10MB) | Modificación de votos |
| Transaccional | Compensación de almacenamiento y registro en `evidencia` | Eliminación física del acta |
| Auditoría | `AuditLogService.log("ACTA_EVIDENCIA_COORDINADOR", ...)` | Logs no persistentes |

## Criterio de aceptación

- [ ] `CoordinatorController` expone `POST /api/v1/coordinator/actas/{id}/imagen`.
- [ ] Requiere rol `COORDINATOR` o `MASTER`.
- [ ] Acepta archivo multipart bajo la clave `'imagen'`.
- [ ] Delega a `EvidenciaService.subirEvidencia(acta, imagen, usuarioId)`.
- [ ] Vincula la evidencia creada al acta electoral y persiste en base de datos.
- [ ] En caso de fallo en BD, ejecuta compensación transaccional eliminando el archivo de S3.
- [ ] Registra evento en `audit_log`.

## Fuera de alcance

- OCR automático de la imagen.

## Dependencias

- `T-BE-14` (S3 Storage y compensación transaccional).
- `EvidenciaService` y `CoordinatorController`.
