# Tasks — Recintos & Notificaciones de Actas Pendientes

---

## A. Database Migrations

### A.1 Crear tabla recinto

- [ ] Liquibase changeset: `xxx-recinto-create.sql`
  ```sql
  CREATE TABLE recinto (...)
  CREATE INDEX idx_recinto_*
  ```
- [ ] Validar locally: `select * from recinto` (tabla vacía)
- [ ] No errores en BD

### A.2 Modificar tabla jrv

- [ ] Liquibase changeset: `xxx-jrv-add-recinto-id.sql`
  - `ALTER TABLE jrv ADD COLUMN recinto_id BIGINT`
  - Inicialmente nullable
  - Create index

### A.3 Migrar datos: asignar JRVs a Recintos

- [ ] Liquibase changeset: `xxx-jrv-migrate-to-recintos.sql`
  - Script: Por cada parroquia, crear 1 Recinto
  - Script: Asignar JRVs existentes a Recintos (UPDATE jrv SET recinto_id = ...)
  - Script: Verificar todas las JRVs tienen recinto_id (SELECT COUNT(*) WHERE recinto_id IS NULL = 0)
  - Idempotente: usar `ON CONFLICT DO NOTHING` o `NOT EXISTS`

### A.4 Modificar tabla acta

- [ ] Liquibase changeset: `xxx-acta-add-approval-fields.sql`
  - `ALTER TABLE acta ADD COLUMN aprobada_por BIGINT REFERENCES usuario(id)`
  - `ALTER TABLE acta ADD COLUMN fecha_aprobacion TIMESTAMP`
  - `ALTER TABLE acta ADD COLUMN motivo_rechazo VARCHAR(500)`
  - Create indices

### A.5 Crear tabla notificacion

- [ ] Liquibase changeset: `xxx-notificacion-create.sql`
  ```sql
  CREATE TABLE notificacion (...)
  CREATE INDEX idx_notif_*
  ```

---

## B. Backend: Recintos (CRUD)

### B.1 Domain Layer

- [ ] Crear entidad `Recinto.java`
  - Fields: id, nombre, parroquiaId, coordinatorId, estado, fechaCreacion, fechaActualizacion
  - Validaciones: @NotNull, @NotBlank, @Size

- [ ] Crear repository `RecintoRepository.java`
  - Method: `findByParroquiaId(Long id)`
  - Method: `findByCoordinatorId(Long id)` (N recintos por coordinator)
  - Method: `existsByNombreAndParroquiaId(String nombre, Long parroquiaId)`

### B.2 Application Layer

- [ ] Crear `RecintoService.java`
  - `crearRecinto(request: RecintoRequest): RecintoResponse`
    - Validar parroquiaId existe
    - Validar coordinatorId existe + rol=COORDINATOR
    - Validar nombre+parroquiaId único
    - Guardar en BD
  - `actualizarRecinto(id, request)`
  - `obtenerRecinto(id)`
  - `listarRecintos(page, size)`
  - `eliminarRecinto(id)`
    - Bloquear si tiene JRVs asignadas
  - `asignarJrvsAlRecinto(recintoId, jrvIds): void`
    - Bulk update: UPDATE jrv SET recinto_id = {recintoId} WHERE id IN (...)

### B.3 Web Layer (REST)

- [ ] Crear `RecintoResource.java`
  - `GET /api/v1/admin/recintos` → `List<RecintoResponse>` (MASTER only)
  - `POST /api/v1/admin/recintos` → `RecintoResponse` (MASTER only)
  - `PUT /api/v1/admin/recintos/{id}` → `RecintoResponse` (MASTER only)
  - `DELETE /api/v1/admin/recintos/{id}` → 204 (MASTER only)
  - Mappers: `RecintoMapper.toResponse()`
  - Error handling: 400 (duplicado), 404 (no existe), 409 (tiene JRVs)

### B.4 Tests

- [ ] `RecintoServiceTest`
  - Crear recinto válido ✓
  - Nombre + parroquiaId duplicados → ActaException
  - Coordinador inválido → Exception
  - Eliminar con JRVs → Exception
  - List recintos paginado ✓

- [ ] `RecintoResourceIT` (integration)
  - POST /admin/recintos → 201 ✓
  - Duplicado → 400 ✓
  - Falta coordinador → 404 ✓
  - DELETE con JRVs → 409 ✓

---

## C. Backend: Actas Pendientes & Notificaciones

### C.1 Modify ActaService

- [ ] Interceptar `enviarActa()` → estado ENVIADA
  - Obtener recinto_id de JRV
  - Crear notificación para Coordinator del Recinto
  - Tipo: "ACTA_ENVIADA"
  - Mensaje: "{jrv} - {cargo} por {observer}"

- [ ] Interceptar `aprobarActa()` → estado APROBADA
  - Validar usuario es Coordinator del Recinto
  - Guardar aprobada_por, fecha_aprobacion
  - Crear notificación para Observer: "Tu acta ha sido APROBADA"

- [ ] Interceptar `rechazarActa()` → estado EN_DISPUTA
  - Validar usuario es Coordinator del Recinto
  - Validar motivoRechazo requerido (1-500 chars)
  - Crear notificación para Observer: "Acta rechazada. Motivo: {motivo}"

### C.2 Crear NotificacionService

- [ ] `crearNotificacion(usuarioId, tipo, titulo, mensaje, actaId?): void`
  - INSERT en tabla notificacion
  - Evaluar envío push (optional Phase 2: WebSocket)

- [ ] `obtenerNotificacionesPendientes(usuarioId, limit): List<NotificacionResponse>`
  - SELECT WHERE usuario_id=? AND leida=false ORDER BY fecha DESC

- [ ] `marcarComoLeida(notifId): void`
  - UPDATE notificacion SET leida=true WHERE id=?

- [ ] Tests: crear, listar, marcar leída ✓

### C.3 Crear ActasCoordinatorResource

- [ ] `GET /api/v1/coordinator/actas-pendientes`
  - Query params: estado=ENVIADA, limit=20, offset=0
  - RLS: obtener recintos asignados al usuario
  - Query: `SELECT acta FROM acta a INNER JOIN jrv j ON a.jrv_id=j.id INNER JOIN recinto r ON j.recinto_id=r.id WHERE r.coordinator_id=? AND a.estado=ENVIADA`
  - Response: lista con actaId, jrv, cargo, observer, estado, fechaRegistro

- [ ] `PUT /api/v1/coordinator/actas/{actaId}/aprobar`
  - Request: { comentario?: string }
  - Llamar `ActaService.aprobarActa(actaId, coordinatorId)`
  - Response: acta actualizada con aprobada_por, fecha_aprobacion

- [ ] `PUT /api/v1/coordinator/actas/{actaId}/rechazar`
  - Request: { motivoRechazo: string (1-500) }
  - Llamar `ActaService.rechazarActa(actaId, coordinatorId, motivo)`
  - Response: acta con estado=EN_DISPUTA, motivo_rechazo

- [ ] `GET /api/v1/coordinator/actas/{actaId}/detalle`
  - Obtener acta completa: foto, datos, detalles (candidatos/votos)
  - RLS: validar que acta pertenece a recinto del coordinator
  - Response: JSON con URL de foto (S3), metadatos, detalles

- [ ] Tests: RLS funciona, solo ve sus recintos ✓

### C.4 Crear NotificacionResource

- [ ] `GET /api/v1/notifications/pendientes?limit=10`
  - Obtener notificaciones sin leer del usuario actual
  - Response: lista + count sin leer

- [ ] `PUT /api/v1/notifications/{notifId}/leer`
  - Marcar notificación como leída
  - Response: 204

- [ ] Tests: obtener, marcar leída ✓

---

## D. Frontend: Panel de Recintos (Admin)

### D.1 Componentes

- [ ] Crear `recintos-list.page.ts`
  - Cargar lista: `GET /admin/recintos`
  - Tabla: nombre, parroquia, coordinador, # mesas, estado
  - Buttons: Crear, Editar, Eliminar

- [ ] Crear `recinto-form.modal.ts`
  - Cascada: Provincia → Cantón → Parroquia
  - Input: nombre
  - Dropdown: coordinador (usuarios con rol COORDINATOR)
  - [Guardar] [Cancelar]
  - Validación: nombre requerido, parroquia requerida

- [ ] Crear `recinto-jrvs.modal.ts` (editar JRVs asignadas)
  - Tabla: JRVs de la parroquia del recinto
  - Checkboxes: seleccionar cuáles asignar
  - [Guardar] [Cancelar]

### D.2 API Integration

- [ ] `recinto.api.ts`
  ```typescript
  getRecintos(): Observable<PagedResponse<RecintoResponse>>
  crearRecinto(req: RecintoRequest): Observable<RecintoResponse>
  actualizarRecinto(id: number, req): Observable<RecintoResponse>
  eliminarRecinto(id: number): Observable<void>
  ```

### D.3 Menu Integration

- [ ] Agregar a menú Configuración:
  - "Recintos" → `/admin/configuracion/recintos`

### D.4 Tests

- [ ] `recintos-list.page.spec.ts`
  - Cargar lista ✓
  - Crear recinto → modal + save ✓
  - Editar recinto ✓
  - Eliminar recinto (con confirmación) ✓
  - Validaciones form ✓

---

## E. Frontend: Panel de Actas Pendientes (Coordinator)

### E.1 Componentes

- [ ] Crear `actas-pendientes.page.ts`
  - Tabs: Pendientes (ENVIADA) | Revisadas (APROBADA/EN_DISPUTA)
  - Polling: `GET /coordinator/actas-pendientes` cada 5 segundos (D3 MVP)
  - Signals: lista de actas, loading, error

- [ ] Crear `acta-card.component.ts`
  - Muestra: JRV, Cargo, Observer, Fecha, Estado
  - Buttons: [Ver Detalle] [Aprobar] [Rechazar]

- [ ] Crear `acta-detalle.modal.ts`
  - Lightbox para foto
  - Tabla: sufragantes, blancos, nulos, total votos
  - Tabla: candidatos + votos
  - Buttons: [Aprobar] [Rechazar] [Cerrar]

- [ ] Crear `rechazo-form.modal.ts`
  - TextArea: motivo (max 500 chars, contador)
  - [Rechazar] [Cancelar]

### E.2 API Integration

- [ ] `coordinator.api.ts`
  ```typescript
  obtenerActasPendientes(estado?: string, limit?: number): Observable<PagedResponse<ActaSummary>>
  obtenerActaDetalle(actaId: number): Observable<ActaDetalle>
  aprobarActa(actaId: number, comentario?: string): Observable<Acta>
  rechazarActa(actaId: number, motivo: string): Observable<Acta>
  ```

### E.3 Notificaciones (Bell)

- [ ] Agregar componente `notificaciones.component.ts`
  - Bell icon en top bar
  - Polling: `GET /notifications/pendientes` cada 10 segundos (D3 MVP)
  - Badge: contador sin leer
  - Dropdown: últimas 5 notificaciones
  - Click en notif → abre `acta-detalle` del acta asociada

- [ ] Crear `notificacion.api.ts`
  ```typescript
  obtenerPendientes(limit: number): Observable<Notificacion[]>
  marcarComoLeida(notifId: number): Observable<void>
  ```

### E.4 Menu Integration

- [ ] Link en top bar: "Actas Pendientes" (si rol=COORDINATOR)
- [ ] Link en menú: Coordinator › Actas Pendientes

### E.5 Tests

- [ ] `actas-pendientes.page.spec.ts`
  - Cargar actas pendientes ✓
  - Polling cada 5s ✓
  - Click acta → abre detalle ✓
  - Aprobar acta → PUT + notification ✓
  - Rechazar acta → modal + motivo + PUT ✓

- [ ] `notificaciones.component.spec.ts`
  - Cargar notificaciones ✓
  - Badge con contador ✓
  - Click notif → abre modal ✓
  - Marcar leída ✓

---

## F. Documentación

- [ ] Update `AUTHENTICATION.md`: agregar rol COORDINATOR
- [ ] Update `API_REFERENCE.md`: endpoints nuevos
- [ ] Create `RECINTOS_GUIDE.md`:
  - Qué es un recinto, rol coordinador
  - Screenshots: crear recinto, asignar coordinador
  - Flujo: observer sube acta → coordinator aprueba/rechaza
- [ ] Create `ACTAS_VALIDACION_GUIDE.md`:
  - Coordinator: cómo revisar actas
  - Estados de acta (ENVIADA → APROBADA | EN_DISPUTA → CORREGIDA)

---

## Implementation Checklist

### Pre-Build
- [ ] Arquitecto aprueba D1-D5
- [ ] Product Manager confirma MVP scope (¿Phase 2 WebSocket?)

### Build Phase
- [ ] A: Migraciones + BD testing
- [ ] B: RecintoService + endpoints
- [ ] C: NotificacionService + ActasCoordinator endpoints
- [ ] D: Panel de Recintos (Admin)
- [ ] E: Panel de Actas Pendientes (Coordinator) + notificaciones
- [ ] F: Documentación

### Post-Build
- [ ] No regresiones en observer/registro-acta
- [ ] No regresiones en admin/usuarios
- [ ] RLS funciona: Coordinator solo ve sus recintos
- [ ] Polling funciona: actas aparecen en 5-10 segundos

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| RLS incompleta → Coordinator ve actas de otro | Tests: verificar RLS en query |
| Notificaciones no llegan | Logging: cada notif creada se registra |
| Polling slow → lag en UI | Usar signals reactivos, no manual refresh |
| Recinto sin JRVs asignadas | Validar en form al crear recinto |
| Eliminar recinto cascade daña integridad | Bloquear si tiene JRVs (D5) |

---

## Notas de Diseño

1. **¿Por qué polling vs WebSocket en MVP?**
   - Polling 5s es suficiente (D3). WebSocket Phase 2 si UX lo requiere.

2. **¿Coordinator puede ver historia de actas?**
   - SÍ. Tab "Revisadas" muestra APROBADA + EN_DISPUTA histórico.

3. **¿Notificación desaparece después de leerla?**
   - NO. Se mantiene en historia pero leida=true (no en badge).

4. **¿Eliminar Recinto deja JRVs huérfanas?**
   - NO. Validamos que recinto no tenga JRVs antes de eliminar (D5).

