# Specification — T-FE-17: Centro de Reportes y Estadísticas de Coordinación (COORDINATOR)

## Requirement: Presentación del Centro de Reportes
El sistema debe renderizar el panel de métricas operativas para usuarios con rol COORDINATOR o MASTER.

### Scenario: Acceso al centro de reportes
- **GIVEN** que el usuario COORDINATOR navega a `/coordinator/reportes`
- **WHEN** se completa la carga de los servicios de dashboard y actas
- **THEN** se muestra el banner de progreso provincial con el porcentaje global y total de JRVs
- **AND** se muestra el desglose de actas en Enviadas, En Disputa, Corregidas, Aprobadas y Anuladas.

---

## Requirement: Descarga de Expediente de Impugnación CNE en PDF
El sistema debe permitir solicitar y descargar el archivo binario PDF generado por el backend para cualquier acta en disputa o para expediente CNE.

### Scenario: Descarga exitosa de expediente PDF
- **GIVEN** que existe un acta ID `55` con inconsistencia
- **WHEN** el coordinador pulsa "Descargar Expediente PDF" en la fila del acta `55`
- **THEN** se emite una petición `GET /api/v1/coordinator/actas/55/expediente-pdf` con cabecera Bearer
- **AND** al recibir el stream binario de `application/pdf`, el navegador dispara la descarga del archivo `expediente-cne-acta-55.pdf`.
