# Tarea ODD / Pandorx: Estandarización de UI (KPI Cards y Tablas PrimeNG)

## 1. Contexto y Objetivos
El frontend de Control Electoral (Angular 19 + PrimeNG 21 + Sakai) presenta inconsistencias visuales en diferentes páginas:
- Cards de KPIs / estadísticas con diferentes paddings, tipografías y colores.
- Tablas PrimeNG (`p-table`) con estilos disparatados, diferentes paddings de cabecera, hover states inconsistentes y manejo dispar de empty/loading states.

Objetivo:
1. Declarar tokens semánticos de diseño en `src/theme/tokens.scss` para KPIs y Tablas (modo claro y `.app-dark`).
2. Crear el componente reutilizable `KpiCardComponent` en `src/app/shared/ui/kpi-card/` con su suite de pruebas unitarias (`kpi-card.component.spec.ts`) y registrarlo en `src/app/shared/ui/index.ts`.
3. Crear o actualizar estilos globales de tabla (`.app-table` o `app-table-standard`) en el sistema de diseño para uniformar todos los `p-table` (bordes sutiles, cabeceras en mayúscula semántica, padding uniforme `p-datatable-sm`, hover suaves, paginador alineado).
4. Refactorizar las páginas clave para usar el nuevo estándar:
   - KPIs: `DashboardPage` (`src/app/features/dashboard/pages/dashboard/dashboard.page.html`), `ResultadosCargoPage`, `CoordinatorReportesPage`.
   - Tablas: `ActasListaPage`, `AdminUsuariosPage`, `AdminSesionesPage`.
5. Mantener los tests de Vitest en verde (`pnpm test`) y verificar el build (`pnpm build`).

## 2. Invariantes
- Mantener compatibilidad con modo oscuro (`.app-dark`).
- No romper contratos REST ni lógica de negocio/guards.
- Usar PrimeIcons y tokens CSS nativos.
