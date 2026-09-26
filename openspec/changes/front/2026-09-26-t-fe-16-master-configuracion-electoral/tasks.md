# Tasks — T-FE-16: Módulo de Configuración Preelectoral MASTER (Cargos, Partidos, Candidatos)

## Fase A: Modelos y Servicio de Configuración Preelectoral

- [ ] **A.1** — En `frontend/app/src/app/features/master/models/configuracion.model.ts`:
  - Declarar interfaces `CargoDto`, `CargoRequest`, `PartidoDto`, `PartidoRequest`, `CandidatoDto`, `CandidatoRequest`.
- [ ] **A.2** — En `frontend/app/src/app/features/master/services/configuracion.service.ts`:
  - Métodos CRUD completos para Cargos (`/api/v1/configuracion/cargos`).
  - Métodos CRUD completos para Partidos (`/api/v1/configuracion/partidos`).
  - Métodos CRUD completos para Candidatos (`/api/v1/configuracion/candidatos`).
- [ ] **A.3** — En `frontend/app/src/app/app.routes.ts`:
  - Reemplazar `PlaceholderPage` en `/master/cargos`, `/master/partidos`, `/master/candidatos` por `MasterConfiguracionPage`.
  - Asegurar `roleGuard` con rol `['MASTER']`.

## Fase B: Componente MasterConfiguracionPage

- [ ] **B.1** — Crear `MasterConfiguracionPage` en `frontend/app/src/app/features/master/pages/configuracion/`:
  - Cabecera con título y botón "+ Nuevo Registro".
  - Tarjetas de resumen métrico: Total Cargos, Total Partidos, Total Candidatos.
  - Barra de navegación por pestañas (`Cargos`, `Partidos Políticos`, `Candidatos`).
  - Tablas estilizadas con columnas correspondientes a cada entidad (siglas, lista, orden, estado).
  - Botones de acción: Editar y Eliminar por fila.
- [ ] **B.2** — Implementar modales de creación y edición:
  - Formulario reactivo con validación de campos obligatorios.
  - Selectores de relación (asociar candidato a cargo y partido existentes).

## Fase C: Tests Unitarios y Verificación de Mutaciones

- [ ] **C.1** — En `master-configuracion.page.spec.ts`:
  - Test de renderizado de la pestaña activa según la ruta de entrada.
  - Test de apertura del modal de nuevo registro.
  - Test de llamada a `service.createCargo()` al confirmar formulario.
  - Test de eliminación con confirmación.
- [ ] **C.2** — Verificación por mutación, ejecutada por quien implementa:
  - Quitar la comprobación de rol `MASTER` en la ruta y comprobar que **cae** el test de restricción RBAC. Anotá en `apply-progress.md` **el nombre del test que cayó**.
  - Cambiar el método HTTP en `crearPartido` a `PUT` en lugar de `POST` y comprobar que **cae** el test del servicio. Anotá en `apply-progress.md` **el nombre del test que cayó**.

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
