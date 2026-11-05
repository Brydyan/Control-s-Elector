# Design — T-FE-17: Centro de Reportes y Estadísticas de Coordinación (COORDINATOR)

---

## D1 — Reutilización de Servicios de Backend Existentes

**Decisión**:
El componente se alimenta de las APIs ya existentes y probadas:
- `GET /api/v1/dashboard/resumen`: totales de actas por estado.
- `GET /api/v1/dashboard/progreso`: avance cantonal.
- `GET /api/v1/coordinator/actas?estado=EN_DISPUTA`: lista rápida de actas impugnables para descarga de PDF.

**Alternativa descartada — Crear un controlador monolítico nuevo en backend solo para reportes de coordinación**:
Descartada porque los endpoints existentes ya proveen todas las métricas necesarias con autoridad de base de datos. Duplicar queries en el backend aumenta la superficie de mantenimiento sin aportar valor funcional.

---

## D2 — Descarga Segura de Expedientes PDF CNE

**Decisión**:
`CoordinatorService.descargarExpedientePdf(actaId: number): Observable<Blob>` realiza la petición con `responseType: 'blob'`, adjuntando automáticamente la cabecera `Authorization: Bearer <token>` a través del `AuthInterceptor`. El componente genera un enlace temporal DOM en memoria (`window.URL.createObjectURL(blob)`) y simula el clic de descarga con nombre `expediente-cne-acta-{id}.pdf`.

**Alternativa descartada — Enlace `<a>` directo con `href="/api/v1/coordinator/actas/{id}/expediente-pdf"`**:
Descartada porque la etiqueta `<a href="...">` nativa del navegador no inyecta el token Bearer JWT de sesión en las cabeceras HTTP, lo que causaría un error 401 Unauthorized inmediato al intentar descargar.

---

## D3 — Simulación Realtime y Tasa de Actas / Hora

**Decisión**:
El cálculo de actas por hora y tiempo de resolución se deriva a partir de las marcas de tiempo (`fechaRegistro` y `auditLog`) de las actas procesadas en la jornada actual. Si la suscripción WebSocket a Supabase Realtime está activa, el banner se actualiza cada vez que se aprueba o corrige un acta sin requerir recargar la página.

**Alternativa descartada — Polling ciego en bucle cerrado cada 3 segundos**:
Descartada porque satura la conexión de la mesa de coordinación con peticiones repetitivas cuando no han ocurrido nuevas transmisiones de actas.
