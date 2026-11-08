# Proposal — T-FE-17: Centro de Reportes y Estadísticas de Coordinación (COORDINATOR)

## Qué se construye
Pantalla centralizada de métricas operativas y generación de expedientes para la mesa de coordinación electoral (`COORDINATOR` y `MASTER`), según el mockup [`COORDINATOR/desktop/07-reportes.html`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/mockups/COORDINATOR/desktop/07-reportes.html):
1. **Ruta y Acceso**:
   - `/coordinator/reportes` protegido con `roleGuard` (`COORDINATOR`, `MASTER`, `ADMIN`).
2. **Banner de Rendimiento Operativo Global**:
   - Indicador grande de % de avance provincial (219 / 322 JRVs procesadas).
   - Tasa de velocidad operativa: actas procesadas por hora.
   - Estado de conexión con Supabase Realtime ("En vivo").
3. **Páneles de Métricas Clave**:
   - Progreso por cantón (Santa Elena, La Libertad, Salinas).
   - Distribución de actas por estado (`ENVIADA`, `EN_DISPUTA`, `CORREGIDA`, `APROBADA`, `ANULADA`).
   - Progreso de escrutinio por cargo (Prefecto, Alcalde, Concejales, CPCCS).
   - Métricas de resolución: tiempo promedio de subsanación de disputas y resoluciones del día.
4. **Centro de Descargas y Expedientes de Impugnación CNE**:
   - Botón directo para descargar el expediente formal en PDF para actas con inconsistencia, integrándose con el endpoint de `T-BE-20` (`GET /api/v1/coordinator/actas/{id}/expediente-pdf`).

## Por qué
- El coordinador necesita una vista consolidada en tiempo real que le permita identificar cuellos de botella (ej. qué cantón está rezagado o cuántas actas siguen en disputa).
- Facilita la generación inmediata del PDF de impugnación CNE para llevar a la audiencia de escrutinio provincial sin tener que entrar acta por acta.

## Alcance

| Elemento | Incluye | No incluye |
|---|---|---|
| **Vistas** | `CoordinatorReportesPage` (`/coordinator/reportes`) | Modificación directa de votos |
| **Componentes** | Banner de progreso, barras cantones/cargos, lista de estados, panel de resoluciones, disparador de descarga PDF | Servicios de recuento físico manual |
| **Servicios** | Integración con `DashboardService` y descarga de PDF vía `CoordinatorService` | Gestión de usuarios |

## Criterios de Aceptación

- [ ] La ruta `/coordinator/reportes` es accesible desde la navegación del rol COORDINATOR.
- [ ] Muestra el avance consolidado de JRVs procesadas vs faltantes.
- [ ] Permite visualizar la distribución de actas en sus 5 estados con porcentajes y colores oficiales.
- [ ] Permite descargar el PDF del expediente de impugnación invocando el endpoint de `T-BE-20` mediante una petición autenticada que devuelve el Blob.
