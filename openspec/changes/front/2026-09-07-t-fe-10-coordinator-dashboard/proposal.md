# Proposal — T-FE-10: Coordinator: Gestión Global de Actas

## Qué se construye
Pantalla principal del coordinator: lista filtrable de todas las actas con acciones rápidas (disputar, corregir, aprobar).

## Por qué
- Coordinator necesita vista global de actas.
- Filtros aceleras búsqueda y toma de decisiones.
- Acciones rápidas evitan navegar a cada acta.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Componente | CoordinatorHomeComponent | detalles de acta |
| API | GET /api/coordinator/actas | búsqueda avanzada |
| UI | Tabla con columnas: JRV, Cargo, Estado, Acciones | edición inline |
| Filtros | Estado, JRV, fecha rango | búsqueda por texto |
| Acciones | Disputar, Corregir, Aprobar (botones) | motivo de disputa |

## Criterio de aceptación

- [ ] CoordinatorHomeComponent cargado en /coordinator/actas
- [ ] GET /api/coordinator/actas al init
- [ ] Tabla muestra todas las actas
- [ ] Filtro por estado funciona
- [ ] Botones "Disputar", "Corregir", "Aprobar" navegan o disparan acciones
- [ ] Loading spinner

## Fuera de alcance

- Edición en línea
- Historial de cambios
- Comentarios en acta
- Exportación

## Dependencias

- T-FE-02 (rutas, roleGuard)
- T-FE-03 (AuthService)

## Decisiones a tomar

**D1 — Filtros en frontend vs backend:** Frontend (Ionic IonSelect).

**D2 — Acciones: botones o links:** Botones con (click) handlers.
