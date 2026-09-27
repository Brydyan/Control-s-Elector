# Tasks — T-FE-17: Centro de Reportes y Estadísticas de Coordinación (COORDINATOR)

## Fase A: Servicio de Coordinación y Rutas

- [ ] **A.1** — En `frontend/app/src/app/features/coordinator/services/coordinator.service.ts`:
  - Implementar método `descargarExpedientePdf(actaId: number): Observable<Blob>` contra `GET /api/v1/coordinator/actas/{id}/expediente-pdf` con `responseType: 'blob'`.
- [ ] **A.2** — En `frontend/app/src/app/app.routes.ts`:
  - Registrar ruta `coordinator/reportes` con `canActivate: [roleGuard]`, `roles: ['COORDINATOR', 'MASTER', 'ADMIN']`.
  - Agregar ítem en navegación de coordinación en `SidebarComponent`.

## Fase B: Componente CoordinatorReportesPage

- [ ] **B.1** — Crear `CoordinatorReportesPage` en `frontend/app/src/app/features/coordinator/pages/reportes/`:
  - Banner superior con porcentaje global de avance y estado "En vivo".
  - Grid de tres columnas:
    - Tarjeta Progreso por Cantón.
    - Tarjeta Estados de Actas (con dots de color).
    - Tarjeta Progreso por Cargo.
  - Sección inferior:
    - Tabla de actas con novedad/disputa y botón de acción "Descargar Expediente PDF".
    - Métricas de resoluciones del día y tiempo promedio de respuesta.
- [ ] **B.2** — Lógica de descarga de archivo:
  - Generar Object URL en memoria a partir del `Blob`.
  - Disparar descarga y revocar URL para prevenir fuga de memoria (`URL.revokeObjectURL`).

## Fase C: Tests Unitarios y Verificación de Mutaciones

- [ ] **C.1** — En `coordinator-reportes.page.spec.ts`:
  - Test de renderizado de las métricas principales.
  - Test de llamada a `descargarExpedientePdf` al pulsar el botón de descarga.
  - Test de revocación de Object URL.
- [ ] **C.2** — Verificación por mutación, ejecutada por quien implementa:
  - Cambiar el mime type esperado de `application/pdf` a `text/plain` y comprobar que **cae** el test de descarga de expediente. Anotá en `apply-progress.md` **el nombre del test que cayó**.
  - Omitir el llamado a `URL.revokeObjectURL` y comprobar que **cae** el test de limpieza de memoria. Anotá en `apply-progress.md` **el nombre del test que cayó**.

---

## Qué NO hacer en esta fase

- NO realizar llamadas de red sin token de autorización; usar siempre el cliente HTTP de Angular con interceptores.
- NO alterar datos de actas desde esta pantalla (es informativa y de descargas).
- NO acumular Object URLs en memoria sin revocar.

---

## Compuertas antes de dar por terminada la fase

```bash
cd frontend/app
pnpm lint
pnpm test:ci
```
- `0 errores de lint`
- Todos los tests unitarios nuevos en verde y suites previas pasando intactas.
