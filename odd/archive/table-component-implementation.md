# Task: Implement Reusable Table Component (<app-table>)

## Objetivo
Implementar el componente standalone reutilizable `<app-table>` en Angular 19 + PrimeNG 21 dentro de `frontend/app/src/app/shared/ui/table/` con suite de tests y compatibilidad con `.app-dark`. Luego migrar las tablas de Catálogos Electorales (`MasterCargosPage`, `MasterPartidosPage`, `MasterCandidatosPage` y `MasterConfiguracionPage`) para usar este componente.

## Requerimientos del Componente
1. Carpeta: `frontend/app/src/app/shared/ui/table/`
   - `table.component.ts`
   - `table.component.html`
   - `table.component.scss` (o usar tokens de `tables.scss`)
   - `table.component.spec.ts`
   - `table.model.ts` (interfaces: `TableColumn<T>`, etc.)
2. Exportar en `frontend/app/src/app/shared/ui/index.ts`.
3. Características:
   - Recibir `data: T[]`
   - Recibir `columns: TableColumn<T>[]` (`key`, `header`, `width`, `sortable`, `align`)
   - Soporte para acciones por fila (3 puntos `pi pi-ellipsis-v` con `p-menu`) si se pasa `actions: (row: T) => MenuItem[]`
   - Soporte para proyección de celdas personalizadas mediante `ng-template` o directiva
   - Integración con loading/skeleton y empty state (`<app-empty-state>`)
   - Soporte para vista mobile card (`.mobile-only`) o fallback responsivo
4. Pruebas y Build:
   - Pruebas unitarias completas pasando con `cd frontend/app && npx vitest run src/app/shared/ui/table`
   - `cd frontend/app && npx vitest run` y `pnpm build` sin errores
   - `pnpm run format` ejecutado
