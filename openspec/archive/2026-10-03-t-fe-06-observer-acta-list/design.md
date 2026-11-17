# Design — T-FE-06: Observer: Flujo de JRVs Asignadas, Dignidades y Consulta de Actas

---

## D1 — Flujo Jerárquico en Cascada vs Listado Plano

**Decisión**: La experiencia del veedor se organiza jerárquicamente:
1. `MisJrvsPage` (`/observer/mis-jrvs`): Lista de mesas electorales asignadas.
2. `JrvCargosPage` (`/observer/jrv/:jrvId/cargos`): Lista de dignidades por mesa.
3. `RegistrarActaPage` (`/observer/jrv/:jrvId/cargo/:cargoId/registrar`): Formulario de acta.

**Alternativa descartada — Listado plano de actas**: Forzar al veedor a buscar un acta sin contexto de recinto/mesa causa confusiones de digitación durante el conteo nocturno.

---

## D2 — Endpoints y Contrato de Servicio (`ObserverService`)

**Decisión**: `ObserverService` centraliza las peticiones con prefijo canónico `/api/v1/observer`:
- `GET /api/v1/observer/jrvs`: Retorna `List<JrvConEstadoDto>` (`jrvId`, `numero`, `parroquia`, `cargosPendientes`, `cargosRegistrados`).
- `GET /api/v1/observer/jrv/{jrvId}/cargos`: Retorna `List<CargoConEstadoDto>` (`cargoId`, `nombre`, `nivel`, `registrado`, `estadoActa`, `actaId`).
- `GET /api/v1/observer/mis-actas`: Retorna `List<ActaResponseDto>` del veedor autenticado.
- `GET /api/v1/observer/correcciones-pendientes`: Retorna actas en estado `EN_DISPUTA` que requieren acción.

**Alternativa descartada — Rutas sin versión `/api/observer/*`**: Provocan errores 404 al omitir el prefijo estándar del API Gateway `/api/v1`.

---

## D3 — Filtrado de JRVs en Cliente

**Decisión**: `MisJrvsPage` almacena el arreglo completo de JRVs en memoria y aplica filtros reactivos mediante pestañas (`TODAS`, `PENDIENTES`, `COMPLETADAS`):
```typescript
applyFilter(filter: FilterTab): void {
  this.activeFilter = filter;
  if (filter === 'PENDIENTES') {
    this.filteredJrvs = this.jrvs.filter(j => j.cargosPendientes > 0);
  } else if (filter === 'COMPLETADAS') {
    this.filteredJrvs = this.jrvs.filter(j => j.cargosPendientes === 0);
  } else {
    this.filteredJrvs = [...this.jrvs];
  }
}
```

**Alternativa descartada — Re-consultar el backend en cada cambio de pestaña**: Un veedor supervisa entre 1 y 5 JRVs en promedio; consultar el servidor en cada tab satura la red móvil innecesariamente.

---

## D4 — Manejo de Estados de Interfaz

**Decisión**: Reutilizar componentes atómicos compartidos de UI (`LoadingStateComponent`, `ErrorStateComponent`, `EmptyStateComponent`, `CardComponent`, `BadgeComponent`) para garantizar consistencia visual y feedback accesible cuando la red fluctúa.
