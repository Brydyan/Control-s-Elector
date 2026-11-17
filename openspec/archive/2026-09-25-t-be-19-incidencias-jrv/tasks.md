# Tasks — T-BE-19: Registro de Incidencias Operativas de JRV sin Acta

## Fase A: Base de Datos y Entidad

- [ ] **A.1** — Crear migración Liquibase `src/main/resources/db/changelog/changes/023-create-incidencia-jrv.sql` e incluir en `db.changelog-master.yaml`.
- [ ] **A.2** — Crear enum `TipoIncidenciaJrv` y entidad `IncidenciaJrv` en paquete `ec.twintailcs.electoral.incidencia.domain`.
- [ ] **A.3** — Crear `IncidenciaJrvRepository`.

## Fase B: Servicio y Controladores

- [ ] **B.1** — Implementar `IncidenciaService` con métodos `registrarIncidencia()` y `listarIncidencias()`.
- [ ] **B.2** — Crear DTOs: `IncidenciaRequestDto` e `IncidenciaResponseDto`.
- [ ] **B.3** — Exponer `POST /api/v1/observer/incidencias` en `ObserverController` o nuevo `IncidenciaObserverController`.
- [ ] **B.4** — Exponer `GET /api/v1/coordinator/incidencias` en `CoordinatorController` o nuevo `IncidenciaCoordinatorController`.

## Fase C: Tests de Integración

- [ ] **C.1** — Crear `IncidenciaJrvIT.java`:
  - Registrar incidencia y verificar persistencia en base de datos.
  - Listar incidencias con rol COORDINATOR.
  - Verificar bloqueo a usuarios anónimos o sin rol.
- [ ] **C.2** — Medir ejecución con `./mvnw test -Dtest=IncidenciaJrvIT`.

## Verificación por Mutación

- **V.1** — Omitir el seteo de `reportadaPor` -> violación de not null en BD o fallo de auditoría.
- **V.2** — Quitar `@PreAuthorize` en listado de coordinador -> test de seguridad debe fallar.
