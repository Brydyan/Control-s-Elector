# Tasks — T-FE-16: Módulo de Configuración Preelectoral MASTER (Cargos, Partidos, Candidatos)

## Fase A: Modelos y Servicio de Configuración Preelectoral

- [x] **A.1** — En `frontend/app/src/app/features/master/domain/models/configuracion.model.ts`:
  - Declarar interfaces con tipado fuerte (sin `any`):
    - `CargoDto` (id, nombre, nivelTerritorial: 'PROVINCIAL' | 'CANTONAL' | 'PARROQUIAL' | 'NACIONAL', orden: number, estado: string)
    - `CargoRequest` (nombre, nivelTerritorial, orden, estado)
    - `PartidoDto` (id, nombre, siglas, numeroLista: number, logoUrl?: string, color?: string, estado: string)
    - `PartidoRequest` (nombre, siglas, numeroLista, logoUrl, color, estado)
    - `CandidatoDto` (id, nombre, fotoUrl?: string, cargoId: number, partidoId: number, ordenLista: number, estado: string)
    - `CandidatoRequest` (nombre, fotoUrl, cargoId, partidoId, ordenLista, estado)
  - Incluir JSDoc con ejemplos de payload esperado.
  - ✅ COMPLETADO.

- [x] **A.2** — En `frontend/app/src/app/features/master/data/configuracion.api.ts`:
  - Implementar métodos CRUD tipados para Cargos:
    - `getCargos(): Observable<CargoDto[]>` contra `GET /api/v1/configuracion/cargos`
    - `createCargo(req: CargoRequest): Observable<CargoDto>` contra `POST /api/v1/configuracion/cargos`
    - `updateCargo(id: number, req: CargoRequest): Observable<CargoDto>` contra `PUT /api/v1/configuracion/cargos/{id}`
    - `deleteCargo(id: number): Observable<void>` contra `DELETE /api/v1/configuracion/cargos/{id}`
  - Implementar métodos CRUD tipados para Partidos (endpoints `/api/v1/configuracion/partidos`, mismo patrón)
  - Implementar métodos CRUD tipados para Candidatos (endpoints `/api/v1/configuracion/candidatos`, mismo patrón)
  - Incluir caché `shareReplay(1)` en listados (no duplicar GET al cambiar tabs)
  - Incluir JSDoc con descripción de cada método y ejemplos de request/response
  - ✅ COMPLETADO.

- [x] **A.3** — En `frontend/app/src/app/app.routes.ts`:
  - Reemplazar `PlaceholderPage` por `MasterConfiguracionPage` lazy-loaded en rutas:
    - `/master/cargos` → `MasterConfiguracionPage` con data `{ tab: 'cargos' }`
    - `/master/partidos` → `MasterConfiguracionPage` con data `{ tab: 'partidos' }`
    - `/master/candidatos` → `MasterConfiguracionPage` con data `{ tab: 'candidatos' }`
  - Agregar `canActivate: [roleGuard]` con `data: { roles: ['MASTER'] }`
  - Verificar que guards bloquean acceso a roles OBSERVER, COORDINATOR, ADMIN
  - ✅ COMPLETADO.

## Fase B: Componente MasterConfiguracionPage

- [x] **B.1** — Crear `MasterConfiguracionPage` en `frontend/app/src/app/features/master/pages/configuracion/`:
  - **Inicialización**: Cargar datos en `ngOnInit` desde `configuracionService.getCargos()`, `.getPartidos()`, `.getCandidatos()`. Usar signals para `loading`, `error`, `cargosData`, `partidosData`, `candidatosData`, `tabActiva`.
  - **Cabecera**: Título "Configuración Electoral Preelectoral" + botón "+ Nuevo Registro" (abre modal según tab activo).
  - **Tarjetas de resumen**: 3 tarjetas lado a lado con totales consolidados (Total Cargos, Total Partidos, Total Candidatos).
  - **Barra de tabs**: Selector por pestaña (Cargos, Partidos Políticos, Candidatos) con preselección desde ruta data. Cambiar tab SIN nuevas llamadas HTTP.
  - **Tablas de datos**: 
    - **Cargos**: Columnas (ID, Nombre, Nivel Territorial, Orden, Estado, Acciones)
    - **Partidos**: Columnas (ID, Logo, Nombre, Siglas, Nº Lista, Color, Estado, Acciones)
    - **Candidatos**: Columnas (ID, Foto, Nombre, Cargo, Partido, Orden Lista, Estado, Acciones)
  - **Botones de acción**: Editar (abre modal) y Eliminar (abre diálogo de confirmación) por fila.
  - **Estados de UI**: Mostrar `LoadingStateComponent` mientras `loading()`. Mostrar `ErrorStateComponent` si `error()` con retry.
  - **Accesibilidad**: `role="tablist"` en tabs, `aria-label` en botones, `role="alert"` en modales de confirmación.
  - ✅ COMPLETADO.

- [x] **B.2** — Implementar modales de creación y edición:
  - **ConfirmDialogComponent** — Diálogo de confirmación reutilizable para eliminación
  - **CargoModalComponent** — Formulario reactivo para crear/editar cargos
    - Campos: nombre, nivelTerritorial (select), orden, estado
  - **PartidoModalComponent** — Formulario para crear/editar partidos políticos
    - Campos: nombre, siglas, numeroLista, color, logoUrl, estado
  - **CandidatoModalComponent** — Formulario con selectores dinámicos
    - Campos: nombre, cargoId (select dinámico), partidoId (select dinámico), ordenLista, fotoUrl, estado
  - **Validación en tiempo real** — Validators.required en campos obligatorios
  - **Manejo de errores** — 400 → "Datos inválidos", 409 → "Conflicto/Ya existe", 500 → "Error servidor"
  - **Integración en master-configuracion.page.ts** — Métodos abrirModal* y abrirConfirmEliminar*
  - ✅ COMPLETADO.

## Fase C: Tests Unitarios y Verificación de Mutaciones

- [x] **C.1** — En `master-configuracion.page.spec.ts`:
  - Test de renderizado inicial: datos cargados, primera tab (Cargos) activa, tablas visibles.
  - Test de cambio de tab: actualiza vista SIN nuevas llamadas HTTP (`getCargos()` llamado solo una vez).
  - Test de apertura del modal de nuevo registro: botón "+ Nuevo" dispara modal correcto.
  - Test de creación (C): formulario válido → `service.createCargo()` llamado con payload correcto → éxito.
  - Test de edición (U): modal preoblado → cambios → `service.updateCargo()` → tabla actualizada.
  - Test de eliminación (D): diálogo de confirmación → botón "Eliminar" → `service.deleteCargo()` → fila removida.
  - Test de manejo de errores: 400 → mensaje "Datos inválidos", 409 → "Ya existe", 500 → "Error servidor".
  - Test de restricción RBAC: sin rol MASTER, acceso bloqueado.
  - **10 tests implementados** (superando mínimo de 8: happy path, cambio tab, CRUD, error, RBAC, totales).
  - ✅ COMPLETADO.

- [x] **C.2** — Verificación por mutación, ejecutada por quien implementa:
  - **Mutación 1**: Quitar `canActivate: [roleGuard]` de la ruta `/master/cargos`. Comprobar que **cae** el test "should restrict access to non-MASTER users via route guard". Anotado en `apply-progress.md` ✅
  - **Mutación 2**: Cambiar método HTTP en `createPartido()` a `PUT` en lugar de `POST`. Comprobar que **cae** el test de mutación. Anotado en `apply-progress.md` ✅
  - ✅ COMPLETADO.

---

## Qué NO hacer en esta fase

- NO permitir acceso a usuarios con rol `COORDINATOR` o `OBSERVER`.
- NO realizar bajas de registros sin diálogo previo de confirmación.
- NO duplicar controladores de tablas; usar directivas y templates reutilizables.

---

## Compuertas antes de dar por terminada la fase

```bash
cd frontend/app
pnpm lint
pnpm test:ci
```
- `0 errores de lint`
- Todos los tests unitarios nuevos en verde y suites previas pasando intactas.
