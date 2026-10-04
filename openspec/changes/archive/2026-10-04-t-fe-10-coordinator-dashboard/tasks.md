# Tasks — T-FE-10: Coordinator: Validación, Auditoría y Gestión Global de Actas

## Fase A: CoordinatorService y Contratos Tipados

- [x] **A.1** — Definir modelos en `features/coordinator/models/coordinator.model.ts`:
  - `ActaItem`, `ActaFiltros`, `ActaDetalleCompleto`, `DisputarActaPayload`, `CorregirActaPayload`, `HistorialEntry`.
- [x] **A.2** — Implementar métodos en `features/coordinator/services/coordinator.service.ts`:
  - `getActas(filtros)` con query params y validación del sobre `{ data, meta }`.
  - `getActaDetalleCompleto(id)` con cálculo consolidado de invariante electoral.
  - `aprobarActa(id)` -> `PUT /api/v1/coordinator/actas/{id}/aprobar`.
  - `disputarActa(id, payload)` -> `PUT /api/v1/coordinator/actas/{id}/disputar`.
  - `corregirActa(id, payload)` -> `PUT /api/v1/coordinator/actas/{id}/corregir`.
  - `anularActa(id)` -> `DELETE /api/v1/coordinator/actas/{id}`.
- [x] **A.3** — Tests unitarios en `coordinator.service.spec.ts` (10 tests pasando).

## Fase B: ActasListaPage (Listado Paginado con Filtros)

- [x] **B.1** — Implementar `features/coordinator/pages/actas-lista/actas-lista.page.ts`:
  - Barra de filtros: estado, dignidad, cantón y búsqueda por número/parroquia.
  - Integración con componente Paginator (`PaginatorComponent`).
  - Navegación al hacer click en fila a `/coordinator/actas/:id`.
- [x] **B.2** — Tests unitarios en `actas-lista.page.spec.ts` (13 tests pasando).

## Fase C: ActaDetallePage (Split-View 50/50 y Auditoría)

- [x] **C.1** — Implementar layout dividido en `features/coordinator/pages/acta-detalle/acta-detalle.page.ts`:
  - Panel izquierdo: votos por candidato, validación de cuadre, historial de eventos, botones de acción.
  - Panel derecho: visor de foto de evidencia con zoom/rotación CSS y enlace de contingencia a WhatsApp.
- [x] **C.2** — Modales interactivos de Disputa (motivo obligatorio) y Corrección (con recálculo de sumas).
- [x] **C.3** — Manejo unificado de errores y recarga automática ante conflictos de concurrencia (HTTP 409).
- [x] **C.4** — Tests unitarios en `acta-detalle.page.spec.ts` (9 tests pasando).

## Fase D: Rutas y RBAC

- [x] **D.1** — Rutas `/coordinator/actas` y `/coordinator/actas/:id` protegidas con `roleGuard(['COORDINATOR', 'MASTER'])`.

## Fase E: Alineación de Contratos (T-BE-18, T-BE-20, T-BE-21)

- [x] **E.1** — En `CoordinatorService.anularActa`: actualizar firma a `anularActa(id: number, motivo: string)` y enviar `{ motivo }` en el cuerpo de `http.delete()` (`AnulacionDto`).
- [x] **E.2** — En `CoordinatorService.getActaDetalleCompleto`: mapear los votos reales de candidatos desde `data.detalles` (`T-BE-21`) eliminando el valor hardcodeado `votos: 0`.
- [x] **E.3** — En `ActaDetallePage`: enlazar el visor de imagen a `data?.acta?.urlEvidencia` (URL prefirmada S3) en reemplazo del campo eliminado `storageUrl`.
- [x] **E.4** — En `ActaDetallePage`: incorporar botón de subida de foto de contingencia (`POST /api/v1/coordinator/actas/{id}/imagen`, `T-BE-18`) y botón de descarga de expediente CNE en PDF (`GET /api/v1/coordinator/actas/{id}/expediente-pdf`, `T-BE-20`).

## Verificación por Mutación

- **V.1** — Alterar el validador del sobre `{ data, meta }` en `CoordinatorService` para aceptar arreglos planos -> tests de servicio deben alertar la regresión de contrato.
- **V.2** — Quitar la validación de motivo no vacío en `confirmarDisputa()` -> `acta-detalle.page.spec.ts` debe fallar.
- **V.3** — Omitir el envío del objeto `{ motivo }` en `anularActa()` -> el test de anulación debe fallar.
